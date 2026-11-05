package com.minicrm.api

import com.minicrm.support.ApiSession
import com.minicrm.support.IntegrationTest
import com.minicrm.support.items
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Кожен користувач (зокрема демо) бачить і змінює лише свої дані — перевірка на рівні REST API. */
class IsolationApiTest : IntegrationTest() {

    private data class Owned(val client: Long, val contact: Long, val deal: Long, val interaction: Long, val task: Long)

    private fun seedOwned(s: ApiSession): Owned {
        val client = s.createClient("Alice Secret Co", tags = listOf("Secret"))
        val contact = s.post("/api/clients/$client/contacts", """{"name":"Hidden Person"}""").body!!["id"].asLong()
        val deal = s.createDeal(client, "Alice Secret Deal", 5000)
        val interaction = s.createInteraction("\"dealId\":$deal", "Alice secret call")
        val task = s.createTask("\"clientId\":$client", "Alice secret task")
        return Owned(client, contact, deal, interaction, task)
    }

    @Test
    fun `registered user cannot read, list, modify or delete another user's records`() {
        val alice = browser().apply { signUp(uniqueEmail()) }
        val bob = browser().apply { signUp(uniqueEmail()) }
        assertNoAccess(seedOwned(alice), alice, bob)
    }

    @Test
    fun `demo accounts are isolated from registered users and from each other`() {
        val alice = browser().apply { signUp(uniqueEmail()) }
        val demo1 = browser().apply { startDemo() }
        val demo2 = browser().apply { startDemo() }
        assertNoAccess(seedOwned(alice), alice, demo1)
        assertNoAccess(seedOwned(demo1), demo1, demo2)
        // Демо 2 не бачить змін демо 1 — у кожного своя копія сид-даних
        val names1 = demo1.get("/api/clients").body!!.items().map { it["name"].asString() }
        val names2 = demo2.get("/api/clients").body!!.items().map { it["name"].asString() }
        assertTrue("Alice Secret Co" in names1)
        assertFalse("Alice Secret Co" in names2)
    }

    private fun assertNoAccess(o: Owned, owner: ApiSession, other: ApiSession) {
        // Власник бачить
        assertEquals(200, owner.get("/api/clients/${o.client}").status)

        // Читання чужого = 404 (не розкриваємо існування)
        listOf("/api/clients/${o.client}", "/api/deals/${o.deal}", "/api/clients/${o.client}/delete-preview").forEach {
            assertEquals(404, other.get(it).status, "GET $it")
        }
        // Списки та фільтри за чужими id не повертають нічого
        assertTrue(other.get("/api/clients").body!!.items().none { it["id"].asLong() == o.client })
        assertTrue(other.get("/api/deals?scope=all").body!!.items().none { it["id"].asLong() == o.deal })
        assertEquals(0, other.get("/api/interactions?clientId=${o.client}").body!!.size())
        assertEquals(0, other.get("/api/interactions?dealId=${o.deal}").body!!.size())
        assertEquals(0, other.get("/api/tasks?clientId=${o.client}").body!!.size())
        assertTrue(other.get("/api/tags").body!!.items().none { it["name"].asString() == "Secret" })
        assertFalse(other.get("/api/export/clients.csv").raw.contains("Alice Secret"))
        assertFalse(other.get("/api/export/deals.csv").raw.contains("Alice Secret"))

        // Зміни чужого = 404
        val results = listOf(
            other.put("/api/clients/${o.client}", """{"type":"company","name":"Hacked"}"""),
            other.post("/api/clients/${o.client}/contacts", """{"name":"Intruder"}"""),
            other.put("/api/contacts/${o.contact}", """{"name":"Hacked"}"""),
            other.put("/api/deals/${o.deal}", """{"title":"Hacked","clientId":${o.client},"amount":1,"stage":"lead"}"""),
            other.patch("/api/deals/${o.deal}/stage", """{"stage":"won"}"""),
            other.post("/api/deals/${o.deal}/archive"),
            other.put("/api/interactions/${o.interaction}", """{"type":"note","date":"2026-11-01","description":"Hacked","clientId":${o.client}}"""),
            other.put("/api/tasks/${o.task}", """{"title":"Hacked","due":"2026-11-01","clientId":${o.client}}"""),
            other.patch("/api/tasks/${o.task}/done", """{"done":true}"""),
            // Не можна прив'язати власні записи до чужого клієнта/угоди
            other.post("/api/deals", """{"title":"Steal","clientId":${o.client},"amount":1,"stage":"lead"}"""),
            other.post("/api/tasks", """{"title":"Steal","due":"2026-11-01","dealId":${o.deal}}"""),
            other.post("/api/interactions", """{"type":"note","date":"2026-11-01","description":"Steal","clientId":${o.client}}"""),
            other.delete("/api/contacts/${o.contact}"),
            other.delete("/api/interactions/${o.interaction}"),
            other.delete("/api/tasks/${o.task}"),
            other.delete("/api/clients/${o.client}"),
        )
        results.forEachIndexed { i, r -> assertEquals(404, r.status, "write #$i: ${r.raw}") }

        // Дані власника не змінились
        val client = owner.get("/api/clients/${o.client}").body!!
        assertEquals("Alice Secret Co", client["name"].asString())
        assertEquals(listOf("Hidden Person"), client["contacts"].items().map { it["name"].asString() })
        val deal = owner.get("/api/deals/${o.deal}").body!!
        assertEquals("lead", deal["stage"].asString())
        assertFalse(deal["archived"].asBoolean())
        assertEquals(1, owner.get("/api/interactions?dealId=${o.deal}").body!!.size())
        val task = owner.get("/api/tasks?clientId=${o.client}").body!!.items().single()
        assertFalse(task["done"].asBoolean())
        assertEquals("Alice secret task", task["title"].asString())
    }
}
