package com.estatedesk.crm.domain

import com.estatedesk.crm.data.Activity
import com.estatedesk.crm.data.Contact
import com.estatedesk.crm.data.ContactDao
import com.estatedesk.crm.data.Deal
import com.estatedesk.crm.data.DealDao
import com.estatedesk.crm.data.Finance
import com.estatedesk.crm.data.FinanceDao
import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.data.LeadDao
import com.estatedesk.crm.data.Phone
import com.estatedesk.crm.data.Property
import com.estatedesk.crm.data.PropertyDao
import com.estatedesk.crm.data.Store
import com.estatedesk.crm.data.Task
import com.estatedesk.crm.data.TaskDao
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Optional demo dataset (opt-in from Settings → Data). Every sample row is
 *  tracked by id in the `sample_ids` setting so it can be removed cleanly. */
class SampleData(
    private val s: Store,
    private val contacts: ContactDao,
    private val leads: LeadDao,
    private val properties: PropertyDao,
    private val deals: DealDao,
    private val tasks: TaskDao,
    private val finances: FinanceDao
) {

    private val ids = mutableListOf<Pair<String, Long>>()

    private fun track(table: String, id: Long) {
        ids.add(table to id)
    }

    private fun t(daysAgo: Long, hour: Int = 10, minute: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -daysAgo.toInt())
        cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun load() {
        if (s.getSetting("sample_ids").isNotBlank()) return

        // -------- contacts
        val c1 = contacts.save(Contact(
            firstName = "Ahmed", lastName = "Raza", fullName = "Ahmed Raza",
            email = "ahmed.raza@example.com", city = "Lahore", area = "DHA Phase 2",
            classification = "Buyer", leadStatus = "Qualified", temperature = "Hot",
            priority = 3, budgetMax = 60_000_000, preferredLocation = "DHA Phase 2",
            preferredType = "House", leadSource = "Referral",
            notes = "Looking for a 10 marla house, immediate purchase."
        ), listOf(Phone(0, 0, "Mobile", "0300-1234501", true)), listOf("VIP"))
        track("contacts", c1)

        val c2 = contacts.save(Contact(
            firstName = "Sara", lastName = "Khan", fullName = "Sara Khan",
            email = "sara.k@example.com", city = "Karachi", area = "Clifton",
            classification = "Buyer", leadStatus = "Contacted", temperature = "Warm",
            priority = 2, budgetMax = 45_000_000, preferredLocation = "Clifton",
            preferredType = "Apartment", leadSource = "Portal",
            notes = "Prefers sea-facing apartment, 3 bedrooms."
        ), listOf(Phone(0, 0, "Mobile", "0301-5557788", true)), emptyList())
        track("contacts", c2)

        val c3 = contacts.save(Contact(
            firstName = "Bilal", lastName = "Sheikh", fullName = "Bilal Sheikh",
            email = "", city = "Islamabad", area = "F-11",
            classification = "Investor", leadStatus = "Customer", temperature = "Hot",
            priority = 3, budgetMax = 120_000_000, preferredLocation = "F-11 / E-11",
            preferredType = "Plot", leadSource = "Walk-in",
            notes = "Buys plots for resale. Cash buyer."
        ), listOf(Phone(0, 0, "Mobile", "0333-9988776", true)), listOf("Investor", "Repeat client"))
        track("contacts", c3)

        val c4 = contacts.save(Contact(
            firstName = "Mahnoor", lastName = "Ali", fullName = "Mahnoor Ali",
            email = "mahnoor.ali@example.com", city = "Lahore", area = "Bahria Town",
            classification = "Buyer", leadStatus = "New", temperature = "Cold",
            priority = 1, budgetMax = 30_000_000, preferredLocation = "Bahria Town",
            preferredType = "House", leadSource = "Social media",
            notes = ""
        ), listOf(Phone(0, 0, "Mobile", "0345-1122334", true)), emptyList())
        track("contacts", c4)

        val c5 = contacts.save(Contact(
            firstName = "Farhan", lastName = "Malik", fullName = "Farhan Malik",
            email = "", city = "Lahore", area = "DHA Phase 6",
            classification = "Seller", leadStatus = "Customer", temperature = "Warm",
            priority = 2, preferredLocation = "",
            preferredType = "", leadSource = "Signboard",
            notes = "Selling 1 kanal house in DHA Phase 6."
        ), listOf(Phone(0, 0, "Mobile", "0322-4567890", false)), emptyList())
        track("contacts", c5)

        val c6 = contacts.save(Contact(
            firstName = "Zara", lastName = "Hussain", fullName = "Zara Hussain",
            email = "zara.h@example.com", city = "Islamabad", area = "Gulberg Greens",
            classification = "Renter", leadStatus = "Contacted", temperature = "Warm",
            priority = 1, budgetMax = 150_000, preferredLocation = "Gulberg Greens",
            preferredType = "House", leadSource = "Portal",
            notes = "Needs a rented house from next month."
        ), listOf(Phone(0, 0, "Mobile", "0312-7788990", true)), emptyList())
        track("contacts", c6)

        val c7 = contacts.save(Contact(
            firstName = "Omar", lastName = "Qureshi", fullName = "Omar Qureshi",
            email = "omar.q@example.com", city = "Lahore", area = "DHA Phase 2",
            classification = "Buyer", leadStatus = "Qualified", temperature = "Warm",
            priority = 2, budgetMax = 55_000_000, preferredLocation = "DHA Phase 2",
            preferredType = "House", leadSource = "Referral",
            notes = "Brother of Ahmed Raza. Also looking in Phase 2."
        ), listOf(Phone(0, 0, "Mobile", "0300-2211334", true)), emptyList())
        track("contacts", c7)

        // -------- properties
        val p1 = properties.save(Property(
            title = "10 Marla Brand New House", type = "House", saleRent = "Sale",
            price = 58_000_000, location = "Lahore", areaName = "DHA Phase 2",
            sizeValue = 10.0, sizeUnit = "Marla", bedrooms = 5, bathrooms = 6, floors = 2,
            condition = "Excellent", furnished = false, ownerName = "Nadeem Estate",
            ownerPhone = "0300-1112223", status = "Available",
            description = "Brand new 10 marla house in DHA Phase 2, near the park. 5 bedrooms with attached baths, imported fittings, modern kitchen.",
            tags = "New, Corner", assignedTo = ""
        ))
        track("properties", p1)

        val p2 = properties.save(Property(
            title = "1 Kanal Luxury Residence", type = "House", saleRent = "Sale",
            price = 115_000_000, location = "Lahore", areaName = "DHA Phase 6",
            sizeValue = 1.0, sizeUnit = "Kanal", bedrooms = 6, bathrooms = 7, floors = 2,
            condition = "New", furnished = true, ownerName = "Farhan Malik",
            ownerPhone = "0322-4567890", status = "Available",
            description = "Fully furnished 1 kanal luxury residence with basement, cinema room and landscaped garden.",
            tags = "Luxury", assignedTo = ""
        ))
        track("properties", p2)

        val p3 = properties.save(Property(
            title = "Sea View 3 Bed Apartment", type = "Apartment", saleRent = "Sale",
            price = 42_000_000, location = "Karachi", areaName = "Clifton",
            sizeValue = 2400.0, sizeUnit = "Sq Ft", bedrooms = 3, bathrooms = 3, floors = 0,
            condition = "Good", furnished = false, ownerName = "Clifton Builders",
            ownerPhone = "0213-5556677", status = "Available",
            description = "High floor sea view apartment in Clifton Block 5, 2400 sq ft, 3 beds.",
            tags = "Sea view", assignedTo = ""
        ))
        track("properties", p3)

        val p4 = properties.save(Property(
            title = "1 Kanal Plot F-11", type = "Plot", saleRent = "Sale",
            price = 95_000_000, location = "Islamabad", areaName = "F-11",
            sizeValue = 1.0, sizeUnit = "Kanal", bedrooms = 0, bathrooms = 0, floors = 0,
            condition = "", furnished = false, ownerName = "CDA Allottee",
            ownerPhone = "0344-5558899", status = "Available",
            description = "Corner plot, west open, possession ready.",
            tags = "Corner", assignedTo = ""
        ))
        track("properties", p4)

        val p5 = properties.save(Property(
            title = "10 Marla House for Rent", type = "House", saleRent = "Rent",
            price = 145_000, location = "Islamabad", areaName = "Gulberg Greens",
            sizeValue = 10.0, sizeUnit = "Marla", bedrooms = 4, bathrooms = 4, floors = 2,
            condition = "Good", furnished = true, ownerName = "Property One",
            ownerPhone = "0311-2223344", status = "Available",
            description = "Furnished 10 marla house on rent, near the main boulevard.",
            tags = "Rent, Furnished", assignedTo = ""
        ))
        track("properties", p5)

        val p6 = properties.save(Property(
            title = "5 Marla Modern House", type = "House", saleRent = "Sale",
            price = 28_000_000, location = "Lahore", areaName = "Bahria Town",
            sizeValue = 5.0, sizeUnit = "Marla", bedrooms = 3, bathrooms = 3, floors = 2,
            condition = "New", furnished = false, ownerName = "Bahria Realtors",
            ownerPhone = "0300-9998877", status = "Available",
            description = "Affordable 5 marla option in Bahria Town, ready to move.",
            tags = "", assignedTo = ""
        ))
        track("properties", p6)

        val p7 = properties.save(Property(
            title = "Commercial Shop Main Blvd", type = "Shop", saleRent = "Rent",
            price = 350_000, location = "Lahore", areaName = "DHA Phase 2",
            sizeValue = 400.0, sizeUnit = "Sq Ft", bedrooms = 0, bathrooms = 1, floors = 0,
            condition = "Good", furnished = false, ownerName = "DHA Commercial",
            ownerPhone = "0300-4445566", status = "Reserved",
            description = "400 sq ft shop on the main boulevard, high foot traffic.",
            tags = "Commercial", assignedTo = ""
        ))
        track("properties", p7)

        // -------- leads
        val l1 = leads.save(Lead(
            title = "10 Marla in DHA Phase 2", contactId = c1, source = "Referral",
            requirement = "Looking for a 10 marla house in DHA Phase 2, budget 6 crore, 5 bedrooms preferred.",
            budgetMin = 45_000_000, budgetMax = 60_000_000, location = "DHA Phase 2",
            propertyType = "House", intent = "Buy", stage = "Property Matched",
            score = 86, priority = 3, probability = 55,
            nextAction = "Arrange viewing of the 10 Marla Brand New House",
            nextFollowUp = t(-1, 15), assignedTo = "", notes = "Cash buyer, wants to decide within 2 weeks."
        ))
        track("leads", l1)

        val l2 = leads.save(Lead(
            title = "Sea-facing flat in Clifton", contactId = c2, source = "Portal",
            requirement = "3 bedroom apartment in Clifton with sea view, budget 4.5 crore.",
            budgetMin = 35_000_000, budgetMax = 45_000_000, location = "Clifton",
            propertyType = "Apartment", intent = "Buy", stage = "Viewing Scheduled",
            score = 72, priority = 2, probability = 40,
            nextAction = "Viewing on Saturday 4pm",
            nextFollowUp = t(1, 16), assignedTo = "", notes = ""
        ))
        track("leads", l2)

        val l3 = leads.save(Lead(
            title = "Investment plots F-11", contactId = c3, source = "Walk-in",
            requirement = "1 kanal plot in F-11 or E-11 for investment, up to 12 crore.",
            budgetMin = 80_000_000, budgetMax = 120_000_000, location = "F-11 / E-11",
            propertyType = "Plot", intent = "Buy", stage = "Negotiation",
            score = 92, priority = 3, probability = 75,
            nextAction = "Negotiate price on the F-11 corner plot",
            nextFollowUp = t(0, 11), assignedTo = "", notes = "Repeat investor."
        ))
        track("leads", l3)

        val l4 = leads.save(Lead(
            title = "House in Bahria Town", contactId = c4, source = "Social media",
            requirement = "5 marla house in Bahria Town under 3 crore.",
            budgetMin = 20_000_000, budgetMax = 30_000_000, location = "Bahria Town",
            propertyType = "House", intent = "Buy", stage = "Contacted",
            score = 41, priority = 1, probability = 15,
            nextAction = "Qualify budget and timeline",
            nextFollowUp = t(2, 10), assignedTo = "", notes = ""
        ))
        track("leads", l4)

        val l5 = leads.save(Lead(
            title = "Rented house Gulberg Greens", contactId = c6, source = "Portal",
            requirement = "Need a furnished house on rent in Gulberg Greens, budget 1.5 lac.",
            budgetMin = 100_000, budgetMax = 150_000, location = "Gulberg Greens",
            propertyType = "House", intent = "Rent", stage = "Requirement Collected",
            score = 58, priority = 1, probability = 30,
            nextAction = "Send rental options",
            nextFollowUp = t(-3, 9), assignedTo = "", notes = "Follow-up overdue!"
        ))
        track("leads", l5)

        val l6 = leads.save(Lead(
            title = "1 Kanal DHA Phase 6", contactId = c7, source = "Referral",
            requirement = "1 kanal house in DHA Phase 6, budget 5.5 crore.",
            budgetMin = 50_000_000, budgetMax = 55_000_000, location = "DHA Phase 6",
            propertyType = "House", intent = "Buy", stage = "New Lead",
            score = 50, priority = 2, probability = 20,
            nextAction = "First call",
            nextFollowUp = t(0, 14), assignedTo = "", notes = ""
        ))
        track("leads", l6)

        // -------- deal
        val d1 = deals.save(Deal(
            title = "F-11 Corner Plot — Bilal Sheikh", buyerContactId = c3,
            propertyId = p4, leadId = l3, value = 95_000_000, commissionPct = 2.0,
            commissionAmount = 1_900_000, commissionReceived = 950_000,
            stage = "Negotiation", expectedClose = t(-14, 0), notes = "Half commission received."
        ))
        track("deals", d1)

        val d2 = deals.save(Deal(
            title = "DHA Phase 2 — Ahmed Raza", buyerContactId = c1,
            propertyId = p1, leadId = l1, value = 58_000_000, commissionPct = 1.5,
            commissionAmount = 870_000, commissionReceived = 0,
            stage = "New", expectedClose = t(-30, 0), notes = ""
        ))
        track("deals", d2)

        // -------- tasks / follow-ups
        track("tasks", tasks.save(Task(
            title = "Follow up with Ahmed Raza on viewing", kind = Task.KIND_FOLLOW_UP,
            contactId = c1, leadId = l1, dueDate = t(-1, 15), dueTimeMinutes = 15 * 60,
            priority = 3, reminder = true, notes = "Ask about the 10 marla house."
        )))
        track("tasks", tasks.save(Task(
            title = "Negotiate price — Bilal Sheikh", kind = Task.KIND_CALL,
            contactId = c3, leadId = l3, dueDate = t(0, 11), dueTimeMinutes = 11 * 60,
            priority = 3, reminder = true, notes = ""
        )))
        track("tasks", tasks.save(Task(
            title = "Viewing: Sea View Apartment with Sara", kind = Task.KIND_VIEWING,
            contactId = c2, propertyId = p3, leadId = l2, dueDate = t(1, 16),
            dueTimeMinutes = 16 * 60, priority = 2, reminder = true, notes = "Meet at the lobby."
        )))
        track("tasks", tasks.save(Task(
            title = "Call Zara — rental options ready", kind = Task.KIND_FOLLOW_UP,
            contactId = c6, leadId = l5, dueDate = t(-3, 9), dueTimeMinutes = 9 * 60,
            priority = 1, reminder = false, notes = "Overdue."
        )))
        track("tasks", tasks.save(Task(
            title = "Update portal listings", kind = Task.KIND_TASK,
            dueDate = t(2, 12), dueTimeMinutes = 12 * 60, priority = 1,
            reminder = false, notes = ""
        )))

        // -------- finances
        track("finances", finances.save(Finance(
            direction = Finance.INCOME, category = "Commission",
            amount = 950_000, date = t(-10, 0), dealId = d1, notes = "First installment — Bilal Sheikh"
        )))
        track("finances", finances.save(Finance(
            direction = Finance.EXPENSE, category = "Marketing",
            amount = 25_000, date = t(-6, 0), dealId = 0, notes = "Facebook ad campaign"
        )))
        track("finances", finances.save(Finance(
            direction = Finance.EXPENSE, category = "Fuel & travel",
            amount = 8_500, date = t(-3, 0), dealId = 0, notes = "Client visits"
        )))

        s.setSetting("sample_ids", org.json.JSONArray(ids.map { "${it.first}:${it.second}" }.toTypedArray()).toString())
        // log a few activities so timelines look alive
        logSampleActivity(c1, l1, p1, "Note", "Requirements collected on the first call.")
        logSampleActivity(c2, l2, p3, "Viewing", "Viewing scheduled for Saturday.")
        logSampleActivity(c3, l3, p4, "Call", "Discussed price expectations — open to 9.5 crore.")
    }

    private fun logSampleActivity(contactId: Long, leadId: Long, propertyId: Long, type: String, body: String) {
        // simple insert via store db
        val cv = android.content.ContentValues().apply {
            put("type", type)
            put("title", type)
            put("body", body)
            put("at", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
            put("contact_id", contactId)
            put("lead_id", leadId)
            put("property_id", propertyId)
            put("created_at", System.currentTimeMillis())
        }
        s.db.insert("activities", null, cv)
    }

    fun hasSample(): Boolean = s.getSetting("sample_ids").isNotBlank()

    fun clear() {
        val raw = s.getSetting("sample_ids")
        if (raw.isBlank()) return
        try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val parts = arr.getString(i).split(":", limit = 2)
                if (parts.size == 2) {
                    s.db.delete(parts[0], "id=?", arrayOf(parts[1]))
                }
            }
        } catch (t: Throwable) {
        }
        s.setSetting("sample_ids", "")
    }
}
