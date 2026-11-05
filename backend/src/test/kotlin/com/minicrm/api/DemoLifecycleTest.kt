package com.minicrm.api

import com.minicrm.demo.DemoCleanupJob
import com.minicrm.support.IntegrationTest
import com.minicrm.support.items
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoLifecycleTest : IntegrationTest() {

    @Autowired
    lateinit var cleanup: DemoCleanupJob

    private fun demoUserIds(): List<Long> = jdbc.sql("SELECT id FROM app_user WHERE demo ORDER BY id").query { rs, _ -> rs.getLong(1) }.list()

    private fun ownedRows(userId: Long) = listOf("client", "contact", "deal", "interaction", "task", "tag")
        .associateWith { count("SELECT count(*) FROM $it WHERE owner_id = :u", "u" to userId) }

    @Test
    fun `try demo creates a private account with a fresh copy of realistic sample data`() {
        val demo = browser()
        val me = demo.startDemo().body!!
        assertTrue(me["demo"].asBoolean())
        assertEquals(clock.instant().plus(Duration.ofHours(24)), Instant.parse(me["demoExpiresAt"].asString()))

        val userId = demoUserIds().last()
        assertEquals(mapOf("client" to 12L, "contact" to 11L, "deal" to 17L, "interaction" to 16L, "task" to 14L, "tag" to 6L), ownedRows(userId))

        // Угоди на всіх етапах, архівні окремо
        val board = demo.get("/api/deals").body!!.items()
        assertEquals(14, board.size)
        assertEquals(setOf("lead", "contacted", "proposal", "negotiation", "won", "lost"), board.map { it["stage"].asString() }.toSet())
        assertEquals(3, demo.get("/api/deals?scope=archived").body!!.size())

        // Завдання у Today / Upcoming / Overdue / виконаних — дати відносно моменту створення
        val today = "2026-11-04"
        val tasks = demo.get("/api/tasks").body!!.items()
        val open = tasks.filter { !it["done"].asBoolean() }
        assertEquals(3, open.count { it["due"].asString() < today })
        assertEquals(3, open.count { it["due"].asString() == today })
        assertEquals(5, open.count { it["due"].asString() > today })
        assertEquals(3, tasks.count { it["done"].asBoolean() })
        assertEquals(6, demo.get("/api/tasks/summary").body!!["dueCount"].asInt())

        val dash = demo.get("/api/dashboard").body!!
        assertEquals(10, dash["activeDeals"].asInt())
        assertEquals(74_500, dash["activeValue"].asLong())
        assertEquals(9_600, dash["wonValue"].asLong())
        assertEquals(false, dash["empty"].asBoolean())
    }

    @Test
    fun `demo data is editable and survives page reloads, new demo is always a clean copy`() {
        val demo = browser()
        demo.startDemo()
        val clients = demo.get("/api/clients").body!!.items()
        val northwind = clients.first { it["name"].asString() == "Northwind Studio" }["id"].asLong()
        assertEquals(200, demo.put("/api/clients/$northwind", """{"type":"company","name":"Northwind Renamed"}""").status)
        val maya = clients.first { it["name"].asString() == "Maya Chen" }["id"].asLong()
        assertEquals(200, demo.delete("/api/clients/$maya").status)

        // «Перезавантаження» — нові запити з тим самим cookie: зміни на місці
        val after = demo.get("/api/clients").body!!.items().map { it["name"].asString() }
        assertTrue("Northwind Renamed" in after)
        assertTrue("Maya Chen" !in after)
        assertEquals(11, after.size)

        // Новий запуск Try demo (в тому ж браузері) — окремий акаунт із чистою копією
        demo.startDemo()
        val fresh = demo.get("/api/clients").body!!.items().map { it["name"].asString() }
        assertEquals(12, fresh.size)
        assertTrue("Northwind Studio" in fresh && "Maya Chen" in fresh)
    }

    @Test
    fun `after 24 hours the demo session ends, account and all its data are deleted`() {
        val demo = browser()
        demo.startDemo()
        val userId = demoUserIds().last()
        assertEquals(200, demo.get("/api/auth/me").status)

        clock.advance(Duration.ofHours(23).plusMinutes(59))
        assertEquals(200, demo.get("/api/clients").status, "still valid just before 24h")

        clock.advance(Duration.ofMinutes(1))
        val expired = demo.get("/api/clients")
        assertEquals(401, expired.status)
        assertEquals("demo_expired", expired.code)
        // Дані видалені одразу при зверненні після закінчення терміну
        assertEquals(0L, count("SELECT count(*) FROM app_user WHERE id = :u", "u" to userId))
        assertTrue(ownedRows(userId).values.all { it == 0L })
        // І надалі користувач бачить саме «demo expired», а не загальну помилку
        assertEquals("demo_expired", demo.get("/api/auth/me").code)
    }

    @Test
    fun `scheduled cleanup deletes expired demos that nobody revisits`() {
        val stale = browser().apply { startDemo() }
        val staleId = demoUserIds().last()
        clock.advance(Duration.ofHours(12))
        browser().startDemo()
        val freshId = demoUserIds().last()
        val registered = browser().apply { signUp(uniqueEmail()) }
        registered.createClient("Registered Co")

        clock.advance(Duration.ofHours(12).plusSeconds(1)) // stale: 24h+1s, fresh: 12h
        val result = cleanup.run()
        assertTrue(result.demos >= 1)
        assertEquals(0L, count("SELECT count(*) FROM app_user WHERE id = :u", "u" to staleId))
        assertTrue(ownedRows(staleId).values.all { it == 0L })
        assertEquals(1L, count("SELECT count(*) FROM app_user WHERE id = :u", "u" to freshId))
        assertEquals(12L, ownedRows(freshId)["client"])
        // Зареєстровані акаунти не зачіпаються
        assertEquals(200, registered.get("/api/clients").status)
        assertEquals("demo_expired", stale.get("/api/auth/me").code)

        // Після спливу 7 днів «надгробок» сесії теж прибирається — далі звичайне «не автентифіковано»
        clock.advance(Duration.ofDays(8))
        cleanup.run()
        assertEquals("unauthenticated", stale.get("/api/auth/me").code)
    }

    @Test
    fun `signing out of a demo deletes it immediately`() {
        val demo = browser().apply { startDemo() }
        val id = demoUserIds().last()
        assertEquals(204, demo.post("/api/auth/sign-out").status)
        assertEquals(0L, count("SELECT count(*) FROM app_user WHERE id = :u", "u" to id))
        assertTrue(ownedRows(id).values.all { it == 0L })
    }

    @Test
    fun `mass demo creation from one IP is rate limited`() {
        val ip = browser() // одна IP для всіх запусків
        repeat(3) { assertEquals(201, ip.post("/api/auth/demo").status) }
        val blocked = ip.post("/api/auth/demo")
        assertEquals(429, blocked.status)
        assertEquals("demo_rate_limited", blocked.code)
        // Через годину ліміт знову доступний
        clock.advance(Duration.ofMinutes(61))
        assertEquals(201, ip.post("/api/auth/demo").status)
    }

    @Test
    fun `demo dates follow the browser time zone`() {
        clock.now = Instant.parse("2026-11-04T23:30:00Z") // у Токіо вже 5 листопада
        val tokyo = browser("Asia/Tokyo")
        tokyo.startDemo()
        val dueToday = tokyo.get("/api/dashboard").body!!["today"].asString()
        assertEquals("2026-11-05", dueToday)
        val todayTasks = tokyo.get("/api/tasks").body!!.items().filter { it["due"].asString() == "2026-11-05" && !it["done"].asBoolean() }
        assertEquals(3, todayTasks.size)
        assertEquals(6, tokyo.get("/api/tasks/summary").body!!["dueCount"].asInt())
        // Той самий акаунт із поясом UTC: «сьогодні» ще 4-те, тож ці 3 завдання — майбутні
        tokyo.timeZone = "UTC"
        assertEquals(3, tokyo.get("/api/tasks/summary").body!!["dueCount"].asInt())
    }
}
