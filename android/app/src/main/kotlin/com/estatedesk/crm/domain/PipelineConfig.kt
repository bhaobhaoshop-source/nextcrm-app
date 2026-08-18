package com.estatedesk.crm.domain

import com.estatedesk.crm.data.StageDef

/** Default, fully customizable pipeline configuration. */
object PipelineConfig {

    val DEFAULT_LEAD_STAGES: List<StageDef> = listOf(
        StageDef("New Lead", 10, "#2E90FA"),
        StageDef("Contacted", 20, "#2E90FA"),
        StageDef("Qualified", 35, "#12B76A"),
        StageDef("Requirement Collected", 45, "#12B76A"),
        StageDef("Property Matched", 55, "#12B76A"),
        StageDef("Viewing Scheduled", 60, "#E8A33D"),
        StageDef("Viewing Completed", 65, "#E8A33D"),
        StageDef("Negotiation", 75, "#E8A33D"),
        StageDef("Documentation", 85, "#7A5AF8"),
        StageDef("Closed Won", 100, "#12B76A"),
        StageDef("Closed Lost", 0, "#F04438"),
        StageDef("Follow-up Later", 25, "#98A2B3")
    )

    val DEFAULT_DEAL_STAGES: List<StageDef> = listOf(
        StageDef("New", 20, "#2E90FA"),
        StageDef("Negotiation", 60, "#E8A33D"),
        StageDef("Documentation", 85, "#7A5AF8"),
        StageDef("Closed Won", 100, "#12B76A"),
        StageDef("Closed Lost", 0, "#F04438")
    )

    val DEFAULT_SOURCES = listOf(
        "Referral", "Portal", "Social media", "Walk-in", "Signboard",
        "Phone call", "Property expo", "Newspaper ad", "Website"
    )

    val DEFAULT_PROPERTY_TYPES = listOf(
        "House", "Apartment", "Plot", "Commercial", "Farmhouse", "Office", "Shop", "Other"
    )

    val DEFAULT_TAGS = listOf(
        "VIP", "Investor", "NRI", "Overseas", "Urgent", "Repeat client", "Hot area"
    )

    val DEFAULT_EXPENSE_CATS = listOf(
        "Marketing", "Fuel & travel", "Office", "Registration & legal", "Other"
    )

    val DEFAULT_INCOME_CATS = listOf(
        "Commission", "Rent income", "Sale income", "Other"
    )

    val STAGE_COLORS = mapOf(
        "New Lead" to "#2E90FA", "Contacted" to "#2E90FA", "Qualified" to "#12B76A",
        "Requirement Collected" to "#12B76A", "Property Matched" to "#12B76A",
        "Viewing Scheduled" to "#E8A33D", "Viewing Completed" to "#E8A33D",
        "Negotiation" to "#E8A33D", "Documentation" to "#7A5AF8",
        "Closed Won" to "#12B76A", "Closed Lost" to "#F04438", "Follow-up Later" to "#98A2B3",
        "New" to "#2E90FA", "Available" to "#12B76A", "Reserved" to "#E8A33D",
        "Sold" to "#175CD3", "Rented" to "#175CD3", "Off Market" to "#98A2B3",
        "Pending" to "#E8A33D"
    )

    fun stageColor(stage: String): Int {
        val hex = STAGE_COLORS[stage] ?: "#175CD3"
        return android.graphics.Color.parseColor(hex)
    }
}
