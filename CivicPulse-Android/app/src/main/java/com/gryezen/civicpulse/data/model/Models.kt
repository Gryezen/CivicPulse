package com.gryezen.civicpulse.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * These shapes mirror the fields committed on the govtheme branch:
 * - User: models.py's User.to_dict() (id/name/email/region/education/employed/occupation/language)
 * - Complaint/docket: the mockDockets object in templates/track.html — richer
 *   than the simple list in dashboard.html's mockComplaints, so this model is
 *   a superset with sensible defaults for whichever fields a given source
 *   doesn't populate (dashboard's list vs. track's full docket detail).
 * - Policy: the CP_POLICIES dataset in static/main.js (PolicyGyaan).
 *
 * None of the complaint/policy endpoints exist server-side yet — every
 * template that touches them says so explicitly ("TODO(backend): replace
 * with GET /api/...", complaint.html's classifyComplaint(), track.html's
 * scoreDockets()). See data/local/DemoData.kt for the same stand-in data
 * and logic, kept in lockstep with the web app's mocks.
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
    @SerialName("language_native") val languageNative: String = "",
    val stage: String = "received",
    val priority: Int = 40,
    val filed: String = "",
    @SerialName("filed_display") val filedDisplay: String = "",
    val files: Int = 0,
    val note: String = ""
) {
    val statusEnum: ComplaintStatus
        get() = runCatching { ComplaintStatus.valueOf(stage) }.getOrDefault(ComplaintStatus.received)
}

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
