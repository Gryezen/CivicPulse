package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.local.DEMO_POLICIES
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.data.remote.ApiClient

/**
 * GET /api/policies/ and GET /api/policies/<slug> aren't built yet — the web
 * app's whole PolicyGyaan dataset (CP_POLICIES) is still a hardcoded array in
 * static/main.js. Falls back to the same list here (data/local/DemoData.kt)
 * on any failure.
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
