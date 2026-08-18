package com.estatedesk.crm.domain

import com.estatedesk.crm.data.Contact
import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.data.Property
import com.estatedesk.crm.data.Store
import com.estatedesk.crm.core.Fmt

/**
 * Professional message drafts generated from real CRM data (deterministic
 * templates — no AI involved, nothing faked). Used for WhatsApp/SMS/email
 * quick actions.
 */
object MessageTemplates {

    fun followUp(contact: Contact?, lead: Lead?, agent: String, company: String): String {
        val name = contact?.firstName?.ifBlank { contact?.displayName() } ?: "there"
        val requirement = lead?.requirement?.take(120) ?: "your property requirement"
        return "Hello $name, this is $agent from $company. " +
                "Following up on your requirement: $requirement. " +
                "Let me know a good time to talk."
    }

    fun propertyIntro(contact: Contact?, p: Property, agent: String, company: String, currency: com.estatedesk.crm.data.CurrencyCfg): String {
        val name = contact?.firstName?.ifBlank { contact?.displayName() } ?: "there"
        val price = Fmt.money(p.price, currency)
        return "Hello $name, this is $agent from $company. I found a property you may like: " +
                "${p.title.ifBlank { p.displayTitle() }} in ${p.areaName.ifBlank { p.location }}, " +
                "priced at $price. Would you like a viewing?"
    }

    fun viewingReminder(contact: Contact?, p: Property, whenText: String): String {
        val name = contact?.firstName?.ifBlank { contact?.displayName() } ?: "there"
        return "Hello $name, a friendly reminder about your viewing of " +
                "${p.title.ifBlank { p.displayTitle() }} on $whenText. See you there!"
    }

    fun genericCheckIn(contact: Contact?, agent: String, company: String): String {
        val name = contact?.firstName?.ifBlank { contact?.displayName() } ?: "there"
        return "Hello $name, $agent from $company here. Just checking in — is there anything new " +
                "on your property search? Happy to help."
    }

    /** Follow-up suggestions derived from actual CRM state. */
    fun suggestions(store: Store, lead: Lead, contact: Contact?): List<String> {
        val out = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val last = maxOf(contact?.lastContacted ?: 0, lead.updatedAt)
        if (last > 0 && now - last > 7L * 86_400_000L) {
            val days = (now - last) / 86_400_000L
            out.add("No contact for $days days — reach out by call or WhatsApp.")
        }
        if (lead.nextFollowUp in 1 until now) {
            val days = (now - lead.nextFollowUp) / 86_400_000L + 1
            out.add("Follow-up overdue by $days day(s).")
        }
        if (lead.budgetMax == 0L) out.add("Budget not recorded — ask about it on the next call.")
        if (lead.location.isBlank()) out.add("Preferred location missing — capture it to enable matching.")
        if (lead.requirement.isBlank()) out.add("Requirement not written down yet — add it to enable property matching.")
        if (lead.stage.equals("New Lead", true)) out.add("New lead — make the first contact within 24 hours.")
        if (lead.nextFollowUp == 0L) out.add("No next follow-up scheduled — set one so this lead is never lost.")
        return out
    }
}
