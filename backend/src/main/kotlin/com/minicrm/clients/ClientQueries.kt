package com.minicrm.clients

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime

data class ClientFilter(val q: String? = null, val type: ClientType? = null, val tags: List<String> = emptyList())

/** Read-model запити списку клієнтів (агрегати по угодах/завданнях/взаємодіях одним SQL). */
@Repository
class ClientQueries(private val jdbc: JdbcClient) {

    fun list(ownerId: Long, filter: ClientFilter): List<ClientSummaryDto> {
        val where = StringBuilder("c.owner_id = :owner")
        val params = mutableMapOf<String, Any>("owner" to ownerId)
        filter.type?.let { where.append(" AND c.type = :type"); params["type"] = it.name }
        if (filter.tags.isNotEmpty()) {
            where.append(" AND EXISTS (SELECT 1 FROM client_tag ct JOIN tag t ON t.id = ct.tag_id WHERE ct.client_id = c.id AND lower(t.name) IN (:tags))")
            params["tags"] = filter.tags.map { it.lowercase() }
        }
        val q = filter.q?.trim()?.lowercase().orEmpty()
        if (q.isNotEmpty()) {
            val digits = q.filter(Char::isDigit)
            where.append(" AND (strpos(lower(c.name), :q) > 0 OR strpos(lower(coalesce(c.email, '')), :q) > 0 OR strpos(lower(coalesce(c.phone, '')), :q) > 0")
            if (digits.length >= 3) {
                where.append(" OR strpos(regexp_replace(coalesce(c.phone, ''), '\\D', '', 'g'), :qd) > 0")
                params["qd"] = digits
            }
            where.append(")")
            params["q"] = q
        }
        val sql = """
            SELECT c.id, c.type, c.name, c.phone, c.email, c.created_at,
                   (SELECT count(*) FROM contact x WHERE x.client_id = c.id) AS contact_count,
                   (SELECT count(*) FROM deal d WHERE d.client_id = c.id AND NOT d.archived AND d.stage NOT IN ('WON', 'LOST')) AS open_deal_count,
                   (SELECT coalesce(sum(d.amount), 0) FROM deal d WHERE d.client_id = c.id AND NOT d.archived AND d.stage NOT IN ('WON', 'LOST')) AS open_deal_value,
                   (SELECT max(i.occurred_on) FROM interaction i WHERE i.client_id = c.id) AS last_interaction,
                   (SELECT string_agg(t.name, chr(31) ORDER BY lower(t.name)) FROM client_tag ct JOIN tag t ON t.id = ct.tag_id WHERE ct.client_id = c.id) AS tag_names,
                   nt.title AS next_task_title, nt.due_date AS next_task_due
            FROM client c
            LEFT JOIN LATERAL (
                SELECT t.title, t.due_date FROM task t WHERE t.client_id = c.id AND NOT t.done ORDER BY t.due_date, t.id LIMIT 1
            ) nt ON TRUE
            WHERE $where
            ORDER BY lower(c.name), c.id
        """.trimIndent()
        return jdbc.sql(sql).params(params).query { rs, _ -> rs.toSummary() }.list()
    }

    fun tagNames(clientId: Long): List<String> =
        jdbc.sql("SELECT t.name FROM client_tag ct JOIN tag t ON t.id = ct.tag_id WHERE ct.client_id = :id ORDER BY lower(t.name)")
            .param("id", clientId).query { rs, _ -> rs.getString(1) }.list()

    fun deletePreview(ownerId: Long, clientId: Long): ClientDeletePreview =
        jdbc.sql(
            """
            SELECT (SELECT count(*) FROM contact WHERE client_id = :id AND owner_id = :owner) AS contacts,
                   (SELECT count(*) FROM deal WHERE client_id = :id AND owner_id = :owner AND NOT archived) AS deals,
                   (SELECT count(*) FROM deal WHERE client_id = :id AND owner_id = :owner AND archived) AS archived_deals,
                   (SELECT count(*) FROM interaction WHERE client_id = :id AND owner_id = :owner) AS interactions,
                   (SELECT count(*) FROM task WHERE client_id = :id AND owner_id = :owner) AS tasks
            """.trimIndent(),
        ).param("id", clientId).param("owner", ownerId).query { rs, _ ->
            ClientDeletePreview(rs.getInt("contacts"), rs.getInt("deals"), rs.getInt("archived_deals"), rs.getInt("interactions"), rs.getInt("tasks"))
        }.single()

    private fun ResultSet.toSummary() = ClientSummaryDto(
        id = getLong("id"),
        type = ClientType.valueOf(getString("type")),
        name = getString("name"),
        phone = getString("phone"),
        email = getString("email"),
        tags = getString("tag_names")?.split('\u001f') ?: emptyList(),
        contactCount = getInt("contact_count"),
        openDealCount = getInt("open_deal_count"),
        openDealValue = getLong("open_deal_value"),
        nextTaskTitle = getString("next_task_title"),
        nextTaskDue = getObject("next_task_due", LocalDate::class.java),
        lastInteraction = getObject("last_interaction", LocalDate::class.java),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
    )
}
