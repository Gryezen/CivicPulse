package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.local.DEMO_DASHBOARD_COMPLAINTS
import com.gryezen.civicpulse.data.local.DEMO_DOCKETS
import com.gryezen.civicpulse.data.local.scoreDockets
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.CreateComplaintResponse
import com.gryezen.civicpulse.data.remote.ApiClient
import com.gryezen.civicpulse.data.remote.parseErrorMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

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
 */
class ComplaintRepository(private val apiClient: ApiClient) {

    suspend fun fileComplaint(complaint: NewComplaint): Result<CreateComplaintResponse> = runCatching {
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
    }

    suspend fun myComplaints(): Result<List<Complaint>> = runCatching {
        val response = apiClient.service.myComplaints()
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not load complaints"))
        response.body().orEmpty()
    }.recoverCatching { DEMO_DASHBOARD_COMPLAINTS }

    suspend fun findComplaint(docketId: String): Result<Complaint> = runCatching {
        val id = docketId.trim().uppercase()
        val response = apiClient.service.findComplaint(id)
        if (!response.isSuccessful) error(response.parseErrorMessage("Lookup failed"))
        response.body() ?: error("Empty response from server")
    }.recoverCatching {
        DEMO_DOCKETS[docketId.trim().uppercase()] ?: throw NoSuchElementException("No complaint found for that docket ID.")
    }

    /**
     * Full ranked queue, used by the Track screen's browse/filter view.
     * No queue endpoint is even sketched yet on the backend (track.html's
     * TODO(backend) comment proposes `GET /api/admin/queue?sort=...` as a
     * possibility, but nothing is wired up) — so this goes straight to the
     * same demo dataset the web app renders today.
     */
    suspend fun queue(): Result<List<Complaint>> = Result.success(DEMO_DOCKETS.values.toList())

    /** Free-text ("NLP") search across the queue — matches track.html's description search box. */
    suspend fun search(query: String): Result<List<Complaint>> = runCatching {
        val response = apiClient.service.searchComplaints(query)
        if (!response.isSuccessful) error("no search endpoint yet")
        response.body().orEmpty()
    }.recoverCatching {
        val scores = scoreDockets(query)
        DEMO_DOCKETS.values.filter { (scores[it.id] ?: 0) > 0 }.sortedByDescending { scores[it.id] ?: 0 }
    }
}
