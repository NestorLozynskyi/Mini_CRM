package com.minicrm.auth

import com.minicrm.common.error.ApiException
import com.minicrm.common.web.RateLimiter
import com.minicrm.config.AppProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Захист від масового створення демо-акаунтів:
 * 1) не більше `app.demo.per-ip-limit` запусків з однієї IP за `per-ip-window`;
 * 2) глобальна стеля одночасно активних демо `app.demo.max-active`.
 * Прострочені демо видаляються планувальником, тож стеля звільняється сама.
 */
@Component
class DemoGuard(
    private val props: AppProperties,
    private val users: AppUserRepository,
    private val clock: Clock,
) {
    private val perIp = RateLimiter(clock, props.demo.perIpLimit, props.demo.perIpWindow)

    fun checkAndRecord(clientIp: String) {
        if (users.countActiveDemos(clock.instant()) >= props.demo.maxActive) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "demo_capacity", "Too many demo sessions are running right now. Please try again later or create an account.")
        }
        if (!perIp.tryAcquire(clientIp)) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "demo_rate_limited", "You’ve started several demos recently. Please wait a while or create an account.")
        }
    }
}

/** Гальмування підбору пароля: після N невдалих спроб для пари IP+email — 429 до кінця вікна. */
@Component
class LoginThrottle(props: AppProperties, clock: Clock) {
    private val failures = RateLimiter(clock, props.login.maxFailures, props.login.window)

    private fun key(ip: String, email: String) = ip + "|" + email.trim().lowercase()

    fun check(ip: String, email: String) {
        if (failures.isLimited(key(ip, email))) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_attempts", "Too many sign-in attempts. Please wait a few minutes and try again.")
        }
    }

    fun recordFailure(ip: String, email: String) {
        failures.tryAcquire(key(ip, email))
    }

    fun recordSuccess(ip: String, email: String) = failures.reset(key(ip, email))
}
