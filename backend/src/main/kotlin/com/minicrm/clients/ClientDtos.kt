package com.minicrm.clients

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class ClientRequest(
    @field:NotNull(message = "Choose a client type")
    val type: ClientType?,

    @field:NotBlank(message = "Name is required")
    @field:Size(max = 200, message = "Name must be at most 200 characters")
    val name: String?,

    @field:Size(max = 50, message = "Phone must be at most 50 characters")
    val phone: String? = null,

    @field:Pattern(regexp = "^$|^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}\\s*$", message = "Enter a valid email address")
    @field:Size(max = 254, message = "Email must be at most 254 characters")
    val email: String? = null,

    @field:Size(max = 300, message = "Address must be at most 300 characters")
    val address: String? = null,

    @field:Pattern(regexp = "^$|^\\s*" + "(?i)(https?://)?([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}(:\\d+)?(/\\S*)?" + "\\s*$", message = "Enter a valid website URL")
    @field:Size(max = 200, message = "Website must be at most 200 characters")
    val website: String? = null,

    @field:Size(max = 5000, message = "Notes must be at most 5000 characters")
    val notes: String? = null,

    @field:Size(max = 20, message = "A client can have at most 20 tags")
    val tags: List<@NotBlank(message = "Tag can’t be empty") @Size(max = 40, message = "Tag must be at most 40 characters") String> = emptyList(),
)

data class ContactRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 200, message = "Name must be at most 200 characters")
    val name: String?,

    @field:Size(max = 50, message = "Phone must be at most 50 characters")
    val phone: String? = null,

    @field:Pattern(regexp = "^$|^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}\\s*$", message = "Enter a valid email address")
    @field:Size(max = 254, message = "Email must be at most 254 characters")
    val email: String? = null,
)

data class ContactDto(val id: Long, val name: String, val phone: String?, val email: String?)

/** Рядок списку клієнтів: дані + зведення для таблиці/карток. */
data class ClientSummaryDto(
    val id: Long,
    val type: ClientType,
    val name: String,
    val phone: String?,
    val email: String?,
    val tags: List<String>,
    val contactCount: Int,
    val openDealCount: Int,
    val openDealValue: Long,
    /** Найближче невиконане завдання клієнта (зокрема його угод). */
    val nextTaskTitle: String?,
    val nextTaskDue: LocalDate?,
    val lastInteraction: LocalDate?,
    val createdAt: Instant,
)

data class ClientDto(
    val id: Long,
    val type: ClientType,
    val name: String,
    val phone: String?,
    val email: String?,
    val address: String?,
    val website: String?,
    val notes: String?,
    val tags: List<String>,
    val contacts: List<ContactDto>,
    val createdAt: Instant,
)

/** Що саме буде видалено разом із клієнтом (для діалогу підтвердження). */
data class ClientDeletePreview(
    val contacts: Int,
    val deals: Int,
    val archivedDeals: Int,
    val interactions: Int,
    val tasks: Int,
)
