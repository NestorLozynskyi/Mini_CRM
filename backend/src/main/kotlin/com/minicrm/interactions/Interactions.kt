package com.minicrm.interactions

import com.fasterxml.jackson.annotation.JsonProperty
import com.minicrm.common.error.NotFoundException
import com.minicrm.common.web.LinkResolver
import com.minicrm.common.web.currentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.ListCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

enum class InteractionType {
    @JsonProperty("call") CALL,
    @JsonProperty("meeting") MEETING,
    @JsonProperty("email") EMAIL,
    @JsonProperty("note") NOTE,
}

@Table("interaction")
data class Interaction(
    @Id val id: Long? = null,
    val ownerId: Long,
    val clientId: Long,
    val dealId: Long?,
    val type: InteractionType,
    val occurredOn: LocalDate,
    val description: String,
    val createdAt: Instant,
)

interface InteractionRepository : ListCrudRepository<Interaction, Long> {
    fun findByIdAndOwnerId(id: Long, ownerId: Long): Interaction?
}

data class InteractionRequest(
    @field:NotNull(message = "Choose an interaction type")
    val type: InteractionType?,

    @field:NotNull(message = "Choose a date")
    val date: LocalDate?,

    @field:NotBlank(message = "Description is required")
    @field:Size(max = 5000, message = "Description must be at most 5000 characters")
    val description: String?,

    val clientId: Long? = null,
    val dealId: Long? = null,
)

data class InteractionDto(
    val id: Long,
    val type: InteractionType,
    val date: LocalDate,
    val description: String,
    val clientId: Long,
    val clientName: String,
    val clientType: String,
    val dealId: Long?,
    val dealTitle: String?,
    val createdAt: Instant,
)

@Repository
class InteractionQueries(private val jdbc: JdbcClient) {
    private val select = """
        SELECT i.*, c.name AS client_name, c.type AS client_type, d.title AS deal_title
        FROM interaction i JOIN client c ON c.id = i.client_id LEFT JOIN deal d ON d.id = i.deal_id
    """.trimIndent()
    private val order = " ORDER BY i.occurred_on DESC, i.created_at DESC, i.id DESC"

    fun list(ownerId: Long, clientId: Long?, dealId: Long?, limit: Int?): List<InteractionDto> {
        val where = StringBuilder(" WHERE i.owner_id = :owner")
        val params = mutableMapOf<String, Any>("owner" to ownerId)
        clientId?.let { where.append(" AND i.client_id = :client"); params["client"] = it }
        dealId?.let { where.append(" AND i.deal_id = :deal"); params["deal"] = it }
        val lim = limit?.coerceIn(1, 200)?.let { " LIMIT $it" } ?: ""
        return jdbc.sql(select + where + order + lim).params(params).query { rs, _ -> rs.toDto() }.list()
    }

    fun get(ownerId: Long, id: Long): InteractionDto? =
        jdbc.sql("$select WHERE i.owner_id = :owner AND i.id = :id").param("owner", ownerId).param("id", id)
            .query { rs, _ -> rs.toDto() }.optional().orElse(null)

    private fun ResultSet.toDto() = InteractionDto(
        id = getLong("id"),
        type = InteractionType.valueOf(getString("type")),
        date = getObject("occurred_on", LocalDate::class.java),
        description = getString("description"),
        clientId = getLong("client_id"),
        clientName = getString("client_name"),
        clientType = getString("client_type").lowercase(),
        dealId = getObject("deal_id") as Long?,
        dealTitle = getString("deal_title"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
    )
}

@Service
class InteractionService(
    private val repo: InteractionRepository,
    private val queries: InteractionQueries,
    private val links: LinkResolver,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(ownerId: Long, clientId: Long?, dealId: Long?, limit: Int?) = queries.list(ownerId, clientId, dealId, limit)

    @Transactional
    fun create(ownerId: Long, req: InteractionRequest): InteractionDto {
        val link = links.resolve(ownerId, req.clientId, req.dealId, "Link it to a client or deal.")
        val saved = repo.save(
            Interaction(
                ownerId = ownerId, clientId = link.clientId, dealId = link.dealId, type = req.type!!, occurredOn = req.date!!,
                description = req.description!!.trim(), createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
            ),
        )
        return queries.get(ownerId, saved.id!!)!!
    }

    @Transactional
    fun update(ownerId: Long, id: Long, req: InteractionRequest): InteractionDto {
        val current = repo.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Interaction")
        val link = links.resolve(ownerId, req.clientId, req.dealId, "Link it to a client or deal.")
        repo.save(current.copy(clientId = link.clientId, dealId = link.dealId, type = req.type!!, occurredOn = req.date!!, description = req.description!!.trim()))
        return queries.get(ownerId, id)!!
    }

    @Transactional
    fun delete(ownerId: Long, id: Long) {
        repo.delete(repo.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Interaction"))
    }
}

@RestController
class InteractionController(private val service: InteractionService) {
    @GetMapping("/api/interactions")
    fun list(
        @RequestParam(required = false) clientId: Long?,
        @RequestParam(required = false) dealId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = service.list(currentUser().id, clientId, dealId, limit)

    @PostMapping("/api/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody body: InteractionRequest) = service.create(currentUser().id, body)

    @PutMapping("/api/interactions/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody body: InteractionRequest) = service.update(currentUser().id, id, body)

    @DeleteMapping("/api/interactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(currentUser().id, id)
}
