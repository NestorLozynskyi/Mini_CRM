package com.minicrm.export

import com.minicrm.clients.ClientFilter
import com.minicrm.clients.ClientType
import com.minicrm.common.web.currentUser
import com.minicrm.deals.dealFilter
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import com.minicrm.clients.ClientQueries
import com.minicrm.deals.DealQueries
import com.minicrm.deals.DealStage

/**
 * RFC 4180 CSV: UTF-8 з BOM (щоб Excel розпізнав кодування), роздільник «,», CRLF, лапки навколо полів
 * з комою/лапками/переносами, подвоєння лапок. Текст, що починається з = + - @ (крім номерів телефонів),
 * префіксується апострофом — захист від CSV/formula injection у табличних редакторах.
 */
object Csv {
    private const val BOM = "\uFEFF"

    /** Номери на кшталт «+1 (503) 555-0142» — не формули, лишаємо як є. */
    private val PHONE_LIKE = Regex("^[+-]?[0-9 ().-]+$")

    fun write(header: List<String>, rows: List<List<Any?>>): String = buildString {
        append(BOM)
        (listOf(header) + rows).forEach { row ->
            append(row.joinToString(",") { cell(it) })
            append("\r\n")
        }
    }

    fun cell(value: Any?): String {
        if (value == null) return ""
        var s = value.toString()
        if (value is String && s.isNotEmpty() && s[0] in "=+-@\t\r" && !PHONE_LIKE.matches(s)) s = "'$s"
        val needsQuotes = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || s.startsWith(" ") || s.endsWith(" ")
        return if (needsQuotes) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}

@Service
class ExportService(private val jdbc: JdbcClient, private val clients: ClientQueries, private val deals: DealQueries) {

    @Transactional(readOnly = true)
    fun clientsCsv(currency: String, ownerId: Long, filter: ClientFilter): Pair<String, Int> {
        val list = clients.list(ownerId, filter)
        val details = jdbc.sql("SELECT id, address, website, notes FROM client WHERE owner_id = :o").param("o", ownerId)
            .query { rs, _ -> rs.getLong("id") to listOf(rs.getString("address"), rs.getString("website"), rs.getString("notes")) }
            .list().toMap()
        val contacts = jdbc.sql("SELECT client_id, name, phone, email FROM contact WHERE owner_id = :o ORDER BY created_at, id").param("o", ownerId)
            .query { rs, _ ->
                rs.getLong("client_id") to listOfNotNull(rs.getString("name"), rs.getString("phone"), rs.getString("email")).joinToString(" · ")
            }.list().groupBy({ it.first }, { it.second })
        val rows = list.map { c ->
            val d = details[c.id].orEmpty()
            listOf(
                c.name, if (c.type == ClientType.COMPANY) "Company" else "Person", c.phone, c.email,
                d.getOrNull(0), d.getOrNull(1), c.tags.joinToString("; "), contacts[c.id].orEmpty().joinToString("; "),
                c.openDealCount, c.openDealValue, d.getOrNull(2), c.createdAt.toString().substring(0, 10),
            )
        }
        val header = listOf("Name", "Type", "Phone", "Email", "Address", "Website", "Tags", "Contact persons", "Open deals", "Open deal value ($currency)", "Notes", "Created")
        return Csv.write(header, rows) to list.size
    }

    @Transactional(readOnly = true)
    fun dealsCsv(currency: String, ownerId: Long, scope: String?, q: String?, clientId: Long?, stages: List<String>?): Pair<String, Int> {
        val list = deals.list(ownerId, dealFilter(scope ?: "all", q, clientId, stages))
        val label = DealStage.entries.associateWith { it.name.lowercase().replaceFirstChar(Char::uppercase) }
        val rows = list.map { d ->
            listOf(d.title, d.clientName, label[d.stage], d.amount, d.expectedClose?.toString(), d.lostReason, if (d.archived) "Archived" else "Active", d.createdAt.toString().substring(0, 10))
        }
        val header = listOf("Title", "Client", "Stage", "Amount ($currency)", "Expected close", "Lost reason", "Status", "Created")
        return Csv.write(header, rows) to list.size
    }
}

@RestController
class ExportController(private val service: ExportService) {

    @GetMapping("/api/export/clients.csv")
    fun clients(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false, name = "tag") tags: List<String>?,
    ): ResponseEntity<ByteArray> {
        val t = when (type?.lowercase()) { "company" -> ClientType.COMPANY; "person" -> ClientType.PERSON; else -> null }
        val (csv, n) = currentUser().let { u -> service.clientsCsv(u.currency, u.id, ClientFilter(q, t, tags.orEmpty())) }
        return file("clients.csv", csv, n)
    }

    @GetMapping("/api/export/deals.csv")
    fun deals(
        @RequestParam(required = false) scope: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) clientId: Long?,
        @RequestParam(required = false, name = "stage") stages: List<String>?,
    ): ResponseEntity<ByteArray> {
        val (csv, n) = currentUser().let { u -> service.dealsCsv(u.currency, u.id, scope, q, clientId, stages) }
        return file("deals.csv", csv, n)
    }

    private fun file(name: String, csv: String, rows: Int): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(MediaType("text", "csv", Charsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
            .header("X-Row-Count", rows.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(csv.toByteArray(Charsets.UTF_8))
}
