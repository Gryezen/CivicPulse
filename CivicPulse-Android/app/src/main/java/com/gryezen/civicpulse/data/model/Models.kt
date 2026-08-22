package com.gryezen.civicpulse.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * These shapes mirror the fields now live on the backend (Frontend/civicpulse):
 * - User: models.py's User.to_dict() — full profile plus role/verification
 *   fields (role/isVerified/isOfficial/isAdmin/employeeId/department/
 *   verificationStatus/hasIdDocument/idDocumentUrl), added so the app can
 *   tell a citizen from an official from an admin the same way the website
 *   does, and gate the officer/admin screens client-side (server still
 *   enforces this for real via _official_required/_admin_required).
 * - Complaint: models.py's Complaint.to_dict() — every field the officer
 *   queue, citizen track view, and two-party closure flow depend on
 *   (confidence/flags/clustering/splitting/dispute/photo-verification),
 *   not just the original citizen-facing subset.
 * - Policy: policies_data.json, served through policy_engine.py (the
 *   PolicyGyaan bridge) — slug/title/source/category/summary/keywords/
 *   eligibility/roadmap.
 *
 * `languageNative` has no server-side equivalent (there's no such column on
 * Complaint) — it's kept as a display-only, client-populated field for the
 * demo dataset (data/local/DemoData.kt); real API responses just leave it at
 * its default.
 */

@Serializable
data class User(
    val id: String? = null,
    val name: String = "",
    val email: String = "",
    val region: String = "",
    val education: String = "",
    val employed: Boolean = true,
    val occupation: String = "",
    val language: String = "English",
    val phone: String = "",
    val role: String = "citizen",
    @SerialName("isVerified") val isVerified: Boolean = false,
    @SerialName("isOfficial") val isOfficial: Boolean = false,
    @SerialName("isAdmin") val isAdmin: Boolean = false,
    @SerialName("employeeId") val employeeId: String = "",
    val department: String = "",
    @SerialName("verificationStatus") val verificationStatus: String = "none",
    @SerialName("verificationRequestedAt") val verificationRequestedAt: String? = null,
    @SerialName("hasIdDocument") val hasIdDocument: Boolean = false,
    @SerialName("idDocumentUrl") val idDocumentUrl: String? = null
)

/**
 * Body for POST /api/auth/register (auth.py). Official self-registration
 * (role="official") additionally needs employeeId + department, plus EITHER
 * a verificationCode (fast-track, instant is_verified) OR an idDocument
 * (base64 data URL, queued for an admin to review — see admin.py). Both
 * fields are optional/blank for a plain citizen registration.
 */
@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val region: String,
    val education: String,
    val employed: Boolean,
    val occupation: String,
    val language: String,
    val role: String = "citizen",
    @SerialName("employee_id") val employeeId: String? = null,
    val department: String? = null,
    @SerialName("verification_code") val verificationCode: String? = null,
    @SerialName("id_document") val idDocument: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val region: String? = null,
    val education: String? = null,
    val employed: Boolean? = null,
    val occupation: String? = null,
    val language: String? = null,
    val email: String? = null,
    val phone: String? = null
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String
)

/**
 * Body for POST /api/user/me/account-type (auth.py's change_account_type()).
 * Switches citizen<->official; needs a password re-check since it's a
 * privilege change, not a profile edit. Switching TO "official" reuses the
 * same EITHER verificationCode OR idDocument rule as registration.
 */
@Serializable
data class AccountTypeChangeRequest(
    @SerialName("target_role") val targetRole: String,
    @SerialName("current_password") val currentPassword: String,
    @SerialName("employee_id") val employeeId: String? = null,
    val department: String? = null,
    @SerialName("verification_code") val verificationCode: String? = null,
    @SerialName("id_document") val idDocument: String? = null
)

/**
 * received -> processing ("AI triage") -> assigned -> pendingConfirmation
 * ("an official says it's fixed, waiting on the citizen") -> resolved.
 * pendingConfirmation is the two-party closure state (ideation doc gap #6,
 * see complaints.py's /confirm and /dispute) — matches track.html's
 * `stageOrder` plus that one extra stage the web build also renders.
 */
enum class ComplaintStatus { received, processing, assigned, pending_confirmation, resolved }

@Serializable
data class Complaint(
    val id: String,
    val title: String,
    val body: String = "",
    val authority: String = "",
    val category: String = "",
    @SerialName("broadCategory") val broadCategory: String = "General Governance",
    val department: String = "",
    val language: String = "English",
    // No server column for this — display-only, populated by the demo
    // dataset (data/local/DemoData.kt). Real API responses leave it "".
    @SerialName("language_native") val languageNative: String = "",
    val stage: String = "received",
    val priority: Int = 40,
    val filed: String = "",
    // Matches Complaint.to_dict()'s camelCase "filedDisplay" key exactly
    // (models.py) — this is NOT snake_case on the wire.
    val filedDisplay: String = "",
    val files: Int = 0,
    val note: String = "",
    // Only present when GET /api/complaints was called with ?q= — see
    // complaints.py's queue(), which adds this per-result.
    val matchLabel: String? = null,

    // --- classify.py v2 / triage fields --------------------------------
    val confidence: Double? = null,
    @SerialName("needsReview") val needsReview: Boolean = false,
    @SerialName("corruptionFlag") val corruptionFlag: Boolean = false,
    @SerialName("threatFlag") val threatFlag: Boolean = false,
    @SerialName("auditTier") val auditTier: Boolean = false,
    // Buried-distress signal (a coarse, high-precision-low-recall phrase
    // check — not a clinical assessment). When set, the complaint is held
    // for a human to check in on. Never surface this as a diagnosis in
    // the UI — see classify.py's _detect_wellbeing_risk() docstring.
    @SerialName("wellbeingRisk") val wellbeingRisk: Boolean = false,
    @SerialName("autoResolved") val autoResolved: Boolean = false,
    @SerialName("aiBrief") val aiBrief: String = "",
    @SerialName("assignedOfficer") val assignedOfficer: String = "",
    @SerialName("modeledSeverity") val modeledSeverity: Int? = null,
    @SerialName("statedUrgency") val statedUrgency: Int? = null,

    // --- clustering.py: corroboration / astroturf detection -------------
    @SerialName("clusterId") val clusterId: String? = null,
    @SerialName("corroborationCount") val corroborationCount: Int = 1,
    @SerialName("isRepeatFiling") val isRepeatFiling: Boolean = false,
    @SerialName("suspectedCoordinated") val suspectedCoordinated: Boolean = false,
    // Same-filer repeated-targeting pattern (e.g. a shopkeeper filing
    // fake complaints against a rival every festival season) — distinct
    // from suspectedCoordinated, which is about a burst of near-identical
    // submissions from DIFFERENT filers around the same time.
    @SerialName("suspectedTargeting") val suspectedTargeting: Boolean = false,

    // --- splitting.py: multi-issue complaints ----------------------------
    @SerialName("bundleId") val bundleId: String? = null,
    @SerialName("unverifiedAllegation") val unverifiedAllegation: Boolean = false,
    // Present only on the immediate response to POST /api/complaints when
    // classify.py's splitter decided this filing described more than one
    // issue — the other sub-issues it was split into. Not part of
    // Complaint.to_dict() on ordinary GETs, so it's always empty there.
    val wasSplit: Boolean = false,
    val splitInto: List<Complaint> = emptyList(),

    // --- two-party closure with photo verification (uploads.py) ---------
    @SerialName("disputeCount") val disputeCount: Int = 0,
    @SerialName("beforePhotoUrl") val beforePhotoUrl: String? = null,
    @SerialName("afterPhotoUrl") val afterPhotoUrl: String? = null,
    @SerialName("photoSimilarity") val photoSimilarity: Double? = null,
    @SerialName("pendingConfirmation") val pendingConfirmation: Boolean = false,
    @SerialName("citizenConfirmedAt") val citizenConfirmedAt: String? = null
) {
    val statusEnum: ComplaintStatus
        get() = runCatching { ComplaintStatus.valueOf(stage) }.getOrDefault(ComplaintStatus.received)
}

/**
 * Body for POST /api/complaints (complaints.py's create_complaint()). The
 * server classifies server-side (classify.py) and returns a full Complaint
 * (potentially with wasSplit/splitInto attached) — there's no separate
 * "response" shape, unlike the old mocked endpoint. beforePhoto is an
 * optional base64 data URL (e.g. "data:image/jpeg;base64,...") — see
 * uploads.py's save_upload() for the accepted formats/size limit.
 */
@Serializable
data class CreateComplaintRequest(
    val title: String,
    val body: String,
    val language: String = "English",
    @SerialName("files_count") val filesCount: Int = 0,
    @SerialName("before_photo") val beforePhoto: String? = null
)

/**
 * Kept as the shape the file-complaint UI works with (id/category/department/
 * priority/language/wasSplit/splitInto) — built from the real Complaint the
 * server returns, or straight from the offline classifier when the server
 * can't be reached. See ComplaintRepository.fileComplaint().
 */
@Serializable
data class CreateComplaintResponse(
    val id: String,
    val category: String = "General Grievance",
    val department: String = "",
    val priority: Int = 40,
    val language: String = "English",
    val wasSplit: Boolean = false,
    val splitInto: List<Complaint> = emptyList()
)

@Serializable
data class DashboardStats(
    val total: Int = 0,
    val received: Int = 0,
    val processing: Int = 0,
    val resolved: Int = 0
) {
    companion object {
        fun from(complaints: List<Complaint>) = DashboardStats(
            total = complaints.size,
            received = complaints.count { it.statusEnum == ComplaintStatus.received },
            processing = complaints.count { it.statusEnum == ComplaintStatus.processing },
            resolved = complaints.count { it.statusEnum == ComplaintStatus.resolved }
        )
    }
}

enum class RoadmapStepStatus { done, current, upcoming }

@Serializable
data class RoadmapStep(
    val phase: String,
    val detail: String,
    val status: RoadmapStepStatus
)

/** Mirrors CP_POLICIES entries in static/main.js — PolicyGyaan's dataset. */
@Serializable
data class Policy(
    val slug: String,
    val title: String,
    val source: String = "PolicyGyaan",
    val category: String = "",
    val summary: String = "",
    val keywords: List<String> = emptyList(),
    val eligibility: String = "",
    val roadmap: List<RoadmapStep> = emptyList(),
    // Populated when this policy came from policy_ingest.py's sync
    // instead of the hand-edited policies_data.json seed — empty/null
    // for hand-seeded entries.
    @SerialName("sourceUrl") val sourceUrl: String = "",
    @SerialName("lastSyncedAt") val lastSyncedAt: String? = null
)

// ------------------------------------------------------------------------
// SMS/IVR demo (ivr.py) — POST /api/ivr/demo. Exercises the same
// handle_inbound() command logic (STATUS, STATUS <id-fragment>, HELP)
// a real telecom gateway webhook would call, without needing an actual
// carrier account — matches templates/sms-demo.html's chat widget.
// ------------------------------------------------------------------------

@Serializable
data class IvrDemoRequest(val phone: String, val text: String)

@Serializable
data class IvrDemoResponse(val reply: String)

// ------------------------------------------------------------------------
// Officer dashboard (officer.py) — GET /api/officer/summary, /queue, etc.
// ------------------------------------------------------------------------

@Serializable
data class StageCounts(
    val received: Int = 0,
    val processing: Int = 0,
    val assigned: Int = 0,
    @SerialName("pending_confirmation") val pendingConfirmation: Int = 0,
    val resolved: Int = 0
)

@Serializable
data class SystemicAlert(
    val department: String,
    @SerialName("recentCount") val recentCount: Int,
    @SerialName("baselineAverage") val baselineAverage: Double,
    @SerialName("deviationRatio") val deviationRatio: Double
)

@Serializable
data class OfficerSummary(
    val total: Int = 0,
    @SerialName("byStage") val byStage: StageCounts = StageCounts(),
    val unresolved: Int = 0,
    @SerialName("needsReview") val needsReview: Int = 0,
    @SerialName("corruptionFlag") val corruptionFlag: Int = 0,
    @SerialName("threatFlag") val threatFlag: Int = 0,
    @SerialName("auditTier") val auditTier: Int = 0,
    @SerialName("wellbeingRisk") val wellbeingRisk: Int = 0,
    @SerialName("autoResolved") val autoResolved: Int = 0,
    @SerialName("autoResolvedShareOfHandled") val autoResolvedShareOfHandled: Double = 0.0,
    @SerialName("byBroadCategory") val byBroadCategory: Map<String, Int> = emptyMap(),
    @SerialName("systemicAlerts") val systemicAlerts: List<SystemicAlert> = emptyList()
)

@Serializable
data class OfficerQueueResponse(
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 50,
    val items: List<Complaint> = emptyList()
)

/** Body for POST /api/officer/bulk. `officer` is only used for action="assign". */
@Serializable
data class BulkActionRequest(
    val ids: List<String>,
    val action: String,
    val officer: String? = null
)

@Serializable
data class BulkActionResponse(val updated: Int = 0)

/** Body for POST /api/officer/complaints/{id}/resolve-with-photo. */
@Serializable
data class ResolveWithPhotoRequest(
    @SerialName("after_photo") val afterPhoto: String
)

@Serializable
data class ClassificationLogEntry(
    val id: String,
    @SerialName("complaintId") val complaintId: String,
    val category: String,
    val department: String,
    val priority: Int,
    val confidence: Double? = null,
    @SerialName("corruptionFlag") val corruptionFlag: Boolean = false,
    @SerialName("threatFlag") val threatFlag: Boolean = false,
    @SerialName("modelSource") val modelSource: String = "rules",
    @SerialName("createdAt") val createdAt: String = ""
)

@Serializable
data class AutoResolutionLogEntry(
    val id: String,
    @SerialName("complaintId") val complaintId: String,
    @SerialName("actionTaken") val actionTaken: Boolean = false,
    val reason: String = "",
    @SerialName("matchedComplaintId") val matchedComplaintId: String? = null,
    val similarity: Double? = null,
    @SerialName("createdAt") val createdAt: String = ""
)

@Serializable
data class AuditTrailResponse(
    val classificationLogs: List<ClassificationLogEntry> = emptyList(),
    val autoResolutionLogs: List<AutoResolutionLogEntry> = emptyList()
)

@Serializable
data class PolicySyncRequest(val source: String? = null)

/** Loosely-typed — policy_ingest.py's run_sync() result shape isn't pinned down server-side. */
@Serializable
data class PolicySyncResponse(
    val added: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0
)

/** Generic wrapper the ViewModels expose to Compose screens. */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
