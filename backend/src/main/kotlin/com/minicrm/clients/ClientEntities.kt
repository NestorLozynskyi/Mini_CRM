package com.minicrm.clients

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.ListCrudRepository
import java.time.Instant

enum class ClientType {
    @JsonProperty("company") COMPANY,
    @JsonProperty("person") PERSON,
}

@Table("client_tag")
data class ClientTagRef(val tagId: Long)

@Table("client")
data class Client(
    @Id val id: Long? = null,
    val ownerId: Long,
    val type: ClientType,
    val name: String,
    val phone: String?,
    val email: String?,
    val address: String?,
    val website: String?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    @MappedCollection(idColumn = "client_id")
    val tags: Set<ClientTagRef> = emptySet(),
)

@Table("contact")
data class Contact(
    @Id val id: Long? = null,
    val ownerId: Long,
    val clientId: Long,
    val name: String,
    val phone: String?,
    val email: String?,
    val createdAt: Instant,
)

interface ClientRepository : ListCrudRepository<Client, Long> {
    fun findByIdAndOwnerId(id: Long, ownerId: Long): Client?
    fun countByOwnerId(ownerId: Long): Long
}

interface ContactRepository : ListCrudRepository<Contact, Long> {
    fun findByIdAndOwnerId(id: Long, ownerId: Long): Contact?
    fun findByClientIdAndOwnerIdOrderByCreatedAtAscIdAsc(clientId: Long, ownerId: Long): List<Contact>
}
