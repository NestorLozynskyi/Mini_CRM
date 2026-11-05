package com.minicrm.tasks

import com.minicrm.common.error.NotFoundException
import com.minicrm.common.web.LinkResolver
import com.minicrm.common.web.UserClock
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
import org.springframework.web.bind.annotation.PatchMapping
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

@Table("task")
data class Task(
    @Id val id: Long? = null,
    val ownerId: Long,
    val clientId: Long,
    val dealId: Long?,
    val title: String,
    val dueDate: LocalDate,
    val done: Boolean,
    val completedAt: Instant?,
    val createdAt: Instant,
)

interface TaskRepository : ListCrudRepository<Task, Long> {
    fun findByIdAndOwnerId(id: Long, ownerId: Long): Task?
}

data class TaskRequest(
    @field:NotBlank(message = "Enter a task title.")
    @field:Size(max = 200, message = "Title must be at most 200 characters")
    val title: String?,

    @field:NotNull(message = "Choose a due date.")
    val due: LocalDate?,

    val clientId: Long? = null,
    val dealId: Long? = null,
)

data class TaskDoneRequest(@field:NotNull val done: Boolean?)

data class TaskDto(
    val id: Long,
    val title: String,
    val due: LocalDate,
    val done: Boolean,
    val completedAt: Instant?,
    val clientId: Long,
    val clientName: String,
    val clientType: String,
    val dealId: Long?,
    val dealTitle: String?,
)

/** Лічильники для навігації та дашборда; «сьогодні» — у поясі браузера (X-Time-Zone). */
data class TaskSummary(val dueCount: Int, val todayOpen: Int, val todayDone: Int, val overdue: Int)

@Repository
class TaskQueries(private val jdbc: JdbcClient) {
    private val select = """
        SELECT t.*, c.name AS client_name, c.type AS client_type, d.title AS deal_title
        FROM task t JOIN client c ON c.id = t.client_id LEFT JOIN deal d ON d.id = t.deal_id
    """.trimIndent()

    fun list(ownerId: Long, clientId: Long?, dealId: Long?, dueOn: LocalDate? = null): List<TaskDto> {
        val where = StringBuilder(" WHERE t.owner_id = :owner")
        val params = mutableMapOf<String, Any>("owner" to ownerId)
        clientId?.let { where.append(" AND t.client_id = :client"); params["client"] = it }
        dealId?.let { where.append(" AND t.deal_id = :deal"); params["deal"] = it }
        dueOn?.let { where.append(" AND t.due_date = :due"); params["due"] = it }
        return jdbc.sql("$select$where ORDER BY t.done, t.due_date, t.id").params(params).query { rs, _ -> rs.toDto() }.list()
    }

    fun get(ownerId: Long, id: Long): TaskDto? =
        jdbc.sql("$select WHERE t.owner_id = :owner AND t.id = :id").param("owner", ownerId).param("id", id)
            .query { rs, _ -> rs.toDto() }.optional().orElse(null)

    fun summary(ownerId: Long, today: LocalDate): TaskSummary =
        jdbc.sql(
            """
            SELECT count(*) FILTER (WHERE NOT done AND due_date <= :today) AS due_count,
                   count(*) FILTER (WHERE NOT done AND due_date = :today)  AS today_open,
                   count(*) FILTER (WHERE done AND due_date = :today)      AS today_done,
                   count(*) FILTER (WHERE NOT done AND due_date < :today)  AS overdue
            FROM task WHERE owner_id = :owner
            """.trimIndent(),
        ).param("owner", ownerId).param("today", today).query { rs, _ ->
            TaskSummary(rs.getInt("due_count"), rs.getInt("today_open"), rs.getInt("today_done"), rs.getInt("overdue"))
        }.single()

    private fun ResultSet.toDto() = TaskDto(
        id = getLong("id"),
        title = getString("title"),
        due = getObject("due_date", LocalDate::class.java),
        done = getBoolean("done"),
        completedAt = getObject("completed_at", OffsetDateTime::class.java)?.toInstant(),
        clientId = getLong("client_id"),
        clientName = getString("client_name"),
        clientType = getString("client_type").lowercase(),
        dealId = getObject("deal_id") as Long?,
        dealTitle = getString("deal_title"),
    )
}

@Service
class TaskService(
    private val repo: TaskRepository,
    private val queries: TaskQueries,
    private val links: LinkResolver,
    private val userClock: UserClock,
    private val clock: Clock,
) {
    private fun now() = clock.instant().truncatedTo(ChronoUnit.MICROS)

    @Transactional(readOnly = true)
    fun list(ownerId: Long, clientId: Long?, dealId: Long?) = queries.list(ownerId, clientId, dealId)

    @Transactional(readOnly = true)
    fun summary(ownerId: Long) = queries.summary(ownerId, userClock.today())

    @Transactional
    fun create(ownerId: Long, req: TaskRequest): TaskDto {
        val link = links.resolve(ownerId, req.clientId, req.dealId, "Link the task to a client or deal.")
        val saved = repo.save(
            Task(ownerId = ownerId, clientId = link.clientId, dealId = link.dealId, title = req.title!!.trim(), dueDate = req.due!!, done = false, completedAt = null, createdAt = now()),
        )
        return queries.get(ownerId, saved.id!!)!!
    }

    @Transactional
    fun update(ownerId: Long, id: Long, req: TaskRequest): TaskDto {
        val current = repo.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Task")
        val link = links.resolve(ownerId, req.clientId, req.dealId, "Link the task to a client or deal.")
        repo.save(current.copy(clientId = link.clientId, dealId = link.dealId, title = req.title!!.trim(), dueDate = req.due!!))
        return queries.get(ownerId, id)!!
    }

    @Transactional
    fun setDone(ownerId: Long, id: Long, done: Boolean): TaskDto {
        val current = repo.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Task")
        if (current.done != done) repo.save(current.copy(done = done, completedAt = if (done) now() else null))
        return queries.get(ownerId, id)!!
    }

    @Transactional
    fun delete(ownerId: Long, id: Long) {
        repo.delete(repo.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Task"))
    }
}

@RestController
class TaskController(private val service: TaskService) {
    @GetMapping("/api/tasks")
    fun list(@RequestParam(required = false) clientId: Long?, @RequestParam(required = false) dealId: Long?) =
        service.list(currentUser().id, clientId, dealId)

    @GetMapping("/api/tasks/summary")
    fun summary() = service.summary(currentUser().id)

    @PostMapping("/api/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody body: TaskRequest) = service.create(currentUser().id, body)

    @PutMapping("/api/tasks/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody body: TaskRequest) = service.update(currentUser().id, id, body)

    @PatchMapping("/api/tasks/{id}/done")
    fun setDone(@PathVariable id: Long, @Valid @RequestBody body: TaskDoneRequest) = service.setDone(currentUser().id, id, body.done!!)

    @DeleteMapping("/api/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(currentUser().id, id)
}
