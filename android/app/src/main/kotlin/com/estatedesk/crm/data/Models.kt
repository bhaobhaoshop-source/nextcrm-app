package com.estatedesk.crm.data

/** Core entities. Timestamps are epoch millis (UTC). Money is stored in minor
 *  units (e.g. cents) as Long — never floating point. Deleted records keep a
 *  non-null deletedAt (Recycle Bin). */

data class Contact(
    val id: Long = 0,
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val avatarPath: String = "",
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val area: String = "",
    val classification: String = "Buyer",
    val leadStatus: String = "New",
    val temperature: String = "Warm",
    val priority: Int = 1,
    val budgetMin: Long = 0,
    val budgetMax: Long = 0,
    val preferredLocation: String = "",
    val preferredType: String = "",
    val leadSource: String = "",
    val assignedTo: String = "",
    val notes: String = "",
    val lastContacted: Long = 0,
    val nextFollowUp: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null
) {
    fun displayName(): String =
        if (fullName.isNotBlank()) fullName
        else listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unnamed" }
}

data class Phone(
    val id: Long = 0,
    val contactId: Long = 0,
    val label: String = "Mobile",
    val number: String = "",
    val isWhatsapp: Boolean = false
)

data class Lead(
    val id: Long = 0,
    val title: String = "",
    val contactId: Long = 0,
    val source: String = "",
    val requirement: String = "",
    val budgetMin: Long = 0,
    val budgetMax: Long = 0,
    val location: String = "",
    val propertyType: String = "",
    val intent: String = "Buy",
    val stage: String = "New Lead",
    val score: Int = 0,
    val priority: Int = 1,
    val probability: Int = 10,
    val nextAction: String = "",
    val nextFollowUp: Long = 0,
    val assignedTo: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    val dealId: Long = 0
) {
    fun displayTitle(): String = title.ifBlank { requirement.lineSequence().firstOrNull()?.take(48) ?: "Lead" }
}

data class Property(
    val id: Long = 0,
    val title: String = "",
    val type: String = "",
    val saleRent: String = "Sale",
    val price: Long = 0,
    val currency: String = "",
    val location: String = "",
    val areaName: String = "",
    val sizeValue: Double = 0.0,
    val sizeUnit: String = "Marla",
    val bedrooms: Int = 0,
    val bathrooms: Int = 0,
    val floors: Int = 0,
    val condition: String = "",
    val furnished: Boolean = false,
    val ownerName: String = "",
    val ownerPhone: String = "",
    val status: String = "Available",
    val description: String = "",
    val tags: String = "",
    val assignedTo: String = "",
    val dateAdded: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null
) {
    fun displayTitle(): String = title.ifBlank { "${type.ifBlank { "Property" }} in ${areaName.ifBlank { location }}" }
    fun tagList(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

data class PropertyPhoto(
    val id: Long = 0,
    val propertyId: Long = 0,
    val path: String = "",
    val caption: String = "",
    val sort: Int = 0
)

data class Deal(
    val id: Long = 0,
    val title: String = "",
    val buyerContactId: Long = 0,
    val sellerContactId: Long = 0,
    val propertyId: Long = 0,
    val leadId: Long = 0,
    val value: Long = 0,
    val currency: String = "",
    val commissionPct: Double = 0.0,
    val commissionAmount: Long = 0,
    val commissionReceived: Long = 0,
    val stage: String = "Negotiation",
    val expectedClose: Long = 0,
    val actualClose: Long? = null,
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null
) {
    fun displayTitle(): String = title.ifBlank { "Deal" }
    fun pendingCommission(): Long = (commissionAmount - commissionReceived).coerceAtLeast(0)
}

data class Task(
    val id: Long = 0,
    val title: String = "",
    val kind: String = "Task",
    val contactId: Long = 0,
    val propertyId: Long = 0,
    val leadId: Long = 0,
    val dealId: Long = 0,
    val dueDate: Long = 0,
    val dueTimeMinutes: Int = -1,
    val priority: Int = 1,
    val status: String = "Open",
    val reminder: Boolean = false,
    val recurrence: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
    val deletedAt: Long? = null
) {
    companion object {
        const val KIND_TASK = "Task"
        const val KIND_FOLLOW_UP = "Follow-up"
        const val KIND_CALL = "Call"
        const val KIND_MEETING = "Meeting"
        const val KIND_VIEWING = "Viewing"
        const val KIND_EMAIL = "Email"
        const val STATUS_OPEN = "Open"
        const val STATUS_DONE = "Done"
        const val REC_NONE = ""
        const val REC_DAILY = "Daily"
        const val REC_WEEKLY = "Weekly"
        const val REC_MONTHLY = "Monthly"
    }

    /** Absolute due timestamp: start-of-day millis + minutes. */
    fun dueAt(): Long = if (dueDate == 0L) 0L else dueDate + dueTimeMinutes * 60_000L
}

data class Activity(
    val id: Long = 0,
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val at: Long = 0,
    val contactId: Long = 0,
    val propertyId: Long = 0,
    val leadId: Long = 0,
    val dealId: Long = 0,
    val taskId: Long = 0,
    val createdAt: Long = 0
) {
    companion object {
        const val T_CALL = "Call"
        const val T_WHATSAPP = "WhatsApp"
        const val T_SMS = "SMS"
        const val T_EMAIL = "Email"
        const val T_MEETING = "Meeting"
        const val T_VIEWING = "Viewing"
        const val T_NOTE = "Note"
        const val T_FOLLOW_UP = "Follow-up"
        const val T_STATUS = "Status change"
        const val T_DEAL = "Deal update"
        const val T_TASK = "Task"
        const val T_REMINDER = "Reminder"
    }
}

data class Finance(
    val id: Long = 0,
    val direction: String = "Expense",
    val category: String = "",
    val amount: Long = 0,
    val currency: String = "",
    val date: Long = 0,
    val dealId: Long = 0,
    val notes: String = "",
    val createdAt: Long = 0,
    val deletedAt: Long? = null
) {
    companion object {
        const val INCOME = "Income"
        const val EXPENSE = "Expense"
    }
}

data class Document(
    val id: Long = 0,
    val name: String = "",
    val path: String = "",
    val mime: String = "",
    val size: Long = 0,
    val entityType: String = "",
    val entityId: Long = 0,
    val createdAt: Long = 0,
    val deletedAt: Long? = null
)

data class CustomFieldDef(
    val key: String = "",
    val label: String = "",
    val type: String = "Text",
    val options: String = "",
    val entities: String = "",
    val sort: Int = 0
)

data class StageDef(
    val name: String = "",
    val probability: Int = 10,
    val color: String = ""
)

data class SearchHit(
    val type: String = "",
    val entityId: Long = 0,
    val name: String = "",
    val detail: String = ""
)

data class StatRow(val label: String = "", val value: Double = 0.0)

data class CurrencyCfg(
    val code: String = "PKR",
    val symbol: String = "Rs",
    val decimals: Int = 0,
    val prefix: Boolean = true
)
