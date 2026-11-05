package com.minicrm.api

import com.minicrm.support.IntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportApiTest : IntegrationTest() {

    @Test
    fun `clients and deals CSV are UTF-8 with BOM, correctly escaped and scoped to the user`() {
        val b = browser().apply { signUp(uniqueEmail()) }
        val c = b.post(
            "/api/clients",
            """{"type":"company","name":"Harbor & Pine, \"Bakery\"","phone":"+1 (207) 555-0163","notes":"Line 1\nLine 2","tags":["Local","Retainer"]}""",
        ).body!!["id"].asLong()
        b.post("/api/clients/$c/contacts", """{"name":"Rosa Delgado","email":"rosa@hp.com"}""")
        b.post("/api/clients", """{"type":"person","name":"=HYPERLINK(\"x\")"}""")
        val d = b.createDeal(c, "Tomás – café menu", 1800, "lost")
        b.patch("/api/deals/$d/stage", """{"stage":"lost","lostReason":"Too pricey"}""")

        val clients = b.get("/api/export/clients.csv")
        assertEquals(200, clients.status)
        assertTrue(clients.headers.firstValue("Content-Type").get().startsWith("text/csv"))
        assertTrue(clients.headers.firstValue("Content-Disposition").get().contains("clients.csv"))
        assertEquals("2", clients.headers.firstValue("X-Row-Count").get())
        val csv = clients.raw
        assertTrue(csv.startsWith("﻿Name,Type,Phone,Email,Address,Website,Tags,Contact persons,"))
        assertTrue(csv.contains("\"Harbor & Pine, \"\"Bakery\"\"\",Company,+1 (207) 555-0163,"), csv)
        assertTrue(csv.contains("\"Line 1\nLine 2\""), "multiline notes quoted")
        assertTrue(csv.contains("Local; Retainer"))
        assertTrue(csv.contains("Rosa Delgado · rosa@hp.com"))
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""), "formula injection neutralised")
        assertTrue(csv.contains("\r\n"))

        val deals = b.get("/api/export/deals.csv").raw
        assertTrue(deals.startsWith("﻿Title,Client,Stage,Amount (USD),Expected close,Lost reason,Status,Created\r\n"))
        assertTrue(deals.contains("Tomás – café menu,\"Harbor & Pine, \"\"Bakery\"\"\",Lost,1800,2026-12-01,Too pricey,Active,"))
        assertEquals("0", b.get("/api/export/deals.csv?scope=archived").headers.firstValue("X-Row-Count").get())
    }
}
