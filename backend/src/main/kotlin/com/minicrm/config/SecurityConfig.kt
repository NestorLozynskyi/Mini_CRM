package com.minicrm.config

import com.minicrm.auth.AuthService
import com.minicrm.auth.SessionAuthenticationFilter
import com.minicrm.auth.SessionCookies
import com.minicrm.common.error.ApiExceptionHandler
import com.minicrm.common.error.ErrorCodes
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.csrf.CsrfException
import tools.jackson.databind.json.JsonMapper
import java.net.URI

/**
 * Автентифікація: власні непрозорі токени сесій у HttpOnly-cookie (таблиця user_session), HTTP-сесій немає.
 * CSRF: стандартний для SPA захист Spring Security — токен у cookie XSRF-TOKEN, клієнт повертає його
 * у заголовку X-XSRF-TOKEN для кожного запиту, що змінює стан (+ SameSite=Lax на cookie сесії).
 */
@Configuration
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder() // bcrypt

    @Bean
    fun securityFilterChain(http: HttpSecurity, auth: AuthService, cookies: SessionCookies, jsonMapper: JsonMapper): SecurityFilterChain {
        fun write(request: HttpServletRequest, response: HttpServletResponse, status: HttpStatus, code: String, detail: String) {
            val problem: ProblemDetail = ApiExceptionHandler.problem(status, code, detail).apply { instance = URI.create(request.requestURI) }
            response.status = status.value()
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.characterEncoding = "UTF-8"
            jsonMapper.writeValue(response.outputStream, problem)
        }

        http
            .csrf { it.spa() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .addFilterBefore(SessionAuthenticationFilter(auth, cookies), AnonymousAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/sign-up", "/api/auth/sign-in", "/api/auth/demo", "/api/auth/csrf").permitAll()
                it.requestMatchers("/actuator/health", "/error").permitAll()
                it.requestMatchers("/api/**").authenticated()
                it.anyRequest().denyAll()
            }
            .exceptionHandling { eh ->
                eh.authenticationEntryPoint { request, response, _ ->
                    if (request.getAttribute(SessionAuthenticationFilter.DEMO_EXPIRED_ATTR) == true) {
                        write(request, response, HttpStatus.UNAUTHORIZED, ErrorCodes.DEMO_EXPIRED, "Your demo session has ended.")
                    } else {
                        write(request, response, HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, "Sign in to continue.")
                    }
                }
                eh.accessDeniedHandler { request, response, ex ->
                    if (ex is CsrfException) {
                        write(request, response, HttpStatus.FORBIDDEN, "csrf_invalid", "Security token is missing or expired. Reload the page and try again.")
                    } else {
                        write(request, response, HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, "Access denied.")
                    }
                }
            }
        return http.build()
    }
}
