package com.gryezen.civicpulse.data.local

import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.CreateComplaintResponse
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.data.model.RoadmapStep
import com.gryezen.civicpulse.data.model.RoadmapStepStatus
import kotlin.random.Random

/**
 * Everything in this file is a Kotlin port of data/logic that only exists
 * client-side on the web app today, because the corresponding backend
 * endpoints aren't built yet:
 *
 * - [DEMO_DOCKETS]      <- track.html's `mockDockets`
 * - [DEMO_DASHBOARD_COMPLAINTS] <- dashboard.html's `mockComplaints`
 * - [DEMO_POLICIES]     <- static/main.js's `CP_POLICIES`
 * - [classifyComplaintLocally] <- complaint.html's `classifyComplaint()` / CATEGORY_RULES
 * - [scoreDockets]      <- track.html's `scoreDockets()` (keyword-overlap NLP stand-in)
 * - [scorePolicies]     <- static/main.js's `scorePolicies()`
 *
 * Repositories fall back to this data when the real endpoint 404s/fails, so
 * the app stays fully demoable end to end. Delete the fallback branches (not
 * this file — the classifier logic is worth keeping as a client-side
 * pre-check even after the server is live) once each endpoint ships.
 */

// ---------------------------------------------------------------- dockets

val DEMO_DOCKETS: Map<String, Complaint> = linkedMapOf(
    "CP-5102" to Complaint(
        id = "CP-5102", title = "Open manhole near school gate, Anna Nagar",
        category = "Public Safety", department = "Municipal Corporation — Public Safety Cell",
        language = "Tamil", languageNative = "தமிழ்",
        priority = 96, filed = "2026-08-17", filedDisplay = "17 Aug 2026", files = 3,
        authority = "Municipal Corporation Services", stage = "processing",
        body = "Uncovered manhole directly outside a primary school gate, first reported this morning. High foot traffic of children before 8am.",
        note = "Flagged Urgent by the model due to child-safety keywords and proximity to a school. Escalated directly to the Public Safety Cell."
    ),
    "CP-5099" to Complaint(
        id = "CP-5099", title = "Transformer sparking after rain, Sector 12",
        category = "Electricity", department = "Electricity Board",
        language = "Hindi", languageNative = "हिंदी",
        priority = 91, filed = "2026-08-16", filedDisplay = "16 Aug 2026", files = 2,
        authority = "Electricity Board", stage = "assigned",
        body = "Visible sparking from a pole-mounted transformer since last night's rain. Residents have been asked to stay clear of the area.",
        note = "Assigned to an Electricity Board field engineer. Ranked Urgent on fire/electrocution risk terms detected in the complaint text."
    ),
    "CP-4821" to Complaint(
        id = "CP-4821", title = "Streetlight not working on 4th Cross Road",
        category = "Street Lighting", department = "Ward Office",
        language = "English", languageNative = "English",
        priority = 58, filed = "2026-08-12", filedDisplay = "12 Aug 2026", files = 2,
        authority = "Municipal Corporation Services", stage = "processing",
        body = "Streetlight pole #14 on 4th Cross Road has been dark for over a week. Area is unsafe for pedestrians after 7pm.",
        note = "Classified as a duplicate cluster with 6 similar reports and routed to the Ward Office. Expect an update within 48 hours."
    ),
    "CP-5087" to Complaint(
        id = "CP-5087", title = "Sewage overflow onto main road, Kumaran Street",
        category = "Drainage & Sewage", department = "Drainage & Sewage Board",
        language = "Tamil", languageNative = "தமிழ்",
        priority = 83, filed = "2026-08-15", filedDisplay = "15 Aug 2026", files = 4,
        authority = "Drainage & Sewage Board", stage = "received",
        body = "Sewage line overflow has been flowing onto the main carriageway for two days, causing a strong odour and health concerns.",
        note = "Queued for AI triage — high priority pre-score due to public-health terms; awaiting confirmation from the model."
    ),
    "CP-4790" to Complaint(
        id = "CP-4790", title = "Garbage not collected for 6 days, Ward 14",
        category = "Sanitation & Waste", department = "Ward / Panchayat Office",
        language = "English", languageNative = "English",
        priority = 47, filed = "2026-08-09", filedDisplay = "09 Aug 2026", files = 3,
        authority = "Ward / Panchayat Office", stage = "received",
        body = "Household waste has piled up on the corner of Ward 14 since last Tuesday. Attracting stray animals.",
        note = "In the queue for AI triage. This usually takes under a minute — check back shortly."
    ),
    "CP-5061" to Complaint(
        id = "CP-5061", title = "Pothole cluster causing two-wheeler accidents",
        category = "Roads & Potholes", department = "PWD (Roads)",
        language = "Kannada", languageNative = "ಕನ್ನಡ",
        priority = 72, filed = "2026-08-14", filedDisplay = "14 Aug 2026", files = 5,
        authority = "PWD (Roads)", stage = "assigned",
        body = "A cluster of deep potholes near the flyover entrance has caused at least two reported two-wheeler falls this week.",
        note = "Ranked High on injury-risk and repeat-mention signals; assigned to PWD for inspection within 5 working days."
    ),
    "CP-5040" to Complaint(
        id = "CP-5040", title = "Encroachment blocking footpath, MG Road",
        category = "Encroachment", department = "Town Planning (Encroachment)",
        language = "Telugu", languageNative = "తెలుగు",
        priority = 34, filed = "2026-08-11", filedDisplay = "11 Aug 2026", files = 1,
        authority = "Town Planning (Encroachment)", stage = "received",
        body = "A vendor stall has expanded onto the footpath, forcing pedestrians onto the road.",
        note = "Ranked Low relative to the current queue — no immediate safety signal detected — but retained for routing."
    ),
    "CP-4602" to Complaint(
        id = "CP-4602", title = "Water supply pipeline leak near bus stand",
        category = "Water Supply", department = "Public Sector Utility (Water Works)",
        language = "English", languageNative = "English",
        priority = 64, filed = "2026-07-27", filedDisplay = "27 Jul 2026", files = 1,
        authority = "Public Sector Utility", stage = "resolved",
        body = "Visible leak from the main pipeline opposite the bus stand, wasting water and flooding the walkway.",
        note = "Marked resolved by the Public Works Engineer on 3 Aug 2026. Reopen this docket if the issue recurs."
    ),
    "CP-5011" to Complaint(
        id = "CP-5011", title = "Streetlight flickering, minor — Gandhi Nagar",
        category = "Street Lighting", department = "Ward Office",
        language = "Bengali", languageNative = "বাংলা",
        priority = 21, filed = "2026-08-08", filedDisplay = "08 Aug 2026", files = 0,
        authority = "Municipal Corporation Services", stage = "resolved",
        body = "One streetlight flickers intermittently but still functions. Low urgency, filed for the record.",
        note = "Resolved — bulb replaced during routine ward maintenance on 10 Aug 2026."
    )
)

/** The simpler shape shown on the dashboard (dashboard.html's `mockComplaints`). */
val DEMO_DASHBOARD_COMPLAINTS: List<Complaint> = listOf(
    Complaint(id = "CP-4821", title = "Streetlight not working on 4th Cross Road", authority = "Municipal Corporation Services", filedDisplay = "12 Aug 2026", stage = "processing"),
    Complaint(id = "CP-4790", title = "Garbage not collected for 6 days, Ward 14", authority = "Ward / Panchayat Office", filedDisplay = "09 Aug 2026", stage = "received"),
    Complaint(id = "CP-4602", title = "Water supply pipeline leak near bus stand", authority = "Public Sector Utility", filedDisplay = "27 Jul 2026", stage = "resolved")
)

const val STAGE_LABEL_TRIAGE = "AI triage"

fun stageLabel(stage: String): String = if (stage == "processing") STAGE_LABEL_TRIAGE else stage.replaceFirstChar { it.uppercase() }

val STAGE_ORDER = listOf("received", "processing", "assigned", "resolved")

// ------------------------------------------------------------------ search

private val STOPWORDS = setOf(
    "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "near", "not",
    "for", "and", "of", "to", "my", "our", "it", "this", "that", "with", "from", "has", "have", "been",
    "there", "still", "again", "since", "me", "please", "i", "we", "issue", "problem"
)

/** Same keyword-overlap stand-in as track.html's scoreDockets(). */
fun scoreDockets(query: String, dockets: Map<String, Complaint> = DEMO_DOCKETS): Map<String, Int> {
    val terms = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in STOPWORDS }.toSet()
    return dockets.mapValues { (_, d) ->
        val titleText = d.title.lowercase()
        val wideText = "${d.body} ${d.category} ${d.department} ${d.authority}".lowercase()
        terms.sumOf { t -> if (titleText.contains(t)) 3 else if (wideText.contains(t)) 1 else 0 }
    }
}

// ----------------------------------------------------------------- filing

private data class ClassificationRule(
    val category: String,
    val department: String,
    val keywords: List<String>,
    val boost: Int
)

// Same keyword table as complaint.html's CATEGORY_RULES.
private val CATEGORY_RULES = listOf(
    ClassificationRule("Public Safety", "Municipal Corporation — Public Safety Cell",
        listOf("danger", "safety", "accident", "manhole", "child", "school", "theft"), 20),
    ClassificationRule("Electricity", "Electricity Board",
        listOf("transformer", "power", "electricity", "wire", "spark", "shock"), 15),
    ClassificationRule("Water Supply", "Public Sector Utility (Water Works)",
        listOf("water", "pipeline", "tap", "supply"), 5),
    ClassificationRule("Drainage & Sewage", "Drainage & Sewage Board",
        listOf("drain", "sewage", "overflow", "flood"), 10),
    ClassificationRule("Roads & Potholes", "PWD (Roads)",
        listOf("pothole", "road", "highway", "tar"), 8),
    ClassificationRule("Street Lighting", "Ward Office",
        listOf("streetlight", "light", "lamp", "bulb", "dark"), 0),
    ClassificationRule("Sanitation & Waste", "Ward / Panchayat Office",
        listOf("garbage", "waste", "trash", "sanitation", "collected"), 0),
    ClassificationRule("Encroachment", "Town Planning (Encroachment)",
        listOf("encroachment", "footpath", "illegal", "stall", "vendor"), -5)
)

/** Same stand-in as complaint.html's classifyComplaint(). */
fun classifyComplaintLocally(title: String, body: String, language: String): CreateComplaintResponse {
    val text = "$title $body".lowercase()
    var best: Pair<ClassificationRule, Int>? = null
    for (rule in CATEGORY_RULES) {
        val hits = rule.keywords.count { text.contains(it) }
        if (hits > 0 && (best == null || hits > best.second)) best = rule to hits
    }
    val docketId = "CP-" + (1000 + Random.nextInt(9000))
    if (best == null) {
        return CreateComplaintResponse(
            id = docketId, category = "General Grievance", department = "Municipal Corporation Services",
            priority = 35 + Random.nextInt(15), language = language
        )
    }
    val (rule, hits) = best
    val base = 45 + hits * 12 + rule.boost
    val priority = (base + Random.nextInt(10)).coerceIn(5, 99)
    return CreateComplaintResponse(docketId, rule.category, rule.department, priority, language)
}

// ---------------------------------------------------------------- policies

private fun step(phase: String, detail: String, status: RoadmapStepStatus) = RoadmapStep(phase, detail, status)

/** Direct Kotlin port of CP_POLICIES in static/main.js. */
val DEMO_POLICIES: List<Policy> = listOf(
    Policy(
        slug = "pm-awas-yojana", title = "PM Awas Yojana — Urban Housing Scheme",
        category = "Housing",
        summary = "Subsidised home loans for first-time urban homebuyers from low- and middle-income groups.",
        keywords = listOf("housing", "home", "rent", "shelter", "urban", "homeless", "house"),
        eligibility = "First-time homebuyers in EWS/LIG/MIG income brackets, urban households without a pucca house.",
        roadmap = listOf(
            step("Check eligibility", "Confirm your income bracket and that your household doesn't already own a pucca house.", RoadmapStepStatus.done),
            step("Apply", "Submit via the PMAY-U portal or your nearest Common Service Centre with income and ID proof.", RoadmapStepStatus.current),
            step("Verification", "Your Urban Local Body and lending bank verify documents and, often, the site — typically 4–6 weeks.", RoadmapStepStatus.upcoming),
            step("Subsidy disbursed", "The interest subsidy is credited directly to your home loan account.", RoadmapStepStatus.upcoming)
        )
    ),
    Policy(
        slug = "msw-grievance-redressal", title = "Municipal Solid Waste Grievance Redressal",
        category = "Sanitation & Waste",
        summary = "The timeline SLAs your municipal corporation is legally bound to for garbage-collection complaints.",
        keywords = listOf("garbage", "waste", "trash", "sanitation", "collection", "dump", "bin"),
        eligibility = "Any resident within municipal corporation limits — no application needed, the SLA applies automatically.",
        roadmap = listOf(
            step("Complaint logged", "Your report is timestamped the moment it reaches the ward office.", RoadmapStepStatus.done),
            step("24-hour acknowledgement", "The ward is required to acknowledge and schedule a collection run within 24 hours.", RoadmapStepStatus.current),
            step("Collection within 72 hours", "Backlog clearance is mandated within 3 working days of the complaint.", RoadmapStepStatus.upcoming),
            step("Repeat-offender escalation", "Wards missing the SLA three times in a quarter are flagged for corporation-level review.", RoadmapStepStatus.upcoming)
        )
    ),
    Policy(
        slug = "jal-jeevan-mission", title = "Jal Jeevan Mission — Piped Water Supply",
        category = "Water Supply",
        summary = "Guarantees functional household tap water; leaks and outages have a mandated repair window under the mission.",
        keywords = listOf("water", "pipeline", "tap", "supply", "leak", "shortage", "drink"),
        eligibility = "All households, with priority for areas lacking a functional household tap connection.",
        roadmap = listOf(
            step("Outage/leak reported", "Logged against the local Public Health Engineering division.", RoadmapStepStatus.done),
            step("Site inspection", "A field engineer assesses the leak or supply gap, usually within 48 hours.", RoadmapStepStatus.current),
            step("Repair or connection", "Pipeline repair or new household connection is carried out under the mission's funded works.", RoadmapStepStatus.upcoming),
            step("Supply restored & logged", "Restoration is logged against the village/ward's functional household tap connection count.", RoadmapStepStatus.upcoming)
        )
    ),
    Policy(
        slug = "pmgsy-road-maintenance", title = "PMGSY — Road Maintenance & Pothole SLA",
        category = "Roads & Potholes",
        summary = "Defines how fast PWD must respond to potholes and road-safety hazards on notified roads.",
        keywords = listOf("pothole", "road", "highway", "tar", "accident", "crack", "footpath"),
        eligibility = "Applies to any PWD-notified road; report the exact stretch and nearest landmark for fastest routing.",
        roadmap = listOf(
            step("Hazard reported", "Location and severity logged against the nearest PWD division.", RoadmapStepStatus.done),
            step("Risk classification", "Injury-risk and repeat-mention signals decide priority — accident-linked reports jump the queue.", RoadmapStepStatus.current),
            step("Inspection & patching", "Field inspection and patching work, typically within 5 working days for high-priority reports.", RoadmapStepStatus.upcoming),
            step("Quality re-check", "A follow-up inspection closes the case only once the patch has held through the next rain.", RoadmapStepStatus.upcoming)
        )
    ),
    Policy(
        slug = "saubhagya-electrification", title = "Saubhagya — Electrical Safety & Connections",
        category = "Electricity",
        summary = "Covers free/subsidised household electrification and safety response for exposed wiring or transformer faults.",
        keywords = listOf("electricity", "transformer", "power", "wire", "spark", "shock", "outage"),
        eligibility = "All households for safety response; free connections prioritised for below-poverty-line households.",
        roadmap = listOf(
            step("Fault reported", "Sparking, exposed wiring, or outage logged against the local Electricity Board division.", RoadmapStepStatus.done),
            step("Emergency triage", "Fire/shock-risk keywords fast-track a field engineer dispatch, often same-day.", RoadmapStepStatus.current),
            step("Repair", "Faulty equipment is repaired or replaced and the line is re-certified safe.", RoadmapStepStatus.upcoming),
            step("Connection (if applicable)", "Eligible households without a connection are enrolled for free electrification under the scheme.", RoadmapStepStatus.upcoming)
        )
    ),
    Policy(
        slug = "smart-street-lighting", title = "Smart Street Lighting Maintenance Scheme",
        category = "Street Lighting",
        summary = "Ward-level SLA for streetlight repair, prioritised by pedestrian-safety and crime-report proximity.",
        keywords = listOf("streetlight", "light", "lamp", "bulb", "dark", "night"),
        eligibility = "Any public street within municipal limits.",
        roadmap = listOf(
            step("Outage reported", "Pole number and stretch logged against the Ward Office.", RoadmapStepStatus.done),
            step("Cluster check", "Nearby reports are merged into one work order so a whole dark stretch is fixed in one visit.", RoadmapStepStatus.current),
            step("Bulb/fixture replaced", "Routine maintenance visits typically close these within 48 hours.", RoadmapStepStatus.upcoming)
        )
    )
)

/** Same stand-in as main.js's scorePolicies(). */
fun scorePolicies(query: String, policies: List<Policy> = DEMO_POLICIES): List<Pair<Policy, Int>> {
    val terms = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()
    return policies.map { p ->
        val text = "${p.title} ${p.summary} ${p.category}".lowercase()
        var score = 0
        terms.forEach { t ->
            if (p.keywords.any { it.contains(t) || t.contains(it) }) score += 3
            else if (text.contains(t)) score += 1
        }
        p to score
    }.sortedByDescending { it.second }
}
