package com.gryezen.civicpulse.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gryezen.civicpulse.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "civicpulse_settings")

/**
 * Holds the two bits of state that need to survive process death but should
 * NOT be treated as the source of truth for auth (the session cookie owned by
 * [com.gryezen.civicpulse.data.remote.PersistentCookieJar] is that source).
 *
 * - baseUrl: configurable from Account > Server settings, since the Render /
 *   Supabase backend URL is still being finalized on the other branch.
 * - isLoggedInHint: lets the UI decide instantly whether to show the login
 *   screen or the app shell before the first /api/user/me round trip returns.
 */
class PreferencesManager(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val LOGGED_IN_HINT = booleanPreferencesKey("logged_in_hint")
        val CACHED_NAME = stringPreferencesKey("cached_name")
        val CACHED_EMAIL = stringPreferencesKey("cached_email")
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.BASE_URL]?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_BASE_URL
    }

    val isLoggedInHint: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOGGED_IN_HINT] ?: false
    }

    val cachedDisplayName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_NAME]?.takeIf { it.isNotBlank() } ?: (prefs[Keys.CACHED_EMAIL] ?: "Citizen")
    }

    suspend fun setBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.dataStore.edit { it[Keys.BASE_URL] = normalized }
    }

    suspend fun setLoggedInHint(loggedIn: Boolean) {
        context.dataStore.edit { it[Keys.LOGGED_IN_HINT] = loggedIn }
    }

    suspend fun cacheIdentity(name: String, email: String) {
        context.dataStore.edit {
            it[Keys.CACHED_NAME] = name
            it[Keys.CACHED_EMAIL] = email
        }
    }

    suspend fun clearSessionState() {
        context.dataStore.edit {
            it[Keys.LOGGED_IN_HINT] = false
            it.remove(Keys.CACHED_NAME)
            it.remove(Keys.CACHED_EMAIL)
        }
    }
}
