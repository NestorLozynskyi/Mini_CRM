package com.minicrm.auth

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.ListCrudRepository
import java.time.Instant

@Table("app_user")
data class AppUser(
    @Id val id: Long? = null,
    val email: String?,
    val passwordHash: String?,
    val demo: Boolean,
    val currency: String = "USD",
    val createdAt: Instant,
    val expiresAt: Instant?,
)

@Table("user_session")
data class UserSession(
    @Id val id: Long? = null,
    val tokenHash: String,
    val userId: Long?,
    val demo: Boolean,
    val createdAt: Instant,
    val expiresAt: Instant,
)

interface AppUserRepository : ListCrudRepository<AppUser, Long> {
    @Query("SELECT * FROM app_user WHERE lower(email) = lower(:email)")
    fun findByEmailIgnoreCase(email: String): AppUser?

    @Query("SELECT count(*) FROM app_user WHERE demo AND expires_at > :now")
    fun countActiveDemos(now: Instant): Long

    /** Видаляє прострочені демо-акаунти; усі їхні дані зникають каскадом FK (ON DELETE CASCADE). */
    @Modifying
    @Query("DELETE FROM app_user WHERE demo AND expires_at <= :now")
    fun deleteExpiredDemos(now: Instant): Int
}

interface UserSessionRepository : ListCrudRepository<UserSession, Long> {
    fun findByTokenHash(tokenHash: String): UserSession?

    @Modifying
    @Query("UPDATE user_session SET expires_at = :expiresAt WHERE id = :id")
    fun updateExpiry(id: Long, expiresAt: Instant)

    /** Прострочені сесії зареєстрованих користувачів і старі «надгробки» видалених демо. */
    @Modifying
    @Query(
        """
        DELETE FROM user_session
        WHERE (NOT demo AND expires_at <= :now)
           OR (demo AND user_id IS NULL AND expires_at <= :tombstoneBorder)
        """,
    )
    fun purge(now: Instant, tombstoneBorder: Instant): Int
}
