package com.gryezen.civicpulse.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * These shapes mirror the fields now live on the backend (Frontend/civicpulse):
 * - User: models.py's User.to_dict() (id/name/email/region/education/employed/occupation/language)
 * - Complaint: models.py's Complaint.to_dict() — id/title/body/language/category/
 *   department/authority/priority/stage/files/note/filed/filedDisplay, plus an
 *   optional `matchLabel` the queue endpoint (GET /api/complaints) adds when a
 *   `q` search term was supplied (complaints.py's queue()).
 * - Policy: policies_data.json, served through policy_engine.py (the
 *   PolicyGyaan bridge) — slug/title/source/category/summary/keywords/
 *   eligibility/roadmap.
 *
 * `languageNative` has no server-side equivalent (there's no such column on
 * Complaint) — it's kept as a display-only, client-populated field for the
 * demo dataset (data/local/DemoData.kt); real API responses just leave it at
 * its default.
 *
 * All three complaint endpoints and both policy endpoints are live now (see
 * ApiService.kt) — data/local/DemoData.kt is kept purely as an offline
 * fallback for when the server can't be reached.
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
    val language: String = "English"
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val region: String,
    val education: String,
    val employed: Boolean,
    val occupation: String,
    val language: String
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
    val email: String? = null
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String
)

/**
 * received -> processing ("AI triage") -> assigned -> resolved.
 * Matches track.html's `stageOrder` exactly.
 */
enum class ComplaintStatus { received, processing, assigned, resolved }

@Serializable
data class Complaint(
    val id: String,
    val title: String,
    val body: String = "",
    val authority: String = "",
    val category: String = "",
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
    val matchLabel: String? = null
) {
    val statusEnum: ComplaintStatus
        get() = runCatching { ComplaintStatus.valueOf(stage) }.getOrDefault(ComplaintStatus.received)
}

/**
 * Body for POST /api/complaints (complaints.py's create_complaint()). The
 * server classifies server-side (classify.py) and returns a full Complaint —
 * there's no separate "response" shape, unlike the old mocked endpoint.
 */
@Serializable
data class CreateComplaintRequest(
    val title: String,
    val body: String,
    val language: String = "English",
    @SerialName("files_count") val filesCount: Int = 0
)

/**
 * Kept as the shape the file-complaint UI works with (id/category/department/
 * priority/language) — built from the real Complaint the server returns, or
 * straight from the offline classifier when the server can't be reached. See
 * ComplaintRepository.fileComplaint().
 */
@Serializable
data class CreateComplaintResponse(
    val id: String,
    val category: String = "General Grievance",
    val department: String = "",
    val priority: Int = 40,
    val language: String = "English"
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
    val roadmap: List<RoadmapStep> = emptyList()
)

/** Generic wrapper the ViewModels expose to Compose screens. */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
