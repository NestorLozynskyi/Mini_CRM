package com.minicrm.api

import com.minicrm.support.IntegrationTest
import com.minicrm.support.items
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Валюта акаунта: зміна, валідація ISO 4217, ізоляція між користувачами, демо стартує з USD, суми не конвертуються. */
class AccountCurrencyApiTest : IntegrationTest() {

    @Test
    fun `changing currency persists, survives sign-in and relabels CSV without touching amounts`() {
        val email = uniqueEmail()
        val s = browser().apply { signUp(email) }
        val client = s.createClient("Northwind")
        val deal = s.createDeal(client, "Rebrand", 12400)
        assertEquals("USD", s.get("/api/auth/me").body!!["currency"].asString())

        val r = s.put("/api/account/currency", """{"currency":"EUR"}""")
        assertEquals(200, r.status)
        assertEquals("EUR", r.body!!["currency"].asString())
        assertEquals(email, r.body!!["email"].asString())
        assertEquals("EUR", s.get("/api/auth/me").body!!["currency"].asString())

        // Суми не конвертуються
        assertEquals(12400, s.get("/api/deals/$deal").body!!["amount"].asLong())
        val csv = s.get("/api/export/deals.csv").raw
        assertTrue(csv.startsWith("﻿Title,Client,Stage,Amount (EUR),"), csv)
        assertTrue(csv.contains("Rebrand,Northwind,Lead,12400,"), csv)
        assertTrue(s.get("/api/export/clients.csv").raw.contains("Open deal value (EUR)"))

        // Повторний вхід (нова сесія) повертає збережену валюту
        val again = browser()
        val signIn = again.post("/api/auth/sign-in", """{"email":"$email","password":"correct-horse-1"}""")
        assertEquals(200, signIn.status)
        assertEquals("EUR", signIn.body!!["currency"].asString())
        assertEquals("EUR", again.get("/api/auth/me").body!!["currency"].asString())
    }

    @Test
    fun `only active ISO 4217 codes are accepted, as problem+json field errors`() {
        val s = browser().apply { signUp(uniqueEmail()) }
        listOf("\"BTC\"", "\"eur\"", "\"EURO\"", "\"XAU\"", "\"ANG\"", "\"\"", "null").forEach { value ->
            val r = s.put("/api/account/currency", """{"currency":$value}""")
            assertEquals(400, r.status, "currency=$value")
            assertEquals("validation_failed", r.body!!["code"].asString())
            assertEquals(setOf("currency"), r.fields())
            assertEquals(1, r.body!!["errors"].size(), "one message per field for $value")
        }
        assertEquals("USD", s.get("/api/auth/me").body!!["currency"].asString())
        listOf("UAH", "JPY", "XCG", "CHF").forEach {
            assertEquals(200, s.put("/api/account/currency", """{"currency":"$it"}""").status, it)
        }
    }

    @Test
    fun `currency change affects only the caller's account and requires auth and CSRF`() {
        val alice = browser().apply { signUp(uniqueEmail()) }
        val bob = browser().apply { signUp(uniqueEmail()) }
        val demo = browser().apply { startDemo() }
        assertEquals(200, alice.put("/api/account/currency", """{"currency":"GBP"}""").status)
        assertEquals(200, demo.put("/api/account/currency", """{"currency":"PLN"}""").status)
        assertEquals("GBP", alice.get("/api/auth/me").body!!["currency"].asString())
        assertEquals("USD", bob.get("/api/auth/me").body!!["currency"].asString())
        assertEquals("PLN", demo.get("/api/auth/me").body!!["currency"].asString())
        assertTrue(bob.get("/api/export/deals.csv").raw.contains("Amount (USD)"))

        assertEquals(401, browser().put("/api/account/currency", """{"currency":"EUR"}""").status)
        assertEquals(403, bob.send("PUT", "/api/account/currency", """{"currency":"EUR"}""", withCsrf = false).status)
        assertEquals("USD", bob.get("/api/auth/me").body!!["currency"].asString())
    }

    @Test
    fun `every new demo starts in USD and a change applies only to that demo`() {
        val first = browser()
        assertEquals("USD", first.startDemo().body!!["currency"].asString())
        first.put("/api/account/currency", """{"currency":"UAH"}""")
        assertEquals("UAH", first.get("/api/auth/me").body!!["currency"].asString())
        val amounts = first.get("/api/deals?scope=all").body!!.items().map { it["amount"].asLong() }

        val second = browser()
        assertEquals("USD", second.startDemo().body!!["currency"].asString())
        assertEquals("USD", second.get("/api/auth/me").body!!["currency"].asString())
        assertEquals(amounts.sorted(), second.get("/api/deals?scope=all").body!!.items().map { it["amount"].asLong() }.sorted())
    }
}
