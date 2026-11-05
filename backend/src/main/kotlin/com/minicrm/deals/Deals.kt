package com.minicrm.deals

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.ListCrudRepository
import java.time.Instant
import java.time.LocalDate

/** Фіксовані етапи воронки (порядок = порядок колонок канбану). */
enum class DealStage {
    @JsonProperty("lead") LEAD,
    @JsonProperty("contacted") CONTACTED,
    @JsonProperty("proposal") PROPOSAL,
    @JsonProperty("negotiation") NEGOTIATION,
    @JsonProperty("won") WON,
    @JsonProperty("lost") LOST;

    val isOpen get() = this != WON && this != LOST

    companion object {
        fun parse(raw: String?): DealStage? = entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

@Table("deal")
data class Deal(
    @Id val id: Long? = null,
    val ownerId: Long,
    val clientId: Long,
    val title: String,
    val amount: Long,
    val stage: DealStage,
    val expectedClose: LocalDate?,
    val lostReason: String?,
    val archived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val stageChangedAt: Instant,
)

interface DealRepository : ListCrudRepository<Deal, Long> {
    fun findByIdAndOwnerId(id: Long, ownerId: Long): Deal?

    /** Записи угоди завжди належать клієнту угоди — при зміні клієнта переносимо їх. */
    @Modifying
    @Query("UPDATE interaction SET client_id = :clientId WHERE deal_id = :dealId AND owner_id = :ownerId")
    fun moveInteractions(dealId: Long, clientId: Long, ownerId: Long): Int

    @Modifying
    @Query("UPDATE task SET client_id = :clientId WHERE deal_id = :dealId AND owner_id = :ownerId")
    fun moveTasks(dealId: Long, clientId: Long, ownerId: Long): Int
}

data class DealRequest(
    @field:NotBlank(message = "Enter a deal title.")
    @field:Size(max = 200, message = "Title must be at most 200 characters")
    val title: String?,

    @field:NotNull(message = "Select a client.")
    val clientId: Long?,

    @field:NotNull(message = "Enter an amount (use 0 if it’s not known yet).")
    @field:Min(value = 0, message = "Amount can’t be negative.")
    @field:Max(value = 999_999_999_999, message = "Amount is too large.")
    val amount: Long?,

    @field:NotNull(message = "Choose a stage.")
    val stage: DealStage?,

    val expectedClose: LocalDate? = null,

    @field:Size(max = 500, message = "Reason must be at most 500 characters")
    val lostReason: String? = null,
)

data class StageChangeRequest(
    @field:NotNull(message = "Choose a stage.")
    val stage: DealStage?,

    @field:Size(max = 500, message = "Reason must be at most 500 characters")
    val lostReason: String? = null,
)

data class DealDto(
    val id: Long,
    val title: String,
    val clientId: Long,
    val clientName: String,
    val clientType: String,
    val amount: Long,
    val stage: DealStage,
    val expectedClose: LocalDate?,
    val lostReason: String?,
    val archived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val stageChangedAt: Instant,
    /** Дата останньої взаємодії по угоді. */
    val lastActivity: LocalDate?,
)

enum class DealScope { ACTIVE, ARCHIVED, ALL }

data class DealFilter(
    val scope: DealScope = DealScope.ACTIVE,
    val q: String? = null,
    val clientId: Long? = null,
    val stages: List<DealStage> = emptyList(),
)
