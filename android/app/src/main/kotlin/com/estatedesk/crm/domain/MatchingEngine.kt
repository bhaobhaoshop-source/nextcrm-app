package com.estatedesk.crm.domain

import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.data.Property

/**
 * Deterministic, rule-based property matching. Every match is explained with
 * per-criterion verdicts (✓ / △ / ✗) so the score is always transparent.
 * No fake AI — this is plain weighted rules over real data.
 */
class MatchingEngine {

    data class Criterion(val label: String, val verdict: Verdict, val note: String)

    enum class Verdict { YES, PARTIAL, NO, UNKNOWN }

    data class MatchResult(
        val property: Property,
        val score: Int,
        val criteria: List<Criterion>
    )

    fun match(lead: Lead, properties: List<Property>): List<MatchResult> {
        val req = parseRequirement(lead.requirement)
        val results = properties
            .filter { it.status != "Sold" && it.status != "Rented" && it.status != "Off Market" }
            .map { p -> scoreProperty(lead, req, p) }
            .filter { it.score >= 35 }
            .sortedByDescending { it.score }
        return results
    }

    data class Requirement(
        val bedrooms: Int = 0,
        val bathrooms: Int = 0,
        val sizeValue: Double = 0.0,
        val sizeUnit: String = "",
        val minPrice: Long = 0,
        val maxPrice: Long = 0
    )

    /** Extract structured bits from free-text requirements like
     *  "10 marla house in DHA Phase 2, budget 6 crore, 5 beds". */
    fun parseRequirement(text: String): Requirement {
        var beds = 0
        var baths = 0
        var size = 0.0
        var unit = ""
        val t = text.lowercase()

        Regex("(\\d+)\\s*(?:bed|bedroom|beds|bd)").find(t)?.let { beds = it.groupValues[1].toIntOrNull() ?: 0 }
        Regex("(\\d+)\\s*(?:bath|bathroom|baths|ba)").find(t)?.let { baths = it.groupValues[1].toIntOrNull() ?: 0 }
        for (u in listOf("kanal", "marla", "sqft", "sq ft", "sqyd", "sq yd", "sqm", "sq m", "acre")) {
            Regex("(\\d+(?:\\.\\d+)?)\\s*$u").find(t)?.let {
                size = it.groupValues[1].toDoubleOrNull() ?: 0.0
                unit = u
            }
            if (size > 0) break
        }
        // budget in text: "budget 6 crore" / "6 crore" / "2.5 million"
        var maxPrice = 0L
        Regex("budget\\s*(?:of\\s*)?([\\d.]+)\\s*(crore|lac|lakh|million|m|k)").find(t)?.let {
            val n = it.groupValues[1].toDoubleOrNull() ?: 0.0
            maxPrice = when (it.groupValues[2]) {
                "crore" -> (n * 10_000_000).toLong()
                "lac", "lakh" -> (n * 100_000).toLong()
                "million", "m" -> (n * 1_000_000).toLong()
                "k" -> (n * 1_000).toLong()
                else -> 0L
            }
        }
        if (maxPrice == 0L) {
            Regex("([\\d.]+)\\s*(crore)").find(t)?.let {
                maxPrice = ((it.groupValues[1].toDoubleOrNull() ?: 0.0) * 10_000_000).toLong()
            }
        }
        return Requirement(beds, baths, size, unit, 0, maxPrice)
    }

    private fun scoreProperty(lead: Lead, req: Requirement, p: Property): MatchResult {
        val criteria = mutableListOf<Criterion>()
        var score = 0

        // --- Budget (weight 25)
        val budget = lead.budgetMax.takeIf { it > 0 } ?: req.maxPrice
        if (budget > 0 && p.price > 0) {
            when {
                p.price <= budget -> {
                    score += 25
                    criteria.add(Criterion("Budget", Verdict.YES, "within budget"))
                }
                p.price <= (budget * 1.1).toLong() -> {
                    score += 15
                    criteria.add(Criterion("Budget", Verdict.PARTIAL, "slightly over budget"))
                }
                else -> criteria.add(Criterion("Budget", Verdict.NO, "over budget"))
            }
        } else {
            criteria.add(Criterion("Budget", Verdict.UNKNOWN, "no budget set"))
        }

        // --- Location (weight 15)
        val want = lead.location.trim().lowercase()
        val have = (p.areaName + " " + p.location).lowercase()
        if (want.isNotBlank()) {
            val wantTokens = want.split(Regex("[,\\s]+")).filter { it.length > 2 }
            val matched = wantTokens.any { have.contains(it) } ||
                    want.split(Regex("[,\\s]+")).any { tok -> tok.length > 3 && have.contains(tok) }
            if (matched) {
                score += 15
                criteria.add(Criterion("Location", Verdict.YES, "matches preferred location"))
            } else {
                criteria.add(Criterion("Location", Verdict.NO, "different location"))
            }
        } else {
            criteria.add(Criterion("Location", Verdict.UNKNOWN, "no location preference"))
        }

        // --- Property type (weight 10)
        if (lead.propertyType.isNotBlank()) {
            if (p.type.equals(lead.propertyType, true) ||
                (lead.propertyType.equals("House", true) && p.type.equals("Farmhouse", true))) {
                score += 10
                criteria.add(Criterion("Property type", Verdict.YES, p.type))
            } else {
                criteria.add(Criterion("Property type", Verdict.NO, "wants ${lead.propertyType}, this is ${p.type}"))
            }
        } else {
            criteria.add(Criterion("Property type", Verdict.UNKNOWN, "no type preference"))
        }

        // --- Sale / rent intent (weight 15)
        if (lead.intent.isNotBlank()) {
            val wanted = if (lead.intent.equals("Buy", true)) "Sale" else "Rent"
            if (p.saleRent.equals(wanted, true)) {
                score += 15
                criteria.add(Criterion("Sale / rent", Verdict.YES, p.saleRent))
            } else {
                criteria.add(Criterion("Sale / rent", Verdict.NO, "looking to ${lead.intent.lowercase()}, this is for ${p.saleRent.lowercase()}"))
            }
        }

        // --- Bedrooms (weight 10)
        if (req.bedrooms > 0) {
            when {
                p.bedrooms == req.bedrooms -> {
                    score += 10
                    criteria.add(Criterion("Bedrooms", Verdict.YES, "${p.bedrooms} bedrooms"))
                }
                p.bedrooms > req.bedrooms -> {
                    score += 7
                    criteria.add(Criterion("Bedrooms", Verdict.PARTIAL, "has ${p.bedrooms}, wants ${req.bedrooms}"))
                }
                else -> criteria.add(Criterion("Bedrooms", Verdict.NO, "only ${p.bedrooms}"))
            }
        } else {
            criteria.add(Criterion("Bedrooms", Verdict.UNKNOWN, "not specified"))
        }

        // --- Bathrooms (weight 5)
        if (req.bathrooms > 0) {
            if (p.bathrooms >= req.bathrooms) {
                score += 5
                criteria.add(Criterion("Bathrooms", Verdict.YES, "${p.bathrooms} bathrooms"))
            } else {
                criteria.add(Criterion("Bathrooms", Verdict.NO, "only ${p.bathrooms}"))
            }
        }

        // --- Size (weight 10)
        if (req.sizeValue > 0) {
            val wantUnit = normalizeUnit(req.sizeUnit)
            val haveUnit = normalizeUnit(p.sizeUnit)
            if (wantUnit == haveUnit && p.sizeValue > 0) {
                val ratio = p.sizeValue / req.sizeValue
                when {
                    ratio in 0.9..1.25 -> {
                        score += 10
                        criteria.add(Criterion("Size", Verdict.YES, "${p.sizeValue} ${p.sizeUnit}"))
                    }
                    ratio in 0.7..1.6 -> {
                        score += 6
                        criteria.add(Criterion("Size", Verdict.PARTIAL, "${p.sizeValue} ${p.sizeUnit} vs ${req.sizeValue} ${req.sizeUnit}"))
                    }
                    else -> criteria.add(Criterion("Size", Verdict.NO, "${p.sizeValue} ${p.sizeUnit}"))
                }
            } else {
                criteria.add(Criterion("Size", Verdict.UNKNOWN, "different unit"))
            }
        } else {
            criteria.add(Criterion("Size", Verdict.UNKNOWN, "not specified"))
        }

        // --- Status bonus (weight 10)
        if (p.status == "Available") {
            score += 10
            criteria.add(Criterion("Availability", Verdict.YES, "currently available"))
        } else {
            criteria.add(Criterion("Availability", Verdict.PARTIAL, p.status))
        }

        return MatchResult(p, score.coerceIn(0, 100), criteria)
    }

    private fun normalizeUnit(u: String): String = when (u.lowercase().trim()) {
        "marla" -> "marla"
        "kanal" -> "kanal"
        "sqft", "sq ft" -> "sqft"
        "sqyd", "sq yd" -> "sqyd"
        "sqm", "sq m" -> "sqm"
        "acre" -> "acre"
        else -> u.lowercase().trim()
    }
}
