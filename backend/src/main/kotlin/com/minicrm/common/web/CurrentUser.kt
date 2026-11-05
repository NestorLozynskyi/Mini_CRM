package com.minicrm.common.web

import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant

/** Принципал автентифікованого запиту (див. SessionAuthenticationFilter). */
data class CurrentUser(
    val id: Long,
    val email: String?,
    val demo: Boolean,
    val currency: String,
    val demoExpiresAt: Instant?,
    val sessionId: Long,
)

/** Id власника даних поточного запиту. Ізоляція: кожен запит до БД фільтрується за цим значенням. */
fun currentUser(): CurrentUser =
    SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
        ?: error("No authenticated user in security context")
