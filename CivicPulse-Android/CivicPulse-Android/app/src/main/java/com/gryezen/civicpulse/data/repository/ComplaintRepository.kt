package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.local.DEMO_DOCKETS
import com.gryezen.civicpulse.data.local.FiledComplaintsStore
import com.gryezen.civicpulse.data.local.classifyComplaintLocally
import com.gryezen.civicpulse.data.local.scoreDockets
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.CreateComplaintRequest
import com.gryezen.civicpulse.data.model.CreateComplaintResponse
import com.gryezen.civicpulse.data.remote.ApiClient
import com.gryezen.civicpulse.data.remote.parseErrorMessage
import kotlinx.coroutines.flow.first
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
 * All three complaint endpoints are live now (see ApiService.kt) — every
 * method here tries the real call first and only falls back to the demo
 * data / local classifier (data/local/DemoData.kt) when the server can't be
 * reached, so the app still works offline.
 *
 * Two things the server doesn't have, so they're handled client-side only:
 *  - Proof files aren't actually uploaded — POST /api/complaints is JSON,
 *    not multipart (see classify.py/complaints.py). Only the *count* is
 *    sent as `files_count`; `dateFrom`/`dateTo`/`authorityLevel` aren't
 *    columns on Complaint either and are only used for the offline
 *    classifier fallback and are otherwise informational.
 *  - There's no "get complaint by docket ID" endpoint — [findComplaint]
 *    resolves against what's already been fetched (locally-filed
 *    complaints, then the queue) instead of a dedicated remote call.
 */
class ComplaintRepository(
    private val apiClient: ApiClient,
    private val filedComplaintsStore: FiledComplaintsStore
) {

    suspend fun fileComplaint(complaint: NewComplaint): Result<CreateComplaintResponse> {
        val result = runCatching {
            val response = apiClient.service.createComplaint(
                CreateComplaintRequest(
                    title = complaint.title,
                    body = complaint.body,
                    language = complaint.language,
                    filesCount = complaint.proofFiles.size
                )
            )
            if (!response.isSuccessful) error(response.parseErrorMessage("Could not file complaint"))
            val filed = response.body() ?: error("Empty response from server")
            filed to CreateComplaintResponse(
                id = filed.id,
                category = filed.category,
                department = filed.department,
                priority = filed.priority,
                language = filed.language
            )
        }.recoverCatching {
            // Offline fallback: the same client-side classifier complaint.html
            // used before the real endpoint existed, so filing still works
            // end-to-end and produces a real-looking docket.
            val classified = classifyComplaintLocally(complaint.title, complaint.body, complaint.language)
            val local = Complaint(
                id = classified.id,
                title = complaint.title,
                body = complaint.body,
                authority = complaint.authorityLevel,
                category = classified.category,
                department = classified.department,
                language = classified.language,
                stage = "received",
                priority = classified.priority,
                filed = isoDate(),
                filedDisplay = displayDate(),
                files = complaint.proofFiles.size
            )
            local to classified
        }

        return result.map { (filed, uiResponse) ->
            filedComplaintsStore.add(filed)
            uiResponse
        }
    }

    suspend fun myComplaints(): Result<List<Complaint>> {
        val remote = runCatching {
            val response = apiClient.service.myComplaints()
            if (!response.isSuccessful) error(response.parseErrorMessage("Could not load complaints"))
            response.body().orEmpty()
        }

        val filed = filedComplaintsStore.complaints.first()
        return remote.map { mergeById(filed, it) }.recoverCatching {
            mergeById(filed, DEMO_DOCKETS.values.toList())
        }
    }

    /** Resolved locally — see the class doc; no server endpoint for single-docket lookup. */
    suspend fun findComplaint(docketId: String): Result<Complaint> {
        val id = docketId.trim().uppercase()
        filedComplaintsStore.find(id)?.let { return Result.success(it) }

        val fromQueue = queue().getOrNull()?.find { it.id.equals(id, ignoreCase = true) }
        if (fromQueue != null) return Result.success(fromQueue)

        return runCatching {
            DEMO_DOCKETS[id] ?: throw NoSuchElementException("No complaint found for that docket ID.")
        }
    }

    /**
     * Full queue for the Track screen's browse/filter view — GET /api/complaints,
     * unfiltered. Requires login, same as everything else in this app.
     */
    suspend fun queue(): Result<List<Complaint>> {
        val filed = filedComplaintsStore.complaints.first()
        val remote = runCatching {
            val response = apiClient.service.queue()
            if (!response.isSuccessful) error(response.parseErrorMessage("Could not load the queue"))
            response.body().orEmpty()
        }
        return remote.map { mergeById(filed, it) }.recoverCatching {
            mergeById(filed, DEMO_DOCKETS.values.toList())
        }
    }

    /** Free-text ("NLP") search — server-side via ?q= (complaints.py's queue()). */
    suspend fun search(query: String): Result<List<Complaint>> = runCatching {
        val response = apiClient.service.queue(query = query)
        if (!response.isSuccessful) error(response.parseErrorMessage("Search failed"))
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
