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
 * `GET /api/admin/view/complaint` isn't live yet (see ApiService.kt), so
 * without this, filing a complaint would be a dead end — the confirmation
 * screen would show a docket ID that then vanishes, since the dashboard and
 * queue only ever render the static demo data. This stores what the person
 * has actually filed on this device (JSON-encoded, DataStore-backed, so it
 * survives app restarts) and repositories merge it on top of the demo/real
 * data. Once the real list endpoint exists, the server becomes the source of
 * truth and this becomes a pure offline cache instead of the only record.
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
