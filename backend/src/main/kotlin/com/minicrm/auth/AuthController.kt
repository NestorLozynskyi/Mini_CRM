package com.minicrm.auth

import com.minicrm.common.error.ApiException
import com.minicrm.common.web.UserClock
import com.minicrm.common.web.currentUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val auth: AuthService,
    private val cookies: SessionCookies,
    private val demoGuard: DemoGuard,
    private val loginThrottle: LoginThrottle,
    private val userClock: UserClock,
) {
    /** Видає CSRF-cookie (XSRF-TOKEN) до першого POST; викликається клієнтом при старті. */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun csrf(token: CsrfToken) {
        token.token
    }

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody body: SignUpRequest, request: HttpServletRequest, response: HttpServletResponse): MeResponse {
        val session = auth.signUp(body.email!!, body.password!!)
        cookies.write(request, response, session.token, session.cookieExpiresAt)
        return MeResponse(email = body.email.trim().lowercase(), demo = false, currency = session.currency, demoExpiresAt = null)
    }

    @PostMapping("/sign-in")
    fun signIn(@Valid @RequestBody body: SignInRequest, request: HttpServletRequest, response: HttpServletResponse): MeResponse {
        val ip = request.remoteAddr ?: "unknown"
        loginThrottle.check(ip, body.email!!)
        val session = auth.signIn(body.email, body.password!!)
        if (session == null) {
            loginThrottle.recordFailure(ip, body.email)
            throw ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Incorrect email or password.")
        }
        loginThrottle.recordSuccess(ip, body.email)
        cookies.write(request, response, session.token, session.cookieExpiresAt)
        return MeResponse(email = body.email.trim().lowercase(), demo = false, currency = session.currency, demoExpiresAt = null)
    }

    @PostMapping("/demo")
    @ResponseStatus(HttpStatus.CREATED)
    fun demo(request: HttpServletRequest, response: HttpServletResponse): MeResponse {
        demoGuard.checkAndRecord(request.remoteAddr ?: "unknown")
        val session = auth.startDemo(userClock.today())
        cookies.write(request, response, session.token, session.cookieExpiresAt)
        return MeResponse(email = null, demo = true, currency = session.currency, demoExpiresAt = session.expiresAt)
    }

    @PostMapping("/sign-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun signOut(request: HttpServletRequest, response: HttpServletResponse) {
        auth.signOut(currentUser())
        cookies.clear(request, response)
    }

    @GetMapping("/me")
    fun me(): MeResponse = currentUser().let { MeResponse(it.email, it.demo, it.currency, it.demoExpiresAt) }
}
