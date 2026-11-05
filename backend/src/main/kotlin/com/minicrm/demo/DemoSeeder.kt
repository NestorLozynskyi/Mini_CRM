package com.minicrm.demo

import com.minicrm.clients.Client
import com.minicrm.clients.ClientRepository
import com.minicrm.clients.ClientTagRef
import com.minicrm.clients.ClientType
import com.minicrm.clients.Contact
import com.minicrm.clients.ContactRepository
import com.minicrm.deals.Deal
import com.minicrm.deals.DealRepository
import com.minicrm.deals.DealStage
import com.minicrm.interactions.Interaction
import com.minicrm.interactions.InteractionRepository
import com.minicrm.interactions.InteractionType
import com.minicrm.tags.Tag
import com.minicrm.tags.TagRepository
import com.minicrm.tasks.Task
import com.minicrm.tasks.TaskRepository
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Початкові дані демо-акаунта — реалістичний набір із макетів (assets/app.js): 12 клієнтів, 11 контактних осіб,
 * 17 угод на всіх етапах (2 архівні), 16 взаємодій, 14 завдань у Today / Upcoming / Overdue / виконаних.
 * Усі дати — зсуви відносно «сьогодні» користувача на момент запуску демо.
 */
@Component
class DemoSeeder(
    private val tags: TagRepository,
    private val clients: ClientRepository,
    private val contacts: ContactRepository,
    private val deals: DealRepository,
    private val interactions: InteractionRepository,
    private val tasks: TaskRepository,
) {
    private data class C(
        val key: String, val name: String, val type: ClientType, val phone: String, val email: String, val address: String,
        val website: String, val notes: String, val tags: List<String>, val createdDaysAgo: Long, val contacts: List<Triple<String, String, String>>,
    )

    private data class D(val key: String, val title: String, val client: String, val amount: Long, val stage: DealStage, val close: Long, val archived: Boolean = false, val lost: String? = null)
    private data class I(val type: InteractionType, val day: Long, val client: String, val deal: String?, val text: String)
    private data class T(val title: String, val day: Long, val client: String? = null, val deal: String? = null, val done: Boolean = false)

    fun seed(ownerId: Long, today: LocalDate, now: Instant) {
        val tagIds = TAGS.associate { (name, color) -> name to tags.save(Tag(ownerId = ownerId, name = name, color = color)).id!! }

        val clientIds = CLIENTS.associate { c ->
            val created = now.minus(Duration.ofDays(c.createdDaysAgo))
            val saved = clients.save(
                Client(
                    ownerId = ownerId, type = c.type, name = c.name, phone = c.phone.ifEmpty { null }, email = c.email.ifEmpty { null },
                    address = c.address.ifEmpty { null }, website = c.website.ifEmpty { null }, notes = c.notes.ifEmpty { null },
                    createdAt = created, updatedAt = created, tags = c.tags.map { ClientTagRef(tagIds.getValue(it)) }.toSet(),
                ),
            )
            c.contacts.forEachIndexed { i, (name, phone, email) ->
                contacts.save(Contact(ownerId = ownerId, clientId = saved.id!!, name = name, phone = phone, email = email, createdAt = created.plusSeconds(i.toLong())))
            }
            c.key to saved.id!!
        }

        // Порядок на дошці: угоди далі за списком — «свіжіші» в колонці (stage_changed_at спадає);
        // зміни розкидані по минулих днях, щоб колонка Updated виглядала реалістично.
        val dealIds = DEALS.withIndex().associate { (i, d) ->
            val changed = now.minus(Duration.ofDays((i + 1).toLong())).minus(Duration.ofMinutes((i * 37 % 300).toLong()))
            d.key to deals.save(
                Deal(
                    ownerId = ownerId, clientId = clientIds.getValue(d.client), title = d.title, amount = d.amount, stage = d.stage,
                    expectedClose = today.plusDays(d.close), lostReason = d.lost, archived = d.archived,
                    createdAt = changed, updatedAt = changed, stageChangedAt = changed,
                ),
            ).id!!
        }
        val dealClient = DEALS.associate { it.key to clientIds.getValue(it.client) }

        INTERACTIONS.forEachIndexed { i, it ->
            interactions.save(
                Interaction(
                    ownerId = ownerId, clientId = clientIds.getValue(it.client), dealId = it.deal?.let(dealIds::getValue), type = it.type,
                    occurredOn = today.plusDays(it.day), description = it.text, createdAt = now.minusSeconds(i.toLong()),
                ),
            )
        }

        TASKS.forEach { t ->
            val clientId = t.deal?.let(dealClient::getValue) ?: clientIds.getValue(t.client!!)
            tasks.save(
                Task(
                    ownerId = ownerId, clientId = clientId, dealId = t.deal?.let(dealIds::getValue), title = t.title, dueDate = today.plusDays(t.day),
                    done = t.done, completedAt = if (t.done) now.minus(Duration.ofDays(-t.day)) else null, createdAt = now,
                ),
            )
        }
    }

    private companion object {
        val TAGS = listOf("VIP" to "#DC6803", "Retainer" to "#079455", "Referral" to "#0086C9", "Design" to "#DD2590", "Web" to "#0E9384", "Local" to "#667085")

        val CO = ClientType.COMPANY
        val PE = ClientType.PERSON

        val CLIENTS = listOf(
            C("c1", "Northwind Studio", CO, "+1 (503) 555-0142", "hello@northwindstudio.co", "214 NW 11th Ave, Portland, OR 97209", "northwindstudio.co",
                "Boutique branding studio. Prefers async updates by email; monthly check-in call on the first Monday.", listOf("VIP", "Design"), 140,
                listOf(Triple("Olivia Hart", "+1 (503) 555-0187", "olivia@northwindstudio.co"), Triple("Sam Patel", "+1 (503) 555-0110", "sam@northwindstudio.co"))),
            C("c2", "Harbor & Pine Bakery", CO, "+1 (207) 555-0163", "orders@harborpinebakery.com", "38 Wharf St, Portland, ME 04101", "harborpinebakery.com",
                "Family bakery with two locations. Rosa handles all marketing decisions.", listOf("Local", "Retainer"), 210,
                listOf(Triple("Rosa Delgado", "+1 (207) 555-0129", "rosa@harborpinebakery.com"))),
            C("c3", "Maya Chen", PE, "+1 (415) 555-0176", "maya.chen@fastmail.com", "San Francisco, CA", "mayachen.design",
                "Freelance illustrator, referred by Sofia Lindqvist.", listOf("Referral", "Design"), 30, emptyList()),
            C("c4", "Brightline Dental", CO, "+1 (612) 555-0134", "office@brightlinedental.com", "1200 Hennepin Ave, Minneapolis, MN 55403", "brightlinedental.com",
                "Three-chair practice. Decisions go through Dr. Blake; Nina manages the front desk and scheduling.", listOf("Web", "Local"), 95,
                listOf(
                    Triple("Aaron Blake", "+1 (612) 555-0101", "a.blake@brightlinedental.com"),
                    Triple("Nina Ford", "+1 (612) 555-0148", "nina@brightlinedental.com"),
                    Triple("Leo Grant", "+1 (612) 555-0192", "leo@brightlinedental.com"),
                )),
            C("c5", "Tomás Rivera", PE, "+1 (512) 555-0158", "tomas.rivera@gmail.com", "Austin, TX", "", "Wedding photographer.", listOf("Web"), 60, emptyList()),
            C("c6", "Copperleaf Coffee Co.", CO, "+1 (303) 555-0117", "hi@copperleafcoffee.com", "2901 Larimer St, Denver, CO 80205", "copperleafcoffee.com",
                "Small-batch roaster with a café. Interested in selling beans online.", listOf("Local", "Referral"), 180,
                listOf(Triple("Jenna Marsh", "+1 (303) 555-0181", "jenna@copperleafcoffee.com"))),
            C("c7", "Elm Street Yoga", CO, "+1 (617) 555-0139", "studio@elmstreetyoga.com", "77 Elm St, Somerville, MA 02144", "elmstreetyoga.com",
                "Monthly website maintenance retainer since spring.", listOf("Retainer"), 240,
                listOf(Triple("Priya Nair", "+1 (617) 555-0124", "priya@elmstreetyoga.com"), Triple("Kate Olsen", "+1 (617) 555-0165", "kate@elmstreetyoga.com"))),
            C("c8", "Daniel Okafor", PE, "+1 (312) 555-0193", "daniel@okafor.me", "Chicago, IL", "okafor.me", "Keynote speaker and author. Fast decision maker.", listOf("VIP"), 21, emptyList()),
            C("c9", "Bluefin Logistics", CO, "+1 (206) 555-0105", "ops@bluefinlogistics.com", "4400 E Marginal Way S, Seattle, WA 98134", "bluefinlogistics.com",
                "Regional freight company, 40 dispatchers. Security review required before contract.", listOf("Web"), 45,
                listOf(Triple("Mark Rowe", "+1 (206) 555-0172", "m.rowe@bluefinlogistics.com"))),
            C("c10", "Sofia Lindqvist", PE, "+1 (718) 555-0146", "sofia@lindqvist.studio", "Brooklyn, NY", "lindqvist.studio",
                "Ceramicist. Happy client, has referred two people.", listOf("Design", "Referral"), 150, emptyList()),
            C("c11", "Greenway Landscaping", CO, "+1 (919) 555-0151", "info@greenwaylandscaping.com", "510 Glenwood Ave, Raleigh, NC 27603", "greenwaylandscaping.com",
                "Seasonal business; very busy until mid-November.", listOf("Local"), 75,
                listOf(Triple("Carlos Mendes", "+1 (919) 555-0188", "carlos@greenwaylandscaping.com"))),
            C("c12", "Hannah Brooks", PE, "+1 (602) 555-0119", "hannah.brooks@outlook.com", "", "", "", emptyList(), 12, emptyList()),
        )

        val DEALS = listOf(
            D("d1", "Brand identity refresh", "c1", 12400, DealStage.PROPOSAL, 18),
            D("d2", "Website redesign", "c4", 8600, DealStage.NEGOTIATION, 9),
            D("d3", "Seasonal menu photography", "c2", 1800, DealStage.WON, -6),
            D("d4", "Portfolio website", "c3", 3200, DealStage.CONTACTED, 21),
            D("d5", "Online shop setup", "c6", 6500, DealStage.LEAD, 40),
            D("d6", "Class booking web app", "c7", 9800, DealStage.PROPOSAL, 25),
            D("d7", "Logo & stationery", "c10", 2400, DealStage.WON, -12),
            D("d8", "Shipment tracking portal", "c9", 18500, DealStage.NEGOTIATION, 14),
            D("d9", "Spring campaign visuals", "c11", 2900, DealStage.LEAD, 30),
            D("d10", "Personal brand kit", "c8", 4200, DealStage.CONTACTED, -2),
            D("d11", "Landing page", "c5", 1500, DealStage.LOST, -3, lost = "Chose a cheaper template-based option."),
            D("d12", "Social media retainer", "c2", 3600, DealStage.LEAD, 55),
            D("d13", "Packaging design", "c6", 5400, DealStage.WON, -20),
            D("d14", "Patient portal UX audit", "c4", 4800, DealStage.CONTACTED, 16),
            D("d15", "Menu board redesign", "c2", 1200, DealStage.LOST, -64, archived = true, lost = "Postponed until next year."),
            D("d16", "Event microsite", "c12", 2200, DealStage.WON, -48, archived = true),
            D("d17", "Quarterly maintenance retainer", "c7", 4500, DealStage.NEGOTIATION, 23, archived = true),
        )

        val INTERACTIONS = listOf(
            I(InteractionType.CALL, -1, "c1", "d1", "Walked Olivia through the three logo directions. She prefers direction B and asked for a darker navy."),
            I(InteractionType.EMAIL, -2, "c4", "d2", "Sent the revised quote with phased delivery: homepage first, booking flow in phase two."),
            I(InteractionType.MEETING, -3, "c7", "d6", "On-site meeting at the studio. Mapped the class booking flow and membership tiers."),
            I(InteractionType.NOTE, -4, "c3", null, "Referred by Sofia Lindqvist. Wants a simple portfolio with 4–6 case studies."),
            I(InteractionType.CALL, -5, "c9", "d8", "Intro call with Mark about portal scope. Needs single sign-on for 40 dispatchers."),
            I(InteractionType.EMAIL, -6, "c2", "d3", "Delivered the final photo set and invoice. Rosa confirmed payment."),
            I(InteractionType.MEETING, -8, "c1", "d1", "Kickoff workshop: brand values, audience and competitor review."),
            I(InteractionType.NOTE, -9, "c5", "d11", "Tomás went with a template service. Keep in touch for future work."),
            I(InteractionType.CALL, -10, "c6", "d5", "Jenna asked about an online shop for beans and merch before the holidays."),
            I(InteractionType.EMAIL, -12, "c10", "d7", "Shared final logo files and stationery print specs."),
            I(InteractionType.MEETING, -14, "c8", "d10", "Coffee meeting about a brand kit for Daniel’s speaking engagements."),
            I(InteractionType.CALL, -15, "c4", "d14", "Dr. Blake mentioned patients struggle with the appointment portal on mobile."),
            I(InteractionType.NOTE, -18, "c11", null, "Busy season ends mid-November; follow up then about the spring campaign."),
            I(InteractionType.EMAIL, -20, "c6", "d13", "Packaging dielines approved; print-ready files sent to the printer."),
            I(InteractionType.CALL, -22, "c2", "d12", "Discussed a monthly social media retainer starting in January."),
            I(InteractionType.MEETING, -25, "c7", null, "Reviewed last quarter’s website analytics with Priya and Kate."),
        )

        val TASKS = listOf(
            T("Send revised proposal to Northwind", -2, deal = "d1"),
            T("Follow up on Brightline quote", -1, deal = "d2"),
            T("Call Copperleaf about shop timeline", -3, client = "c6"),
            T("Prepare negotiation notes for Bluefin", 0, deal = "d8"),
            T("Email Maya the portfolio questionnaire", 0, client = "c3"),
            T("Review booking wireframes", 0, deal = "d6"),
            T("Schedule discovery call with Daniel", 1, deal = "d10"),
            T("Draft retainer agreement", 3, deal = "d12"),
            T("Send patient portal audit outline", 5, deal = "d14"),
            T("Ask Sofia for a testimonial", 8, client = "c10"),
            T("Check in with Greenway after busy season", 12, client = "c11"),
            T("Deliver final photo set", -6, deal = "d3", done = true),
            T("Book kickoff workshop with Northwind", -9, deal = "d1", done = true),
            T("Send logo files to Sofia", -12, deal = "d7", done = true),
        )
    }
}
