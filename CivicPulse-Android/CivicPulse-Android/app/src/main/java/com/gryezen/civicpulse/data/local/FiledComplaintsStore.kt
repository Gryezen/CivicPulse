package com.gryezen.civicpulse.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gryezen.civicpulse.data.model.Complaint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.filedComplaintsDataStore by preferencesDataStore(name = "civicpulse_filed_complaints")

/**
 * `GET /api/complaints/mine` is live now (see ApiService.kt), so the server
 * is the source of truth for a signed-in citizen's filings. This store stays
 * around as an offline cache/fallback: it lets a just-filed complaint show
 * up immediately (before a refresh round-trips to the server) and keeps the
 * app usable when the server can't be reached. Repositories merge it on top
 * of the remote/demo data, with locally-filed entries winning on id
 * collision (see ComplaintRepository.mergeById).
 */
class FiledComplaintsStore(context: Context) {

    private val dataStore = context.filedComplaintsDataStore
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("filed_complaints_json")

    /** Newest first. */
    val complaints: Flow<List<Complaint>> = dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<Complaint>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun add(complaint: Complaint) {
        dataStore.edit { prefs ->
            val existing = prefs[key]
                ?.let { runCatching { json.decodeFromString<List<Complaint>>(it) }.getOrDefault(emptyList()) }
                .orEmpty()
            val updated = listOf(complaint) + existing.filter { it.id != complaint.id }
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun find(docketId: String): Complaint? = complaints.first().find { it.id.equals(docketId, ignoreCase = true) }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }
}
