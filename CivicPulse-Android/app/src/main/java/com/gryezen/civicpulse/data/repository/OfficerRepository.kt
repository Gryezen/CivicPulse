package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.model.AuditTrailResponse
import com.gryezen.civicpulse.data.model.BulkActionRequest
import com.gryezen.civicpulse.data.model.BulkActionResponse
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.OfficerQueueResponse
import com.gryezen.civicpulse.data.model.OfficerSummary
import com.gryezen.civicpulse.data.model.PolicySyncRequest
import com.gryezen.civicpulse.data.model.PolicySyncResponse
import com.gryezen.civicpulse.data.model.ResolveWithPhotoRequest
import com.gryezen.civicpulse.data.remote.ApiClient
import com.gryezen.civicpulse.data.remote.parseErrorMessage

/**
 * Talks to officer.py — every route here 403s server-side unless
 * current_user.isOfficial, so this is only ever reached from screens
 * already gated on User.isOfficial (see AppShell/CivicPulseNavHost). No
 * offline fallback: unlike the citizen-facing repositories, there's no
 * meaningful demo data for a triage queue an official is actually working
 * from, so failures surface as plain errors instead of silently showing
 * stale/fake counts.
 */
class OfficerRepository(private val apiClient: ApiClient) {

    suspend fun summary(): Result<OfficerSummary> = runCatching {
        val response = apiClient.service.officerSummary()
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not load the officer summary"))
        response.body() ?: error("Empty response from server")
    }

    suspend fun queue(
        broadCategory: String? = null,
        status: String? = null,
        onlyFlagged: Boolean = false,
        includeAutoResolved: Boolean = false,
        page: Int = 1,
        pageSize: Int = 50
    ): Result<OfficerQueueResponse> = runCatching {
        val response = apiClient.service.officerQueue(
            broadCategory = broadCategory,
            status = status,
            onlyFlagged = if (onlyFlagged) "1" else null,
            includeAutoResolved = if (includeAutoResolved) "1" else null,
            page = page,
            pageSize = pageSize
        )
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not load the queue"))
        response.body() ?: error("Empty response from server")
    }

    suspend fun assign(ids: List<String>, officer: String?): Result<BulkActionResponse> =
        bulk(ids, "assign", officer)

    suspend fun escalate(ids: List<String>): Result<BulkActionResponse> =
        bulk(ids, "escalate")

    suspend fun resolve(ids: List<String>): Result<BulkActionResponse> =
        bulk(ids, "resolve")

    private suspend fun bulk(ids: List<String>, action: String, officer: String? = null): Result<BulkActionResponse> = runCatching {
        val response = apiClient.service.officerBulkAction(BulkActionRequest(ids = ids, action = action, officer = officer))
        if (!response.isSuccessful) error(response.parseErrorMessage("Bulk action failed"))
        response.body() ?: error("Empty response from server")
    }

    /** after-photo as a base64 data URL — see util/FileUtils.kt's encodeFileAsImageDataUrl(). */
    suspend fun resolveWithPhoto(complaintId: String, afterPhotoDataUrl: String): Result<Complaint> = runCatching {
        val response = apiClient.service.officerResolveWithPhoto(complaintId, ResolveWithPhotoRequest(afterPhotoDataUrl))
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not resolve with photo"))
        response.body() ?: error("Empty response from server")
    }

    suspend fun auditTrail(complaintId: String): Result<AuditTrailResponse> = runCatching {
        val response = apiClient.service.officerAuditTrail(complaintId)
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not load the audit trail"))
        response.body() ?: error("Empty response from server")
    }

    suspend fun syncPolicies(source: String?): Result<PolicySyncResponse> = runCatching {
        val response = apiClient.service.officerSyncPolicies(PolicySyncRequest(source))
        if (!response.isSuccessful) error(response.parseErrorMessage("Policy sync failed"))
        response.body() ?: error("Empty response from server")
    }
}
