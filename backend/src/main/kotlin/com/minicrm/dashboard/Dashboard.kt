package com.minicrm.dashboard

import com.minicrm.common.web.UserClock
import com.minicrm.common.web.currentUser
import com.minicrm.deals.DealStage
import com.minicrm.interactions.InteractionDto
import com.minicrm.interactions.InteractionQueries
import com.minicrm.tasks.TaskDto
import com.minicrm.tasks.TaskQueries
import com.minicrm.tasks.TaskSummary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class StageStat(val stage: DealStage, val count: Int, val amount: Long)

data class DashboardDto(
    val today: LocalDate,
    /** Активні = не Won, не Lost, не архівні. */
    val activeDeals: Int,
    val activeValue: Long,
    /** Неархівні угоди (дошка) — розподіл за всіма етапами. */
    val boardDeals: Int,
    val wonValue: Long,
    val stages: List<StageStat>,
    val tasks: TaskSummary,
    /** Завдання з дедлайном сьогодні (активні й виконані). */
    val todayTasks: List<TaskDto>,
    val recentInteractions: List<InteractionDto>,
    /** Новий акаунт без жодних даних → екран привітання. */
    val empty: Boolean,
)

@Service
class DashboardService(
    private val jdbc: JdbcClient,
    private val tasks: TaskQueries,
    private val interactions: InteractionQueries,
    private val userClock: UserClock,
) {
    @Transactional(readOnly = true)
    fun get(ownerId: Long): DashboardDto {
        val today = userClock.today()
        val rows = jdbc.sql("SELECT stage, count(*) AS n, coalesce(sum(amount), 0) AS total FROM deal WHERE owner_id = :owner AND NOT archived GROUP BY stage")
            .param("owner", ownerId)
            .query { rs, _ -> StageStat(DealStage.valueOf(rs.getString("stage")), rs.getInt("n"), rs.getLong("total")) }
            .list().associateBy { it.stage }
        val stages = DealStage.entries.map { rows[it] ?: StageStat(it, 0, 0) }
        val open = stages.filter { it.stage.isOpen }
        val anyData = jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM client WHERE owner_id = :o) OR EXISTS (SELECT 1 FROM task WHERE owner_id = :o) OR EXISTS (SELECT 1 FROM deal WHERE owner_id = :o)",
        ).param("o", ownerId).query(Boolean::class.java).single()
        return DashboardDto(
            today = today,
            activeDeals = open.sumOf { it.count },
            activeValue = open.sumOf { it.amount },
            boardDeals = stages.sumOf { it.count },
            wonValue = stages.first { it.stage == DealStage.WON }.amount,
            stages = stages,
            tasks = tasks.summary(ownerId, today),
            todayTasks = tasks.list(ownerId, null, null, dueOn = today),
            recentInteractions = interactions.list(ownerId, null, null, 4),
            empty = !anyData,
        )
    }
}

@RestController
class DashboardController(private val service: DashboardService) {
    @GetMapping("/api/dashboard")
    fun get() = service.get(currentUser().id)
}
