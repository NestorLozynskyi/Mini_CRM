package com.minicrm.deals

import com.minicrm.common.web.currentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class DealController(private val service: DealService) {

    @GetMapping("/api/deals")
    fun list(
        @RequestParam(required = false) scope: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) clientId: Long?,
        @RequestParam(required = false, name = "stage") stages: List<String>?,
    ): List<DealDto> = service.list(currentUser().id, dealFilter(scope, q, clientId, stages))

    @PostMapping("/api/deals")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody body: DealRequest) = service.create(currentUser().id, body)

    @GetMapping("/api/deals/{id}")
    fun get(@PathVariable id: Long) = service.get(currentUser().id, id)

    @PutMapping("/api/deals/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody body: DealRequest) = service.update(currentUser().id, id, body)

    @PatchMapping("/api/deals/{id}/stage")
    fun changeStage(@PathVariable id: Long, @Valid @RequestBody body: StageChangeRequest) = service.changeStage(currentUser().id, id, body)

    @PostMapping("/api/deals/{id}/archive")
    fun archive(@PathVariable id: Long) = service.setArchived(currentUser().id, id, true)

    @PostMapping("/api/deals/{id}/restore")
    fun restore(@PathVariable id: Long) = service.setArchived(currentUser().id, id, false)
}

fun dealFilter(scope: String?, q: String?, clientId: Long?, stages: List<String>?) = DealFilter(
    scope = when (scope?.lowercase()) {
        "archived" -> DealScope.ARCHIVED
        "all" -> DealScope.ALL
        else -> DealScope.ACTIVE
    },
    q = q,
    clientId = clientId,
    stages = stages.orEmpty().mapNotNull { DealStage.parse(it) },
)
