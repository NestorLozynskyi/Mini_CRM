package com.minicrm.auth

import com.minicrm.config.AppProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Cookie сесії: HttpOnly, SameSite=Lax, Secure коли запит прийшов через HTTPS (Caddy на VPS). */
@Component
class SessionCookies(private val props: AppProperties, private val clock: Clock) {

    val name: String get() = props.session.cookieName

    fun read(request: HttpServletRequest): String? = request.cookies?.firstOrNull { it.name == name }?.value

    fun write(request: HttpServletRequest, response: HttpServletResponse, token: String, expiresAt: Instant) {
        val maxAge = Duration.between(clock.instant(), expiresAt).coerceAtLeast(Duration.ZERO)
        response.addHeader(HttpHeaders.SET_COOKIE, base(request, token).maxAge(maxAge).build().toString())
    }

    fun clear(request: HttpServletRequest, response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, base(request, "").maxAge(0).build().toString())
    }

    private fun base(request: HttpServletRequest, value: String) =
        ResponseCookie.from(name, value).httpOnly(true).secure(request.isSecure).sameSite("Lax").path("/")
}
