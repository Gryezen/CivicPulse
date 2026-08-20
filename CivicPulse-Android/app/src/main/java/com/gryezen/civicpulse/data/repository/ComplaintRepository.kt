package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.local.DEMO_DASHBOARD_COMPLAINTS
import com.gryezen.civicpulse.data.local.DEMO_DOCKETS
import com.gryezen.civicpulse.data.local.FiledComplaintsStore
import com.gryezen.civicpulse.data.local.classifyComplaintLocally
import com.gryezen.civicpulse.data.local.scoreDockets
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.CreateComplaintResponse
import com.gryezen.civicpulse.data.remote.ApiClient
import com.gryezen.civicpulse.data.remote.parseErrorMessage
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NewComplaint(
    val title: String,
    val dateFrom: String,
    val dateTo: String,
    val authorityLevel: String,
    val language: String,
    val body: String,
    val proofFiles: List<File> = emptyList()
)

/**
 * None of the complaint endpoints exist server-side yet (see ApiService.kt's
 * doc comment) — every method here tries the real call first, and falls back
 * to the same demo data / local scoring the web app mocks
 * (data/local/DemoData.kt) on any failure, so the app stays fully usable.
 *
 * Complaints actually filed on this device are additionally persisted via
 * [filedComplaintsStore] and merged on top of the demo/remote lists, so
 * filing a complaint isn't a dead end while there's no real list endpoint —
 * see FiledComplaintsStore's doc comment.
 */
class ComplaintRepository(
    private val apiClient: ApiClient,
    private val filedComplaintsStore: FiledComplaintsStore
) {

    suspend fun fileComplaint(complaint: NewComplaint): Result<CreateComplaintResponse> {
        val result = runCatching {
            val textType = "text/plain".toMediaType()
            val parts = complaint.proofFiles.map { file ->
                val guessedType = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "mp4" -> "video/mp4"
                    "wav" -> "audio/wav"
                    else -> "application/octet-stream"
                }.toMediaType()
                MultipartBody.Part.createFormData(
                    "proof_of_complaints",
                    file.name,
                    file.asRequestBody(guessedType)
                )
            }

            val response = apiClient.service.createComplaint(
                title = complaint.title.toRequestBody(textType),
                dateFrom = complaint.dateFrom.toRequestBody(textType),
                dateTo = complaint.dateTo.toRequestBody(textType),
                authorityLevel = complaint.authorityLevel.toRequestBody(textType),
                language = complaint.language.toRequestBody(textType),
                body = complaint.body.toRequestBody(textType),
                proofFiles = parts
            )
            if (!response.isSuccessful) error(response.parseErrorMessage("Could not file complaint"))
            response.body() ?: error("Empty response from server")
        }.recoverCatching {
            // POST /api/create/complaint isn't live yet — fall back to the
            // same client-side classifier complaint.html uses, so filing
            // still works end-to-end and produces a real-looking docket.
            classifyComplaintLocally(complaint.title, complaint.body, complaint.language)
        }

        result.onSuccess { response ->
            filedComplaintsStore.add(
                Complaint(
                    id = response.id,
                    title = complaint.title,
                    body = complaint.body,
                    authority = complaint.authorityLevel,
                    category = response.category,
                    department = response.department,
                    language = response.language,
                    stage = "received",
                    priority = response.priority,
                    filed = isoDate(),
                    filedDisplay = displayDate(),
                    files = complaint.proofFiles.size
                )
            )
        }
        return result
    }

    suspend fun myComplaints(): Result<List<Complaint>> {
        val remote = runCatching {
            val response = apiClient.service.myComplaints()
            if (!response.isSuccessful) error(response.parseErrorMessage("Could not load complaints"))
            response.body().orEmpty()
        }.getOrElse { DEMO_DASHBOARD_COMPLAINTS }

        val filed = filedComplaintsStore.complaints.first()
        return Result.success(mergeById(filed, remote))
    }

    suspend fun findComplaint(docketId: String): Result<Complaint> {
        val id = docketId.trim().uppercase()
        filedComplaintsStore.find(id)?.let { return Result.success(it) }

        return runCatching {
            val response = apiClient.service.findComplaint(id)
            if (!response.isSuccessful) error(response.parseErrorMessage("Lookup failed"))
            response.body() ?: error("Empty response from server")
        }.recoverCatching {
            DEMO_DOCKETS[id] ?: throw NoSuchElementException("No complaint found for that docket ID.")
        }
    }

    /**
     * Full ranked queue, used by the Track screen's browse/filter view.
     * No queue endpoint is even sketched yet on the backend (track.html's
     * TODO(backend) comment proposes `GET /api/admin/queue?sort=...` as a
     * possibility, but nothing is wired up) — so this is the demo dataset
     * plus whatever's actually been filed on this device.
     */
    suspend fun queue(): Result<List<Complaint>> {
        val filed = filedComplaintsStore.complaints.first()
        return Result.success(mergeById(filed, DEMO_DOCKETS.values.toList()))
    }

    /** Free-text ("NLP") search across the queue — matches track.html's description search box. */
    suspend fun search(query: String): Result<List<Complaint>> = runCatching {
        val response = apiClient.service.searchComplaints(query)
        if (!response.isSuccessful) error("no search endpoint yet")
        response.body().orEmpty()
    }.recoverCatching {
        val all = queue().getOrDefault(DEMO_DOCKETS.values.toList()).associateBy { it.id }
        val scores = scoreDockets(query, all)
        all.values.filter { (scores[it.id] ?: 0) > 0 }.sortedByDescending { scores[it.id] ?: 0 }
    }

    /** Locally-filed entries win on id collision (unlikely, but they're the freshest truth). */
    private fun mergeById(primary: List<Complaint>, secondary: List<Complaint>): List<Complaint> {
        val primaryIds = primary.map { it.id }.toSet()
        return primary + secondary.filter { it.id !in primaryIds }
    }

    private fun isoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun displayDate(): String = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
}
