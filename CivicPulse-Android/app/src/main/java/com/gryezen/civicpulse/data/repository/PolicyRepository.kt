package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.local.DEMO_POLICIES
import com.gryezen.civicpulse.data.local.ResponseCacheStore
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.data.remote.ApiClient
import kotlinx.serialization.builtins.ListSerializer

private const val CACHE_KEY_POLICIES = "policies_recommended"

/**
 * GET /api/policies and GET /api/policies/<slug> are live now — served from
 * policies_data.json via policy_engine.py's PolicyGyaan bridge (app.py),
 * with Gemini-ranked-or-keyword-scored recommendations baked in server-side.
 *
 * Cache-first: [cachedRecommended] reads the last successful response from
 * disk instantly (no network round-trip), so a ViewModel can paint real
 * data the moment a screen opens instead of a blank spinner — this matters
 * a lot on slow/2G connections where a round-trip can take 10-20+ seconds.
 * [recommended] then does the real network call, updates the cache on
 * success, and only falls back to the bundled demo list if there's truly
 * nothing else to show (first launch, never been online).
 */
class PolicyRepository(
    private val apiClient: ApiClient,
    private val responseCacheStore: ResponseCacheStore
) {

    /** Instant, synchronous local read — call this first to paint before [recommended] resolves. */
    suspend fun cachedRecommended(): List<Policy>? =
        responseCacheStore.get(CACHE_KEY_POLICIES, ListSerializer(Policy.serializer()))?.value

    suspend fun recommended(): Result<List<Policy>> {
        val remote = runCatching {
            val response = apiClient.service.policies()
            if (!response.isSuccessful) error("no policies endpoint yet")
            response.body().orEmpty().takeIf { it.isNotEmpty() } ?: error("empty")
        }

        remote.onSuccess { responseCacheStore.put(CACHE_KEY_POLICIES, ListSerializer(Policy.serializer()), it) }

        return remote.recoverCatching {
            cachedRecommended() ?: DEMO_POLICIES
        }
    }

    suspend fun bySlug(slug: String): Result<Policy> = runCatching {
        val response = apiClient.service.policy(slug)
        if (!response.isSuccessful) error("no policy endpoint yet")
        response.body() ?: error("empty")
    }.recoverCatching {
        // A single policy isn't worth its own cache entry — fall back to
        // whatever's in the last-cached recommended list (covers the common
        // case of tapping into detail right after browsing offline), then demo.
        (cachedRecommended().orEmpty() + DEMO_POLICIES).find { it.slug == slug }
            ?: throw NoSuchElementException("That policy link may be out of date.")
    }
}
