package com.minicrm.api

import com.minicrm.support.IntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthApiTest : IntegrationTest() {

    @Test
    fun `sign-up validates input with field errors`() {
        val r = browser().post("/api/auth/sign-up", """{"email":"jordan@","password":"pine42"}""")
        assertEquals(400, r.status)
        assertEquals("validation_failed", r.code)
        assertEquals(setOf("email", "password"), r.fields())
    }

    @Test
    fun `blank fields get exactly one required-message each`() {
        val r = browser().post("/api/auth/sign-up", """{"email":"","password":""}""")
        val errors = r.body!!["errors"]
        assertEquals(2, errors.size())
        assertEquals(setOf("Enter your email address", "Create a password"), (0 until errors.size()).map { errors[it]["message"].asString() }.toSet())
    }

    @Test
    fun `sign-up creates account, session survives reloads, password stored as strong hash`() {
        val email = uniqueEmail()
        val b = browser()
        b.signUp(email)
        val cookie = b.cookie("mcrm_session")
        assertNotNull(cookie)
        // Новий запит з тим самим cookie (як після перезавантаження сторінки)
        val me = b.get("/api/auth/me")
        assertEquals(200, me.status)
        assertEquals(email, me.body!!["email"].asString())
        assertEquals(false, me.body!!["demo"].asBoolean())
        assertEquals("USD", me.body!!["currency"].asString())

        val hash = jdbc.sql("SELECT password_hash FROM app_user WHERE email = :e").param("e", email).query(String::class.java).single()
        assertTrue(hash.startsWith("{bcrypt}\$2"), "password must be bcrypt-hashed, got $hash")
        val stored = jdbc.sql("SELECT token_hash FROM user_session us JOIN app_user u ON u.id = us.user_id WHERE u.email = :e")
            .param("e", email).query(String::class.java).single()
        assertTrue(stored != cookie && stored.length == 64, "only token hash is stored")
    }

    @Test
    fun `duplicate email is rejected case-insensitively`() {
        val email = uniqueEmail()
        browser().signUp(email)
        val r = browser().post("/api/auth/sign-up", """{"email":"${email.uppercase()}","password":"another-pass-1"}""")
        assertEquals(409, r.status)
        assertEquals("email_taken", r.code)
    }

    @Test
    fun `sign-in with wrong or unknown credentials returns the same error`() {
        val email = uniqueEmail()
        browser().signUp(email, "correct-horse-1")
        val wrong = browser().post("/api/auth/sign-in", """{"email":"$email","password":"wrong-password"}""")
        val unknown = browser().post("/api/auth/sign-in", """{"email":"nobody-${uniqueEmail()}","password":"wrong-password"}""")
        assertEquals(401, wrong.status)
        assertEquals("invalid_credentials", wrong.code)
        assertEquals(401, unknown.status)
        assertEquals("invalid_credentials", unknown.code)

        val ok = browser().post("/api/auth/sign-in", """{"email":"$email","password":"correct-horse-1"}""")
        assertEquals(200, ok.status)
    }

    @Test
    fun `repeated failed sign-ins are throttled`() {
        val email = uniqueEmail()
        browser().signUp(email)
        val b = browser()
        repeat(5) { assertEquals(401, b.post("/api/auth/sign-in", """{"email":"$email","password":"nope-nope-1"}""").status) }
        val blocked = b.post("/api/auth/sign-in", """{"email":"$email","password":"correct-horse-1"}""")
        assertEquals(429, blocked.status)
        assertEquals("too_many_attempts", blocked.code)
    }

    @Test
    fun `protected API requires session and state-changing calls require CSRF token`() {
        val anon = browser()
        val r = anon.get("/api/clients")
        assertEquals(401, r.status)
        assertEquals("unauthenticated", r.code)

        val b = browser()
        b.signUp(uniqueEmail())
        val noCsrf = b.post("/api/clients", """{"type":"company","name":"X"}""", withCsrf = false)
        assertEquals(403, noCsrf.status)
        assertEquals("csrf_invalid", noCsrf.code)
        assertEquals(201, b.post("/api/clients", """{"type":"company","name":"X"}""").status)
    }

    @Test
    fun `sign-out invalidates the session on the server`() {
        val b = browser()
        b.signUp(uniqueEmail())
        val token = b.cookie("mcrm_session")!!
        assertEquals(204, b.post("/api/auth/sign-out").status)
        assertNull(b.cookie("mcrm_session")?.takeIf { it.isNotEmpty() })
        // Повторне використання старого токена (наприклад, скопійованого) не працює
        val replay = browser()
        val res = java.net.http.HttpClient.newHttpClient().send(
            java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:$port/api/auth/me")).header("Cookie", "mcrm_session=$token").build(),
            java.net.http.HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(401, res.statusCode())
        assertEquals(401, replay.get("/api/auth/me").status)
    }
}
