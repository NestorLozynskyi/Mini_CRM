package com.minicrm.deals

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime

@Repository
class DealQueries(private val jdbc: JdbcClient) {

    fun list(ownerId: Long, f: DealFilter): List<DealDto> {
        val where = StringBuilder("d.owner_id = :owner")
        val params = mutableMapOf<String, Any>("owner" to ownerId)
        when (f.scope) {
            DealScope.ACTIVE -> where.append(" AND NOT d.archived")
            DealScope.ARCHIVED -> where.append(" AND d.archived")
            DealScope.ALL -> {}
        }
        f.clientId?.let { where.append(" AND d.client_id = :client"); params["client"] = it }
        if (f.stages.isNotEmpty()) { where.append(" AND d.stage IN (:stages)"); params["stages"] = f.stages.map { it.name } }
        val q = f.q?.trim()?.lowercase().orEmpty()
        if (q.isNotEmpty()) { where.append(" AND (strpos(lower(d.title), :q) > 0 OR strpos(lower(c.name), :q) > 0)"); params["q"] = q }
        return jdbc.sql("$SELECT WHERE $where ORDER BY d.stage_changed_at DESC, d.id DESC").params(params).query { rs, _ -> rs.toDto() }.list()
    }

    fun get(ownerId: Long, id: Long): DealDto? =
        jdbc.sql("$SELECT WHERE d.owner_id = :owner AND d.id = :id").param("owner", ownerId).param("id", id).query { rs, _ -> rs.toDto() }.optional().orElse(null)

    private fun ResultSet.toDto() = DealDto(
        id = getLong("id"),
        title = getString("title"),
        clientId = getLong("client_id"),
        clientName = getString("client_name"),
        clientType = getString("client_type").lowercase(),
        amount = getLong("amount"),
        stage = DealStage.valueOf(getString("stage")),
        expectedClose = getObject("expected_close", LocalDate::class.java),
        lostReason = getString("lost_reason"),
        archived = getBoolean("archived"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java).toInstant(),
        stageChangedAt = getObject("stage_changed_at", OffsetDateTime::class.java).toInstant(),
        lastActivity = getObject("last_activity", LocalDate::class.java),
    )

    companion object {
        private val SELECT = """
            SELECT d.*, c.name AS client_name, c.type AS client_type,
                   (SELECT max(i.occurred_on) FROM interaction i WHERE i.deal_id = d.id) AS last_activity
            FROM deal d JOIN client c ON c.id = d.client_id
        """.trimIndent()
    }
}
