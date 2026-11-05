package com.minicrm.clients

import com.minicrm.common.web.currentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class ClientController(private val service: ClientService) {

    @GetMapping("/api/clients")
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false, name = "tag") tags: List<String>?,
    ): List<ClientSummaryDto> = service.list(currentUser().id, ClientFilter(q, parseType(type), tags.orEmpty()))

    @PostMapping("/api/clients")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody body: ClientRequest) = service.create(currentUser().id, body)

    @GetMapping("/api/clients/{id}")
    fun get(@PathVariable id: Long) = service.get(currentUser().id, id)

    @PutMapping("/api/clients/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody body: ClientRequest) = service.update(currentUser().id, id, body)

    @GetMapping("/api/clients/{id}/delete-preview")
    fun deletePreview(@PathVariable id: Long) = service.deletePreview(currentUser().id, id)

    @DeleteMapping("/api/clients/{id}")
    fun delete(@PathVariable id: Long) = service.delete(currentUser().id, id)

    @PostMapping("/api/clients/{id}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    fun addContact(@PathVariable id: Long, @Valid @RequestBody body: ContactRequest) = service.addContact(currentUser().id, id, body)

    @PutMapping("/api/contacts/{id}")
    fun updateContact(@PathVariable id: Long, @Valid @RequestBody body: ContactRequest) = service.updateContact(currentUser().id, id, body)

    @DeleteMapping("/api/contacts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContact(@PathVariable id: Long) = service.deleteContact(currentUser().id, id)

    private fun parseType(raw: String?): ClientType? = when (raw?.lowercase()) {
        "company" -> ClientType.COMPANY
        "person" -> ClientType.PERSON
        else -> null
    }
}
