package com.minicrm.auth

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class SignUpRequest(
    @field:NotBlank(message = "Enter your email address")
    @field:Pattern(regexp = EMAIL_REGEX, message = "Enter a valid email address")
    @field:Size(max = 254, message = "Email must be at most 254 characters")
    val email: String?,

    @field:NotBlank(message = "Create a password")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    @field:Size(max = 64, message = "Password must be at most 64 characters")
    val password: String?,
)

data class SignInRequest(
    @field:NotBlank(message = "Enter your email address")
    @field:Pattern(regexp = EMAIL_REGEX, message = "Enter a valid email address")
    val email: String?,

    @field:NotBlank(message = "Enter your password")
    @field:Size(max = 200, message = "Password is too long")
    val password: String?,
)

data class MeResponse(
    val email: String?,
    val demo: Boolean,
    val currency: String,
    /** Для демо — момент автоматичного видалення акаунта. */
    val demoExpiresAt: Instant?,
)

/** Те саме правило, що й на клієнті (макет sign-in/sign-up): x@y.zz */
const val EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$"
