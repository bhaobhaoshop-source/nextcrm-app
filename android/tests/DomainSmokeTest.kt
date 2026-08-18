// JVM smoke test for EstateDesk domain logic (no Android runtime needed).
import com.estatedesk.crm.core.Csv
import com.estatedesk.crm.core.Security
import com.estatedesk.crm.domain.MatchingEngine
import com.estatedesk.crm.domain.LeadScorer
import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.data.Property
import com.estatedesk.crm.data.Contact

fun main() {
    var pass = 0
    var fail = 0
    fun check(name: String, cond: Boolean) {
        if (cond) { pass++; println("PASS  $name") } else { fail++; println("FAIL  $name") }
    }

    // ---- Matching engine
    val eng = MatchingEngine()
    val lead = Lead(
        title = "10 marla DHA Phase 2", requirement = "10 marla house in DHA Phase 2, budget 6 crore, 5 beds",
        budgetMin = 45_000_000, budgetMax = 60_000_000, location = "DHA Phase 2",
        propertyType = "House", intent = "Buy"
    )
    val good = Property(title = "10 Marla New House", type = "House", saleRent = "Sale",
        price = 58_000_000, location = "Lahore", areaName = "DHA Phase 2", sizeValue = 10.0,
        sizeUnit = "Marla", bedrooms = 5, bathrooms = 6, status = "Available")
    val bad = Property(title = "Sea View Flat", type = "Apartment", saleRent = "Sale",
        price = 42_000_000, location = "Karachi", areaName = "Clifton", sizeValue = 2400.0,
        sizeUnit = "Sq Ft", bedrooms = 3, bathrooms = 3, status = "Available")
    val overBudget = good.copy(id = 2, price = 90_000_000, areaName = "DHA Phase 2")

    val m1 = eng.match(lead, listOf(good, bad, overBudget)).firstOrNull()
    check("matching: top match is the DHA house", m1?.property?.areaName == "DHA Phase 2")
    check("matching: score >= 70 for a perfect fit", (m1?.score ?: 0) >= 70)
    val overBudgetMatch = eng.match(lead, listOf(overBudget)).firstOrNull()
    check("matching: over-budget flagged with Budget ✗",
        overBudgetMatch?.criteria?.any { it.label == "Budget" && it.verdict == MatchingEngine.Verdict.NO } == true)
    check("matching: filters non-available properties",
        eng.match(lead, listOf(good.copy(id = 9, status = "Sold"))).isEmpty())

    val req = eng.parseRequirement("5 bedroom 10 marla in DHA budget 6 crore 4 bath")
    check("requirement parse: bedrooms=5", req.bedrooms == 5)
    check("requirement parse: size=10 marla", req.sizeValue == 10.0 && req.sizeUnit == "marla")
    check("requirement parse: budget 6 crore -> 60,000,000", req.maxPrice == 60_000_000L)

    // ---- Lead scoring
    val contact = Contact(fullName = "Ahmed Raza", temperature = "Hot", lastContacted = System.currentTimeMillis())
    val (score, factors) = LeadScorer.score(lead.copy(nextFollowUp = System.currentTimeMillis() + 86_400_000), contact, 1,
        listOf("New Lead", "Contacted", "Qualified"))
    check("scoring: hot + follow-up + budget yields high score", score >= 60)
    check("scoring: factors list is non-empty", factors.isNotEmpty())

    // ---- CSV
    val csv = "Name,Phone,Note\r\n\"Ali, Khan\",03001234567,\"line1\nline2\"\r\nSara,555,\"quoted \"\"value\"\"\"\r\n"
    val rows = Csv.parse(csv)
    check("csv: 2 data rows", rows.size == 3)
    check("csv: quoted comma handled", rows[1][0] == "Ali, Khan")
    check("csv: embedded newline handled", rows[1][2] == "line1\nline2")
    check("csv: escaped quotes handled", rows[2][2] == "quoted \"value\"")
    val roundtrip = Csv.write(listOf("a", "b"), listOf(listOf("x,y", "z"), listOf("1", "2")))
    check("csv: roundtrip parse", Csv.parse(roundtrip).size == 3 && Csv.parse(roundtrip)[1][0] == "x,y")

    // ---- Security (PIN hashing)
    val salt = Security.randomSalt()
    val hash = Security.hashPin("1234", salt)
    check("security: correct PIN verifies", Security.verifyPin("1234", Security.saltToHex(salt), hash))
    check("security: wrong PIN rejected", !Security.verifyPin("9999", Security.saltToHex(salt), hash))

    println("\n$pass passed, $fail failed")
    kotlin.system.exitProcess(if (fail == 0) 0 else 1)
}
