package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.local.DEMO_POLICIES
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.data.remote.ApiClient

/**
 * GET /api/policies and GET /api/policies/<slug> are live now — served from
 * policies_data.json via policy_engine.py's PolicyGyaan bridge (app.py),
 * with Gemini-ranked-or-keyword-scored recommendations baked in server-side.
 * Falls back to the bundled demo list (data/local/DemoData.kt) on any
 * failure so the app stays usable offline.
 */
class PolicyRepository(private val apiClient: ApiClient) {

    suspend fun recommended(): Result<List<Policy>> = runCatching {
        val response = apiClient.service.policies()
        if (!response.isSuccessful) error("no policies endpoint yet")
        response.body().orEmpty().takeIf { it.isNotEmpty() } ?: error("empty")
    }.recoverCatching { DEMO_POLICIES }

    suspend fun bySlug(slug: String): Result<Policy> = runCatching {
        val response = apiClient.service.policy(slug)
        if (!response.isSuccessful) error("no policy endpoint yet")
        response.body() ?: error("empty")
    }.recoverCatching {
        DEMO_POLICIES.find { it.slug == slug } ?: throw NoSuchElementException("That policy link may be out of date.")
    }
}
