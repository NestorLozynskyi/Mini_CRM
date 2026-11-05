package com.minicrm.api

import com.minicrm.support.IntegrationTest
import com.minicrm.support.items
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Бізнес-сценарії клієнтів, угод, взаємодій, завдань і дашборда через REST. */
class CrmApiTest : IntegrationTest() {

    private fun user() = browser().apply { signUp(uniqueEmail()) }

    @Test
    fun `client CRUD with validation, tags and contact persons`() {
        val b = user()
        val bad = b.post("/api/clients", """{"type":"company","name":"  ","email":"nope","website":"not a url"}""")
        assertEquals(400, bad.status)
        assertEquals(setOf("name", "email", "website"), bad.fields())

        val created = b.post(
            "/api/clients",
            """{"type":"company","name":" Acme ","phone":"+1 555 0100","email":"hi@acme.io","website":"acme.io","notes":"N","tags":["VIP","vip","Local"]}""",
        )
        assertEquals(201, created.status)
        val id = created.body!!["id"].asLong()
        assertEquals("Acme", created.body!!["name"].asString())
        assertEquals(listOf("Local", "VIP"), created.body!!["tags"].items().map { it.asString() })

        val contact = b.post("/api/clients/$id/contacts", """{"name":"Olivia","email":"bad"}""")
        assertEquals(400, contact.status)
        val cid = b.post("/api/clients/$id/contacts", """{"name":"Olivia","email":"o@acme.io"}""").body!!["id"].asLong()
        assertEquals("Liv", b.put("/api/contacts/$cid", """{"name":"Liv","phone":"123"}""").body!!["name"].asString())

        val updated = b.put("/api/clients/$id", """{"type":"person","name":"Acme Person","tags":["Local"]}""").body!!
        assertEquals("person", updated["type"].asString())
        assertNull(updated["email"].asStringOpt().orElse(null))
        assertEquals(listOf("Local"), updated["tags"].items().map { it.asString() })
        assertEquals(listOf("Local"), b.get("/api/tags").body!!.items().map { it["name"].asString() }, "unused tag removed")

        assertEquals(204, b.delete("/api/contacts/$cid").status)
        assertEquals(0, b.get("/api/clients/$id").body!!["contacts"].size())
    }

    @Test
    fun `client list supports search by name, email and phone digits, type and tag filters`() {
        val b = user()
        b.post("/api/clients", """{"type":"company","name":"Northwind","phone":"+1 (503) 555-0142","tags":["VIP"]}""")
        b.post("/api/clients", """{"type":"person","name":"Maya Chen","email":"maya@fastmail.com","tags":["Local"]}""")
        b.post("/api/clients", """{"type":"company","name":"Bluefin","tags":["Local"]}""")
        fun names(q: String) = b.get("/api/clients$q").body!!.items().map { it["name"].asString() }
        assertEquals(listOf("Bluefin", "Maya Chen", "Northwind"), names(""))
        assertEquals(listOf("Northwind"), names("?q=north"))
        assertEquals(listOf("Maya Chen"), names("?q=FASTMAIL"))
        assertEquals(listOf("Northwind"), names("?q=5030142".let { "?q=503555" }))
        assertEquals(listOf("Maya Chen"), names("?type=person"))
        assertEquals(listOf("Bluefin", "Maya Chen"), names("?tag=local"))
        assertEquals(listOf("Bluefin", "Northwind"), names("?tag=Local&tag=VIP&type=company"))
        assertEquals(emptyList(), names("?q=xyz"))
    }

    @Test
    fun `deal stages, lost reason, archive and restore, client change moves records`() {
        val b = user()
        val c1 = b.createClient("One")
        val c2 = b.createClient("Two")
        val invalid = b.post("/api/deals", """{"title":"","clientId":null,"amount":-5,"stage":"lead"}""")
        assertEquals(setOf("title", "clientId", "amount"), invalid.fields())
        assertEquals(404, b.post("/api/deals", """{"title":"X","clientId":999999,"amount":1,"stage":"lead"}""").status)

        val deal = b.createDeal(c1, "Website", 8600, "proposal")
        b.createInteraction("\"dealId\":$deal")
        b.createTask("\"dealId\":$deal")

        // Drag & Drop / Move to… → Lost з причиною
        val lost = b.patch("/api/deals/$deal/stage", """{"stage":"lost","lostReason":" Too expensive "}""").body!!
        assertEquals("lost", lost["stage"].asString())
        assertEquals("Too expensive", lost["lostReason"].asString())
        // Повернення з Lost прибирає причину
        val back = b.patch("/api/deals/$deal/stage", """{"stage":"negotiation"}""").body!!
        assertTrue(back["lostReason"].isNull)
        assertEquals("negotiation", b.get("/api/deals/$deal").body!!["stage"].asString(), "stage persisted")

        b.post("/api/deals/$deal/archive")
        assertTrue(b.get("/api/deals").body!!.items().none { it["id"].asLong() == deal }, "archived hidden from board")
        assertEquals(listOf(deal), b.get("/api/deals?scope=archived").body!!.items().map { it["id"].asLong() })
        b.post("/api/deals/$deal/restore")
        assertEquals(listOf(deal), b.get("/api/deals").body!!.items().map { it["id"].asLong() })

        b.put("/api/deals/$deal", """{"title":"Website v2","clientId":$c2,"amount":9000,"stage":"won","expectedClose":null}""")
        assertEquals(1, b.get("/api/interactions?clientId=$c2").body!!.size())
        assertEquals(1, b.get("/api/tasks?clientId=$c2").body!!.size())
        assertEquals(0, b.get("/api/tasks?clientId=$c1").body!!.size())

        val list = b.get("/api/deals?q=two&stage=won").body!!.items()
        assertEquals(listOf("Website v2"), list.map { it["title"].asString() })
        assertEquals("Two", list.single()["clientName"].asString())
    }

    @Test
    fun `interactions and tasks require a link, client history includes deal records`() {
        val b = user()
        val c = b.createClient("Hist")
        val d = b.createDeal(c, "Deal")
        val noLink = b.post("/api/interactions", """{"type":"call","date":"2026-11-01","description":"x"}""")
        assertEquals(setOf("link"), noLink.fields())
        val both = b.post("/api/tasks", """{"title":"t","due":"2026-11-01","clientId":$c,"dealId":$d}""")
        assertEquals(setOf("link"), both.fields())
        assertEquals(setOf("description", "date", "type"), b.post("/api/interactions", """{"clientId":$c,"description":" "}""").fields())

        val i1 = b.createInteraction("\"clientId\":$c", "Client note", "2026-10-01")
        b.createInteraction("\"dealId\":$d", "Deal call", "2026-11-02")
        val history = b.get("/api/interactions?clientId=$c").body!!.items()
        assertEquals(listOf("Deal call", "Client note"), history.map { it["description"].asString() }, "newest first, deal records included")
        assertEquals("Deal", history.first()["dealTitle"].asString())
        assertEquals(1, b.get("/api/interactions?dealId=$d").body!!.size())

        val edited = b.put("/api/interactions/$i1", """{"type":"meeting","date":"2026-10-05","description":"Edited","dealId":$d}""").body!!
        assertEquals("meeting", edited["type"].asString())
        assertEquals(d, edited["dealId"].asLong())
        assertEquals(204, b.delete("/api/interactions/$i1").status)

        val t = b.createTask("\"dealId\":$d", "Prep", "2026-11-03")
        assertEquals(c, b.get("/api/tasks?clientId=$c").body!!.items().single()["clientId"].asLong())
        assertEquals(1, b.get("/api/tasks/summary").body!!["overdue"].asInt())
        assertTrue(b.patch("/api/tasks/$t/done", """{"done":true}""").body!!["done"].asBoolean())
        assertEquals(0, b.get("/api/tasks/summary").body!!["dueCount"].asInt())
        b.patch("/api/tasks/$t/done", """{"done":false}""")
        b.put("/api/tasks/$t", """{"title":"Prep v2","due":"2026-11-09","clientId":$c}""")
        val task = b.get("/api/tasks").body!!.items().single()
        assertEquals("Prep v2", task["title"].asString())
        assertTrue(task["dealId"].isNull)
        assertEquals(204, b.delete("/api/tasks/$t").status)
    }

    @Test
    fun `dashboard counts active deals and pipeline, empty for a brand-new account`() {
        val b = user()
        val empty = b.get("/api/dashboard").body!!
        assertTrue(empty["empty"].asBoolean())
        assertEquals(0, empty["activeDeals"].asInt())
        assertEquals(6, empty["stages"].size())

        val c = b.createClient("Dash")
        b.createDeal(c, "A", 1000, "lead")
        b.createDeal(c, "B", 2500, "negotiation")
        b.createDeal(c, "C", 4000, "won")
        b.createDeal(c, "D", 700, "lost")
        val archived = b.createDeal(c, "E", 9999, "proposal")
        b.post("/api/deals/$archived/archive")
        b.createTask("\"clientId\":$c", "today", "2026-11-04")
        b.createTask("\"clientId\":$c", "late", "2026-11-01")

        val d = b.get("/api/dashboard").body!!
        assertEquals(false, d["empty"].asBoolean())
        assertEquals(2, d["activeDeals"].asInt())
        assertEquals(3500, d["activeValue"].asLong())
        assertEquals(4, d["boardDeals"].asInt())
        assertEquals(4000, d["wonValue"].asLong())
        val proposal = d["stages"].items().first { it["stage"].asString() == "proposal" }
        assertEquals(0, proposal["count"].asInt(), "archived excluded")
        assertEquals(1, d["tasks"]["todayOpen"].asInt())
        assertEquals(1, d["tasks"]["overdue"].asInt())
        assertEquals(listOf("today"), d["todayTasks"].items().map { it["title"].asString() })
    }
}
