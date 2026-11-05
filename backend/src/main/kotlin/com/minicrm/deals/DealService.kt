package com.minicrm.deals

import com.minicrm.clients.ClientService
import com.minicrm.common.error.NotFoundException
import com.minicrm.common.web.clean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.temporal.ChronoUnit

@Service
class DealService(
    private val deals: DealRepository,
    private val queries: DealQueries,
    private val clients: ClientService,
    private val clock: Clock,
) {
    private fun now() = clock.instant().truncatedTo(ChronoUnit.MICROS)

    fun load(ownerId: Long, id: Long): Deal = deals.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Deal")

    @Transactional(readOnly = true)
    fun list(ownerId: Long, filter: DealFilter) = queries.list(ownerId, filter)

    @Transactional(readOnly = true)
    fun get(ownerId: Long, id: Long): DealDto = queries.get(ownerId, id) ?: throw NotFoundException("Deal")

    @Transactional
    fun create(ownerId: Long, req: DealRequest): DealDto {
        clients.load(ownerId, req.clientId!!) // клієнт має належати тому ж акаунту
        val now = now()
        val stage = req.stage!!
        val saved = deals.save(
            Deal(
                ownerId = ownerId, clientId = req.clientId, title = req.title!!.trim(), amount = req.amount!!, stage = stage,
                expectedClose = req.expectedClose, lostReason = lostReason(stage, req.lostReason), archived = false,
                createdAt = now, updatedAt = now, stageChangedAt = now,
            ),
        )
        return get(ownerId, saved.id!!)
    }

    @Transactional
    fun update(ownerId: Long, id: Long, req: DealRequest): DealDto {
        val current = load(ownerId, id)
        clients.load(ownerId, req.clientId!!)
        val now = now()
        val stage = req.stage!!
        deals.save(
            current.copy(
                clientId = req.clientId, title = req.title!!.trim(), amount = req.amount!!, stage = stage,
                expectedClose = req.expectedClose, lostReason = lostReason(stage, req.lostReason), updatedAt = now,
                stageChangedAt = if (stage != current.stage) now else current.stageChangedAt,
            ),
        )
        if (req.clientId != current.clientId) {
            deals.moveInteractions(id, req.clientId, ownerId)
            deals.moveTasks(id, req.clientId, ownerId)
        }
        return get(ownerId, id)
    }

    /** Зміна етапу (Drag & Drop на канбані або «Move to…»). Причина програшу — лише для Lost. */
    @Transactional
    fun changeStage(ownerId: Long, id: Long, req: StageChangeRequest): DealDto {
        val current = load(ownerId, id)
        val stage = req.stage!!
        val now = now()
        if (stage != current.stage || stage == DealStage.LOST) {
            deals.save(
                current.copy(
                    stage = stage, lostReason = lostReason(stage, req.lostReason), updatedAt = now,
                    stageChangedAt = if (stage != current.stage) now else current.stageChangedAt,
                ),
            )
        }
        return get(ownerId, id)
    }

    @Transactional
    fun setArchived(ownerId: Long, id: Long, archived: Boolean): DealDto {
        val current = load(ownerId, id)
        if (current.archived != archived) deals.save(current.copy(archived = archived, updatedAt = now()))
        return get(ownerId, id)
    }

    private fun lostReason(stage: DealStage, raw: String?) = if (stage == DealStage.LOST) raw.clean() else null
}
