package com.minicrm.auth

import com.minicrm.common.error.ApiException
import com.minicrm.common.web.CurrentUser
import com.minicrm.config.AppProperties
import com.minicrm.demo.DemoSeeder
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** [cookieExpiresAt] для демо довший за сесію: браузер має донести токен і після 24 год, щоб показати «demo expired». */
data class IssuedSession(val token: String, val expiresAt: Instant, val cookieExpiresAt: Instant, val currency: String = "USD")

sealed interface SessionLookup {
    data class Valid(val user: CurrentUser, val renewedUntil: Instant?) : SessionLookup
    data object DemoExpired : SessionLookup
    data object Invalid : SessionLookup
}

@Service
class AuthService(
    private val users: AppUserRepository,
    private val sessions: UserSessionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val demoSeeder: DemoSeeder,
    private val props: AppProperties,
    private val clock: Clock,
) {
    private val dummyHash by lazy { passwordEncoder.encode("not-a-real-password-0000")!! }

    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MICROS)

    @Transactional
    fun signUp(email: String, password: String): IssuedSession {
        val normalized = email.trim().lowercase()
        if (users.findByEmailIgnoreCase(normalized) != null) throw emailTaken()
        val user = try {
            users.save(
                AppUser(email = normalized, passwordHash = passwordEncoder.encode(password), demo = false, createdAt = now(), expiresAt = null),
            )
        } catch (_: DuplicateKeyException) {
            throw emailTaken()
        }
        return issue(user)
    }

    /** null — невірні облікові дані (без розрізнення «немає такого email» / «невірний пароль»). */
    @Transactional
    fun signIn(email: String, password: String): IssuedSession? {
        val user = users.findByEmailIgnoreCase(email.trim())
        if (user?.passwordHash == null) {
            passwordEncoder.matches(password, dummyHash) // вирівнювання часу відповіді
            return null
        }
        if (!passwordEncoder.matches(password, user.passwordHash)) return null
        return issue(user)
    }

    /** Новий ізольований демо-акаунт з чистою копією сид-даних; дати відносно «сьогодні» користувача. */
    @Transactional
    fun startDemo(today: LocalDate): IssuedSession {
        val now = now()
        val user = users.save(AppUser(email = null, passwordHash = null, demo = true, createdAt = now, expiresAt = now.plus(props.demo.ttl)))
        demoSeeder.seed(user.id!!, today, now)
        return issue(user)
    }

    @Transactional
    fun signOut(user: CurrentUser) {
        if (user.demo) {
            // Демо приватне: вихід одразу видаляє тимчасовий акаунт і всі його дані.
            sessions.deleteById(user.sessionId)
            users.deleteById(user.id)
        } else {
            sessions.deleteById(user.sessionId)
        }
    }

    @Transactional
    fun lookup(token: String): SessionLookup {
        val session = sessions.findByTokenHash(SessionTokens.hash(token)) ?: return SessionLookup.Invalid
        val now = now()
        val user = session.userId?.let { users.findById(it).orElse(null) }
        if (user == null) {
            return if (session.demo) SessionLookup.DemoExpired else SessionLookup.Invalid
        }
        if (user.demo) {
            if (!user.expiresAt!!.isAfter(now)) {
                users.deleteById(user.id!!) // не чекаємо планувальника: прострочене демо видаляється при першому ж зверненні
                return SessionLookup.DemoExpired
            }
            return SessionLookup.Valid(user.toCurrent(session.id!!), null)
        }
        if (!session.expiresAt.isAfter(now)) {
            sessions.deleteById(session.id!!)
            return SessionLookup.Invalid
        }
        // Ковзне подовження: коли лишилося менше половини терміну — продовжуємо на повний.
        val renewed = if (session.expiresAt.isBefore(now.plus(props.session.ttl.dividedBy(2)))) {
            now.plus(props.session.ttl).also { sessions.updateExpiry(session.id!!, it) }
        } else {
            null
        }
        return SessionLookup.Valid(user.toCurrent(session.id!!), renewed)
    }

    private fun issue(user: AppUser): IssuedSession {
        val token = SessionTokens.newToken()
        val expiresAt = if (user.demo) user.expiresAt!! else now().plus(props.session.ttl)
        sessions.save(UserSession(tokenHash = SessionTokens.hash(token), userId = user.id, demo = user.demo, createdAt = now(), expiresAt = expiresAt))
        val cookieExpiresAt = if (user.demo) expiresAt.plus(props.demo.tombstoneRetention) else expiresAt
        return IssuedSession(token, expiresAt, cookieExpiresAt, user.currency)
    }

    private fun AppUser.toCurrent(sessionId: Long) =
        CurrentUser(id = id!!, email = email, demo = demo, currency = currency, demoExpiresAt = expiresAt, sessionId = sessionId)

    private fun emailTaken() = ApiException(HttpStatus.CONFLICT, "email_taken", "An account with this email already exists.")
}
