package com.minicrm.common.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * «Сьогодні» користувача. Браузер надсилає свій IANA-пояс у заголовку `X-Time-Zone` з кожним запитом,
 * тож межі Today/Overdue на сервері збігаються з клієнтськими. Невідомий/відсутній пояс → UTC.
 */
@Component
class UserClock(private val clock: Clock) {

    fun zone(): ZoneId = zoneOf(currentRequest()?.getHeader(HEADER))

    fun today(): LocalDate = LocalDate.now(clock.withZone(zone()))

    fun instant() = clock.instant()

    companion object {
        const val HEADER = "X-Time-Zone"

        fun zoneOf(raw: String?): ZoneId {
            if (raw.isNullOrBlank() || raw.length > 64) return ZoneOffset.UTC
            return try {
                ZoneId.of(raw.trim())
            } catch (_: DateTimeException) {
                ZoneOffset.UTC
            }
        }

        private fun currentRequest(): HttpServletRequest? =
            (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
    }
}
