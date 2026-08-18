package com.estatedesk.crm.domain

import com.estatedesk.crm.core.Csv
import com.estatedesk.crm.data.Contact
import com.estatedesk.crm.data.ContactDao
import com.estatedesk.crm.data.DealDao
import com.estatedesk.crm.data.FinanceDao
import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.data.LeadDao
import com.estatedesk.crm.data.Phone
import com.estatedesk.crm.data.Property
import com.estatedesk.crm.data.PropertyDao
import com.estatedesk.crm.data.Store
import com.estatedesk.crm.data.TaskDao

/** CSV import/export with column mapping, validation and duplicate checks. */
class ImportExport(
    private val s: Store,
    val contacts: ContactDao,
    val leads: LeadDao,
    val properties: PropertyDao,
    val deals: DealDao,
    val tasks: TaskDao,
    val finances: FinanceDao
) {

    // ------------------------------------------------------------ export

    data class ExportSpec(val entity: String, val label: String, val header: List<String>)

    fun exportSpecs(): List<ExportSpec> = listOf(
        ExportSpec("contacts", "Contacts", listOf(
            "first_name", "last_name", "full_name", "phone", "email", "city", "area",
            "classification", "lead_status", "temperature", "priority", "budget",
            "preferred_location", "preferred_type", "lead_source", "notes", "created_at"
        )),
        ExportSpec("leads", "Leads", listOf(
            "title", "contact_name", "contact_phone", "source", "requirement", "budget_min",
            "budget_max", "location", "property_type", "intent", "stage", "priority",
            "probability", "next_action", "notes", "created_at"
        )),
        ExportSpec("properties", "Properties", listOf(
            "title", "type", "sale_rent", "price", "location", "area_name", "size_value",
            "size_unit", "bedrooms", "bathrooms", "floors", "condition", "furnished",
            "owner_name", "owner_phone", "status", "description", "tags", "date_added"
        )),
        ExportSpec("deals", "Deals", listOf(
            "title", "buyer", "seller", "property", "value", "commission_pct",
            "commission_amount", "commission_received", "stage", "expected_close",
            "actual_close", "notes", "created_at"
        )),
        ExportSpec("tasks", "Tasks", listOf(
            "title", "kind", "contact", "property", "lead", "due_date", "priority",
            "status", "recurrence", "notes", "created_at"
        )),
        ExportSpec("finances", "Finances", listOf(
            "direction", "category", "amount", "date", "deal", "notes"
        ))
    )

    fun exportCsv(entity: String): Pair<String, String>? {
        val rows = mutableListOf<List<String>>()
        val header: List<String>
        when (entity) {
            "contacts" -> {
                header = exportSpecs()[0].header
                for (c in contacts.query("", "", "", "", "", 0, "", "", "newest", 0, 100_000)) {
                    val phone = contacts.phones(c.id).joinToString("; ") { it.number }
                    rows.add(listOf(
                        c.firstName, c.lastName, c.fullName, phone, c.email, c.city, c.area,
                        c.classification, c.leadStatus, c.temperature, c.priority.toString(),
                        c.budgetMax.toString(), c.preferredLocation, c.preferredType,
                        c.leadSource, c.notes, c.createdAt.toString()
                    ))
                }
            }
            "leads" -> {
                header = exportSpecs()[1].header
                for (l in leads.query("", "", "", "", "", 0, "", "", "newest", 0, 100_000)) {
                    val c = contacts.byId(l.contactId)
                    val phone = if (l.contactId > 0) contacts.phones(l.contactId).joinToString("; ") { it.number } else ""
                    rows.add(listOf(
                        l.title, c?.displayName() ?: "", phone, l.source, l.requirement,
                        l.budgetMin.toString(), l.budgetMax.toString(), l.location,
                        l.propertyType, l.intent, l.stage, l.priority.toString(),
                        l.probability.toString(), l.nextAction, l.notes, l.createdAt.toString()
                    ))
                }
            }
            "properties" -> {
                header = exportSpecs()[2].header
                for (p in properties.query("", "", "", "", "", 0, "", "newest", 0, 100_000)) {
                    rows.add(listOf(
                        p.title, p.type, p.saleRent, p.price.toString(), p.location, p.areaName,
                        p.sizeValue.toString(), p.sizeUnit, p.bedrooms.toString(),
                        p.bathrooms.toString(), p.floors.toString(), p.condition,
                        if (p.furnished) "1" else "0", p.ownerName, p.ownerPhone, p.status,
                        p.description, p.tags, p.dateAdded.toString()
                    ))
                }
            }
            "deals" -> {
                header = exportSpecs()[3].header
                for (d in deals.query("", "", "newest", 0, 100_000)) {
                    val buyer = contacts.byId(d.buyerContactId)?.displayName() ?: ""
                    val seller = contacts.byId(d.sellerContactId)?.displayName() ?: ""
                    val prop = properties.byId(d.propertyId)?.displayTitle() ?: ""
                    rows.add(listOf(
                        d.title, buyer, seller, prop, d.value.toString(), d.commissionPct.toString(),
                        d.commissionAmount.toString(), d.commissionReceived.toString(), d.stage,
                        d.expectedClose.toString(), (d.actualClose ?: 0).toString(), d.notes,
                        d.createdAt.toString()
                    ))
                }
            }
            "tasks" -> {
                header = exportSpecs()[4].header
                for (t in tasks.query("all", "", "", 0, 0, 0, 100_000)) {
                    val c = contacts.byId(t.contactId)?.displayName() ?: ""
                    val p = properties.byId(t.propertyId)?.displayTitle() ?: ""
                    val l = leads.byId(t.leadId)?.displayTitle() ?: ""
                    rows.add(listOf(
                        t.title, t.kind, c, p, l, t.dueDate.toString(), t.priority.toString(),
                        t.status, t.recurrence, t.notes, t.createdAt.toString()
                    ))
                }
            }
            "finances" -> {
                header = exportSpecs()[5].header
                for (f in finances.query("", "", 0, 0, 100_000)) {
                    val d = deals.byId(f.dealId)?.displayTitle() ?: ""
                    rows.add(listOf(
                        f.direction, f.category, f.amount.toString(), f.date.toString(), d, f.notes
                    ))
                }
            }
            else -> return null
        }
        if (rows.isEmpty()) return null
        val csv = Csv.write(header, rows)
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return "estatedesk-${entity}-$stamp.csv" to csv
    }

    // ------------------------------------------------------------ import

    data class ImportResult(val imported: Int, val skipped: Int, val duplicates: Int, val errors: List<String>)

    fun importRows(entity: String, mapping: Map<String, Int>, rows: List<List<String>>,
                   skipDuplicates: Boolean): ImportResult {
        var imported = 0
        var skipped = 0
        var dup = 0
        val errors = mutableListOf<String>()
        val cur = s.currency()

        fun cell(row: List<String>, field: String): String {
            val idx = mapping[field] ?: -1
            return if (idx in row.indices) row[idx].trim() else ""
        }
        fun longOf(v: String): Long = v.replace(",", "").toLongOrNull() ?: 0L
        fun intOf(v: String): Int = v.replace(",", "").toIntOrNull() ?: 0

        for ((i, row) in rows.withIndex()) {
            try {
                when (entity) {
                    "contacts" -> {
                        val first = cell(row, "first_name")
                        val last = cell(row, "last_name")
                        val phone = cell(row, "phone")
                        val email = cell(row, "email")
                        if (first.isBlank() && last.isBlank() && phone.isBlank()) {
                            skipped++; continue
                        }
                        val full = cell(row, "full_name").ifBlank { "$first $last".trim() }
                        val existing = if (skipDuplicates) {
                            val phones = phone.split(";", ",").map { it.trim() }.filter { it.isNotBlank() }
                            contacts.findDuplicates(first, last, email, phones).isNotEmpty()
                        } else false
                        if (existing) {
                            dup++; skipped++; continue
                        }
                        val budget = longOf(cell(row, "budget"))
                        val contact = Contact(
                            firstName = first, lastName = last, fullName = full,
                            email = email, city = cell(row, "city"), area = cell(row, "area"),
                            classification = cell(row, "classification").ifBlank { "Buyer" },
                            leadStatus = cell(row, "lead_status").ifBlank { "New" },
                            temperature = cell(row, "temperature").ifBlank { "Warm" },
                            priority = intOf(cell(row, "priority")).coerceIn(1, 3).takeIf { it > 0 } ?: 1,
                            budgetMin = 0, budgetMax = budget,
                            preferredLocation = cell(row, "preferred_location"),
                            preferredType = cell(row, "preferred_type"),
                            leadSource = cell(row, "lead_source"),
                            notes = cell(row, "notes")
                        )
                        val phones = phone.split(";", ",").map { it.trim() }
                            .filter { it.isNotBlank() }
                            .mapIndexed { idx2, num ->
                                Phone(0, 0, if (idx2 == 0) "Mobile" else "Other", num, idx2 == 0)
                            }
                        contacts.save(contact, phones, emptyList())
                        imported++
                    }
                    "leads" -> {
                        val title = cell(row, "title")
                        val requirement = cell(row, "requirement")
                        if (title.isBlank() && requirement.isBlank()) {
                            skipped++; continue
                        }
                        val phone = cell(row, "contact_phone")
                        var contactId = 0L
                        if (phone.isNotBlank()) {
                            contactId = contacts.findDuplicates("", "", "", listOf(phone))
                                .firstOrNull()?.id ?: 0L
                        }
                        val stage = cell(row, "stage")
                        val validStage = if (s.leadStageNames().contains(stage)) stage else s.leadStageNames().first()
                        val lead = Lead(
                            title = title.ifBlank { requirement.take(48) },
                            contactId = contactId,
                            source = cell(row, "source"),
                            requirement = requirement,
                            budgetMin = longOf(cell(row, "budget_min")),
                            budgetMax = longOf(cell(row, "budget_max")),
                            location = cell(row, "location"),
                            propertyType = cell(row, "property_type"),
                            intent = cell(row, "intent").ifBlank { "Buy" },
                            stage = validStage,
                            priority = intOf(cell(row, "priority")).coerceIn(1, 3).takeIf { it > 0 } ?: 1,
                            probability = intOf(cell(row, "probability")).coerceIn(0, 100),
                            nextAction = cell(row, "next_action"),
                            notes = cell(row, "notes")
                        )
                        leads.save(lead)
                        imported++
                    }
                    "properties" -> {
                        val title = cell(row, "title")
                        val location = cell(row, "location")
                        if (title.isBlank() && location.isBlank()) {
                            skipped++; continue
                        }
                        val status = cell(row, "status").ifBlank { "Available" }
                        val prop = Property(
                            title = title,
                            type = cell(row, "type"),
                            saleRent = cell(row, "sale_rent").ifBlank { "Sale" },
                            price = longOf(cell(row, "price")),
                            currency = cur.code,
                            location = location,
                            areaName = cell(row, "area_name"),
                            sizeValue = cell(row, "size_value").replace(",", "").toDoubleOrNull() ?: 0.0,
                            sizeUnit = cell(row, "size_unit").ifBlank { "Marla" },
                            bedrooms = intOf(cell(row, "bedrooms")),
                            bathrooms = intOf(cell(row, "bathrooms")),
                            floors = intOf(cell(row, "floors")),
                            condition = cell(row, "condition"),
                            furnished = cell(row, "furnished") == "1" || cell(row, "furnished").equals("true", true),
                            ownerName = cell(row, "owner_name"),
                            ownerPhone = cell(row, "owner_phone"),
                            status = status,
                            description = cell(row, "description"),
                            tags = cell(row, "tags")
                        )
                        properties.save(prop)
                        imported++
                    }
                    else -> skipped++
                }
            } catch (t: Throwable) {
                skipped++
                errors.add("Row ${i + 1}: ${t.message ?: "invalid data"}")
            }
        }
        return ImportResult(imported, skipped, dup, errors)
    }

    fun validationIssues(entity: String, mapping: Map<String, Int>, rows: List<List<String>>): List<String> {
        val issues = mutableListOf<String>()
        fun cell(row: List<String>, field: String): String {
            val idx = mapping[field] ?: -1
            return if (idx in row.indices) row[idx].trim() else ""
        }
        for ((i, row) in rows.withIndex()) {
            when (entity) {
                "contacts" -> {
                    val phone = cell(row, "phone")
                    if (phone.isNotBlank() && phone.filter { it.isDigit() }.length !in 6..15) {
                        issues.add("Row ${i + 1}: unusual phone number")
                    }
                }
                "leads" -> {
                    val budget = cell(row, "budget_max")
                    if (budget.isNotBlank() && budget.replace(",", "").toLongOrNull() == null) {
                        issues.add("Row ${i + 1}: budget is not a number")
                    }
                }
                "properties" -> {
                    val price = cell(row, "price")
                    if (price.isNotBlank() && price.replace(",", "").toLongOrNull() == null) {
                        issues.add("Row ${i + 1}: price is not a number")
                    }
                }
            }
        }
        return issues
    }

    fun countDuplicates(entity: String, mapping: Map<String, Int>, rows: List<List<String>>): Int {
        if (entity != "contacts") return 0
        var n = 0
        fun cell(row: List<String>, field: String): String {
            val idx = mapping[field] ?: -1
            return if (idx in row.indices) row[idx].trim() else ""
        }
        for (row in rows) {
            val phones = cell(row, "phone").split(";", ",").map { it.trim() }.filter { it.isNotBlank() }
            if (contacts.findDuplicates(
                    cell(row, "first_name"), cell(row, "last_name"), cell(row, "email"), phones
                ).isNotEmpty()
            ) n++
        }
        return n
    }
}
