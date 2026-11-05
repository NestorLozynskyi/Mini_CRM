package com.minicrm.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/** Відновлює автентифікацію з cookie сесії для кожного запиту (сервер не тримає HTTP-сесій). */
class SessionAuthenticationFilter(
    private val auth: AuthService,
    private val cookies: SessionCookies,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest) = !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = cookies.read(request)
        if (token != null) {
            if (!SessionTokens.looksValid(token)) {
                cookies.clear(request, response)
            } else {
                when (val result = auth.lookup(token)) {
                    is SessionLookup.Valid -> {
                        val authority = SimpleGrantedAuthority(if (result.user.demo) "ROLE_DEMO" else "ROLE_USER")
                        SecurityContextHolder.getContext().authentication =
                            UsernamePasswordAuthenticationToken.authenticated(result.user, null, listOf(authority))
                        result.renewedUntil?.let { cookies.write(request, response, token, it) }
                    }
                    // Cookie лишається: кожне перезавантаження показуватиме екран demo-expired, доки його не замінить новий вхід/демо.
                    SessionLookup.DemoExpired -> request.setAttribute(DEMO_EXPIRED_ATTR, true)
                    SessionLookup.Invalid -> cookies.clear(request, response)
                }
            }
        }
        chain.doFilter(request, response)
    }

    companion object {
        const val DEMO_EXPIRED_ATTR = "minicrm.demoExpired"
    }
}
