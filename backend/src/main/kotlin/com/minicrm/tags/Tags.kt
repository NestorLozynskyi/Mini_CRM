package com.minicrm.tags

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.ListCrudRepository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import com.minicrm.common.web.currentUser

@Table("tag")
data class Tag(@Id val id: Long? = null, val ownerId: Long, val name: String, val color: String)

data class TagDto(val name: String, val color: String)

interface TagRepository : ListCrudRepository<Tag, Long> {
    fun findByOwnerIdOrderByName(ownerId: Long): List<Tag>

    @Query("SELECT * FROM tag WHERE owner_id = :ownerId AND lower(name) = lower(:name)")
    fun findByOwnerAndName(ownerId: Long, name: String): Tag?

    /** Теги без жодного клієнта зникають (вони існують лише як мітки клієнтів). */
    @Modifying
    @Query("DELETE FROM tag t WHERE t.owner_id = :ownerId AND NOT EXISTS (SELECT 1 FROM client_tag ct WHERE ct.tag_id = t.id)")
    fun deleteOrphans(ownerId: Long): Int
}

@Service
class TagService(private val tags: TagRepository) {

    /** Повертає id тегів за назвами, створюючи відсутні (регістр назви — як у першого використання). */
    fun resolve(ownerId: Long, names: Collection<String>): List<Long> {
        val existing = tags.findByOwnerIdOrderByName(ownerId)
        val byLower = existing.associateBy { it.name.lowercase() }.toMutableMap()
        return names.map { raw ->
            val name = raw.trim()
            byLower[name.lowercase()]?.id ?: tags.save(Tag(ownerId = ownerId, name = name, color = NEW_TAG_COLOR))
                .also { byLower[name.lowercase()] = it }.id!!
        }.distinct()
    }

    fun list(ownerId: Long): List<TagDto> = tags.findByOwnerIdOrderByName(ownerId).map { TagDto(it.name, it.color) }

    fun deleteOrphans(ownerId: Long) = tags.deleteOrphans(ownerId)

    companion object {
        /** Колір нового тегу — як у макеті clients.html (нейтральний сірий дизайн-системи). */
        const val NEW_TAG_COLOR = "#667085"
    }
}

@RestController
class TagController(private val service: TagService) {
    @GetMapping("/api/tags")
    fun list(): List<TagDto> = service.list(currentUser().id)
}
