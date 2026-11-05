package com.minicrm.clients

import com.minicrm.common.error.NotFoundException
import com.minicrm.common.web.clean
import com.minicrm.tags.TagService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.temporal.ChronoUnit

@Service
class ClientService(
    private val clients: ClientRepository,
    private val contacts: ContactRepository,
    private val queries: ClientQueries,
    private val tags: TagService,
    private val clock: Clock,
) {
    private fun now() = clock.instant().truncatedTo(ChronoUnit.MICROS)

    @Transactional(readOnly = true)
    fun list(ownerId: Long, filter: ClientFilter) = queries.list(ownerId, filter)

    @Transactional(readOnly = true)
    fun get(ownerId: Long, id: Long): ClientDto = load(ownerId, id).toDto()

    /** Клієнт, що належить власнику, або 404 (чужі записи невідрізнювані від неіснуючих). */
    fun load(ownerId: Long, id: Long): Client = clients.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("Client")

    @Transactional
    fun create(ownerId: Long, req: ClientRequest): ClientDto {
        val now = now()
        val saved = clients.save(
            Client(
                ownerId = ownerId, type = req.type!!, name = req.name!!.trim(), phone = req.phone.clean(), email = req.email.clean(),
                address = req.address.clean(), website = req.website.clean(), notes = req.notes.clean(),
                createdAt = now, updatedAt = now, tags = tagRefs(ownerId, req.tags),
            ),
        )
        return saved.toDto()
    }

    @Transactional
    fun update(ownerId: Long, id: Long, req: ClientRequest): ClientDto {
        val current = load(ownerId, id)
        val saved = clients.save(
            current.copy(
                type = req.type!!, name = req.name!!.trim(), phone = req.phone.clean(), email = req.email.clean(),
                address = req.address.clean(), website = req.website.clean(), notes = req.notes.clean(),
                updatedAt = now(), tags = tagRefs(ownerId, req.tags),
            ),
        )
        tags.deleteOrphans(ownerId)
        return saved.toDto()
    }

    @Transactional(readOnly = true)
    fun deletePreview(ownerId: Long, id: Long): ClientDeletePreview {
        load(ownerId, id)
        return queries.deletePreview(ownerId, id)
    }

    /**
     * Каскадне видалення в одній транзакції: контактні особи, угоди (зокрема архівні), взаємодії й завдання
     * видаляються зовнішніми ключами ON DELETE CASCADE разом із клієнтом.
     */
    @Transactional
    fun delete(ownerId: Long, id: Long): ClientDeletePreview {
        val client = load(ownerId, id)
        val removed = queries.deletePreview(ownerId, id)
        clients.delete(client)
        tags.deleteOrphans(ownerId)
        return removed
    }

    // ---- contacts

    @Transactional
    fun addContact(ownerId: Long, clientId: Long, req: ContactRequest): ContactDto {
        load(ownerId, clientId)
        return contacts.save(
            Contact(ownerId = ownerId, clientId = clientId, name = req.name!!.trim(), phone = req.phone.clean(), email = req.email.clean(), createdAt = now()),
        ).toDto()
    }

    @Transactional
    fun updateContact(ownerId: Long, contactId: Long, req: ContactRequest): ContactDto {
        val c = contacts.findByIdAndOwnerId(contactId, ownerId) ?: throw NotFoundException("Contact")
        return contacts.save(c.copy(name = req.name!!.trim(), phone = req.phone.clean(), email = req.email.clean())).toDto()
    }

    @Transactional
    fun deleteContact(ownerId: Long, contactId: Long) {
        val c = contacts.findByIdAndOwnerId(contactId, ownerId) ?: throw NotFoundException("Contact")
        contacts.delete(c)
    }

    private fun tagRefs(ownerId: Long, names: List<String>): Set<ClientTagRef> =
        tags.resolve(ownerId, names.map { it.trim() }.filter { it.isNotEmpty() }).map { ClientTagRef(it) }.toSet()

    private fun Client.toDto() = ClientDto(
        id = id!!, type = type, name = name, phone = phone, email = email, address = address, website = website, notes = notes,
        tags = queries.tagNames(id),
        contacts = contacts.findByClientIdAndOwnerIdOrderByCreatedAtAscIdAsc(id, ownerId).map { it.toDto() },
        createdAt = createdAt,
    )
}

fun Contact.toDto() = ContactDto(id!!, name, phone, email)
