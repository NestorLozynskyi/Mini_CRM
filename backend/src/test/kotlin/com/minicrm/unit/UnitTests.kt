package com.minicrm.unit

import com.minicrm.auth.SignUpRequest
import com.minicrm.clients.ClientRequest
import com.minicrm.clients.ClientType
import com.minicrm.common.web.RateLimiter
import com.minicrm.common.web.UserClock
import com.minicrm.deals.DealRequest
import com.minicrm.deals.DealStage
import com.minicrm.export.Csv
import com.minicrm.support.MutableClock
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unit-тести без Spring і БД: валідація DTO, CSV, ліміти, часові пояси. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnitTests {
    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    @AfterAll
    fun close() = factory.close()

    private fun fields(o: Any) = validator.validate(o).map { it.propertyPath.toString() }.toSortedSet().toList()
    private fun messages(o: Any) = validator.validate(o).map { it.message }.toSet()

    @Test
    fun `sign-up rules match the mockup messages`() {
        assertEquals(setOf("Enter a valid email address", "Password must be at least 8 characters"), messages(SignUpRequest("jordan@", "pine42")))
        assertTrue(messages(SignUpRequest("", "")).containsAll(setOf("Enter your email address", "Create a password")))
        assertTrue(fields(SignUpRequest("jordan.lee@example.com", "harbor-lights-7")).isEmpty())
    }

    @Test
    fun `client email and website rules accept blanks and mockup formats`() {
        fun c(email: String?, website: String?) = ClientRequest(ClientType.COMPANY, "N", email = email, website = website)
        assertTrue(fields(c("", "")).isEmpty())
        assertTrue(fields(c("hello@northwindstudio.co", "northwindstudio.co")).isEmpty())
        assertTrue(fields(c(null, "https://www.example.com/path")).isEmpty())
        assertEquals(listOf("email", "website"), fields(c("hello@", "not a site")))
        assertEquals(listOf("name", "type"), fields(ClientRequest(null, " ")))
    }

    @Test
    fun `deal amount must be a non-negative number`() {
        assertEquals(setOf("Amount can’t be negative."), messages(DealRequest("T", 1, -1, DealStage.LEAD)))
        assertEquals(setOf("Enter an amount (use 0 if it’s not known yet).", "Enter a deal title.", "Select a client."), messages(DealRequest(" ", null, null, DealStage.LEAD)))
        assertTrue(fields(DealRequest("T", 1, 0, DealStage.WON)).isEmpty())
    }

    @Test
    fun `csv cells are escaped per RFC 4180 and protected from formula injection`() {
        assertEquals("plain", Csv.cell("plain"))
        assertEquals("\"a,b\"", Csv.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.cell("say \"hi\""))
        assertEquals("\"two\nlines\"", Csv.cell("two\nlines"))
        assertEquals("'=SUM(A1)", Csv.cell("=SUM(A1)"))
        assertEquals("'@cmd", Csv.cell("@cmd"))
        assertEquals("+1 (503) 555-0142", Csv.cell("+1 (503) 555-0142"))
        assertEquals("-5", Csv.cell(-5L))
        assertEquals("", Csv.cell(null))
        assertEquals("﻿A,B\r\n1,\"x,y\"\r\n", Csv.write(listOf("A", "B"), listOf(listOf(1, "x,y"))))
    }

    @Test
    fun `rate limiter allows N events per window`() {
        val clock = MutableClock()
        val limiter = RateLimiter(clock, 2, Duration.ofMinutes(10))
        assertTrue(limiter.tryAcquire("ip"))
        assertTrue(limiter.tryAcquire("ip"))
        assertFalse(limiter.tryAcquire("ip"))
        assertTrue(limiter.tryAcquire("other"))
        clock.advance(Duration.ofMinutes(10))
        assertTrue(limiter.tryAcquire("ip"))
    }

    @Test
    fun `unknown or malicious time zone header falls back to UTC`() {
        assertEquals(ZoneOffset.UTC, UserClock.zoneOf(null))
        assertEquals(ZoneOffset.UTC, UserClock.zoneOf("Mars/Olympus"))
        assertEquals(ZoneOffset.UTC, UserClock.zoneOf("x".repeat(100)))
        assertEquals("Europe/Kyiv", UserClock.zoneOf("Europe/Kyiv").id)
    }
}
