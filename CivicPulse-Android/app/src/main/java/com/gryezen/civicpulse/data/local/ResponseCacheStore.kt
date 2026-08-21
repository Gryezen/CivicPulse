package com.gryezen.civicpulse.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.responseCacheDataStore by preferencesDataStore(name = "civicpulse_response_cache")

/**
 * Caches the last successful response for a given remote list call (the
 * complaints queue, "my complaints", the policy catalogue) on disk, so the
 * app has something real to show the moment a screen opens — instead of
 * either a blank spinner or the bundled demo data — while a fresh network
 * request runs in the background.
 *
 * This matters most on slow/2G connections (see the "poor wifi" thread):
 * caching doesn't make the network faster, but it means a citizen on a bad
 * connection sees their last-synced complaints/policies immediately rather
 * than staring at a spinner for 20+ seconds, and the screen updates quietly
 * if/when the refresh succeeds. Pair with server-side gzip (app.py) and
 * offline-queued writes (FiledComplaintsStore) — caching reads is only one
 * piece of the fix, not the whole thing.
 */
class ResponseCacheStore(context: Context) {

    private val dataStore = context.responseCacheDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private fun dataKey(cacheKey: String) = stringPreferencesKey("cache_${cacheKey}_data")
    private fun timeKey(cacheKey: String) = longPreferencesKey("cache_${cacheKey}_at")

    /** Null if nothing has ever been cached for this key. */
    suspend fun <T> get(cacheKey: String, serializer: KSerializer<T>): CachedValue<T>? {
        val prefs = dataStore.data.first()
        val raw = prefs[dataKey(cacheKey)] ?: return null
        val savedAt = prefs[timeKey(cacheKey)] ?: 0L
        val value = runCatching { json.decodeFromString(serializer, raw) }.getOrNull() ?: return null
        return CachedValue(value, savedAt)
    }

    suspend fun <T> put(cacheKey: String, serializer: KSerializer<T>, value: T) {
        dataStore.edit { prefs ->
            prefs[dataKey(cacheKey)] = json.encodeToString(serializer, value)
            prefs[timeKey(cacheKey)] = System.currentTimeMillis()
        }
    }
}

data class CachedValue<T>(val value: T, val savedAtMillis: Long)
