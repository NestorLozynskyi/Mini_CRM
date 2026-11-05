package com.minicrm.api

import com.minicrm.support.IntegrationTest
import com.minicrm.support.items
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ClientCascadeTest : IntegrationTest() {

    @Test
    fun `deleting a client removes its deals (incl archived), interactions, tasks and contacts in one go`() {
        val b = browser().apply { signUp(uniqueEmail()) }
        val target = b.createClient("Cascade Co", tags = listOf("Only here", "Shared"))
        val keep = b.createClient("Keep Co", tags = listOf("Shared"))

        repeat(2) { b.post("/api/clients/$target/contacts", """{"name":"Person $it","email":"p$it@cascade.co"}""") }
        val deal1 = b.createDeal(target, "Open deal")
        val deal2 = b.createDeal(target, "Archived deal", stage = "won")
        b.post("/api/deals/$deal2/archive")
        b.createInteraction("\"clientId\":$target", "Client-level note")
        b.createInteraction("\"dealId\":$deal1", "Deal-level call")
        b.createInteraction("\"dealId\":$deal2", "Archived deal email")
        b.createTask("\"clientId\":$target", "Client task")
        b.createTask("\"dealId\":$deal1", "Deal task")

        val keepDeal = b.createDeal(keep, "Keep deal")
        b.createInteraction("\"dealId\":$keepDeal", "Keep call")
        b.createTask("\"clientId\":$keep", "Keep task")
        b.post("/api/clients/$keep/contacts", """{"name":"Keeper"}""")

        // Діалог підтвердження отримує точні кількості
        val preview = b.get("/api/clients/$target/delete-preview").body!!
        assertEquals(2, preview["contacts"].asInt())
        assertEquals(1, preview["deals"].asInt())
        assertEquals(1, preview["archivedDeals"].asInt())
        assertEquals(3, preview["interactions"].asInt())
        assertEquals(2, preview["tasks"].asInt())

        assertEquals(200, b.delete("/api/clients/$target").status)

        val p = "c" to target
        assertEquals(0L, count("SELECT count(*) FROM client WHERE id = :c", p))
        assertEquals(0L, count("SELECT count(*) FROM contact WHERE client_id = :c", p))
        assertEquals(0L, count("SELECT count(*) FROM deal WHERE client_id = :c", p))
        assertEquals(0L, count("SELECT count(*) FROM deal WHERE id IN (:d)", "d" to listOf(deal1, deal2)))
        assertEquals(0L, count("SELECT count(*) FROM interaction WHERE client_id = :c OR deal_id IN (:d)", p, "d" to listOf(deal1, deal2)))
        assertEquals(0L, count("SELECT count(*) FROM task WHERE client_id = :c OR deal_id IN (:d)", p, "d" to listOf(deal1, deal2)))
        assertEquals(0L, count("SELECT count(*) FROM client_tag WHERE client_id = :c", p))

        // Інший клієнт не зачеплений; тег, що лишився без клієнтів, зник
        val k = b.get("/api/clients/$keep").body!!
        assertEquals(listOf("Keeper"), k["contacts"].items().map { it["name"].asString() })
        assertEquals(listOf("Shared"), k["tags"].items().map { it.asString() })
        assertEquals(1, b.get("/api/interactions?clientId=$keep").body!!.size())
        assertEquals(1, b.get("/api/tasks?clientId=$keep").body!!.size())
        assertEquals(listOf("Shared"), b.get("/api/tags").body!!.items().map { it["name"].asString() })
        assertEquals(404, b.get("/api/deals/$deal1").status)
    }
}
