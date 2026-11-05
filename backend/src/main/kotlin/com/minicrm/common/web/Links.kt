package com.minicrm.common.web

import com.minicrm.clients.ClientService
import com.minicrm.common.error.FieldValidationException
import com.minicrm.deals.DealService
import org.springframework.stereotype.Component

/** Прив'язка взаємодії/завдання: рівно одне з clientId / dealId; для угоди клієнт = клієнт угоди. */
data class ResolvedLink(val clientId: Long, val dealId: Long?)

@Component
class LinkResolver(private val clients: ClientService, private val deals: DealService) {
    fun resolve(ownerId: Long, clientId: Long?, dealId: Long?, message: String): ResolvedLink {
        if ((clientId == null) == (dealId == null)) throw FieldValidationException("link", message)
        return if (dealId != null) {
            val deal = deals.load(ownerId, dealId)
            ResolvedLink(deal.clientId, deal.id)
        } else {
            ResolvedLink(clients.load(ownerId, clientId!!).id!!, null)
        }
    }
}
