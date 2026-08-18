package com.estatedesk.crm.domain

import com.estatedesk.crm.data.Contact
import com.estatedesk.crm.data.Lead
import java.util.concurrent.TimeUnit

/**
 * Deterministic lead scoring from real CRM signals. Score is recomputed on
 * every save and shown in the UI with an explanation.
 */
object LeadScorer {

    data class Factor(val label: String, val points: Int)

    fun score(lead: Lead, contact: Contact?, phonesCount: Int, stages: List<String>): Pair<Int, List<Factor>> {
        val factors = mutableListOf<Factor>()
        var total = 15 // base

        if (phonesCount > 0 || (contact != null && contact.email.isNotBlank())) {
            total += 10; factors.add(Factor("Contactable", 10))
        }
        if (lead.budgetMax > 0) { total += 10; factors.add(Factor("Budget known", 10)) }
        if (lead.location.isNotBlank()) { total += 10; factors.add(Factor("Location known", 10)) }
        if (lead.propertyType.isNotBlank()) { total += 5; factors.add(Factor("Type known", 5)) }
        if (lead.requirement.isNotBlank()) { total += 5; factors.add(Factor("Requirement captured", 5)) }

        val lastContact = maxOf(contact?.lastContacted ?: 0, lead.updatedAt)
        val now = System.currentTimeMillis()
        if (lastContact > 0) {
            val days = TimeUnit.MILLISECONDS.toDays(now - lastContact)
            when {
                days <= 7 -> { total += 15; factors.add(Factor("Active in last 7 days", 15)) }
                days <= 30 -> { total += 8; factors.add(Factor("Active in last 30 days", 8)) }
                else -> { total -= 5; factors.add(Factor("Stale contact", -5)) }
            }
        }

        if (lead.nextFollowUp > 0) {
            if (lead.nextFollowUp >= now) {
                total += 10; factors.add(Factor("Follow-up scheduled", 10))
            } else {
                total -= 10; factors.add(Factor("Follow-up overdue", -10))
            }
        }

        val stageIdx = stages.indexOf(lead.stage)
        if (stageIdx >= 0) {
            val pts = (stageIdx + 1) * 2
            total += pts; factors.add(Factor("Stage: ${lead.stage}", pts))
        }
        if (lead.source.equals("Referral", true)) {
            total += 8; factors.add(Factor("Referral source", 8))
        }
        when (lead.priority) {
            3 -> { total += 10; factors.add(Factor("High priority", 10)) }
            2 -> { total += 5; factors.add(Factor("Medium priority", 5)) }
        }
        if (contact != null && contact.temperature.equals("Hot", true)) {
            total += 10; factors.add(Factor("Hot temperature", 10))
        }
        return (total.coerceIn(0, 100)) to factors
    }

    fun label(score: Int): String = when {
        score >= 75 -> "Hot prospect"
        score >= 50 -> "Promising"
        score >= 25 -> "Developing"
        else -> "Low signal"
    }
}
