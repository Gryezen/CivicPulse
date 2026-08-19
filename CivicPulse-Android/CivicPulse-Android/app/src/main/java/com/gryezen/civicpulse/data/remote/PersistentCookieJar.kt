package com.gryezen.civicpulse.data.remote

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * The backend branch uses session-based Flask-Login auth (see auth.py:
 * register/login/logout/me/patch/change-password, all cookie-backed), so the
 * client needs to persist the session cookie across app restarts the same way
 * a browser would. This is a minimal SharedPreferences-backed CookieJar —
 * good enough for a single-session-cookie backend; swap for a DB-backed jar
 * later if the API grows more cookies.
 *
 * Cookies are serialized field-by-field (not via Cookie.toString(), which
 * only round-trips the name/value pair and drops domain/path/expiry) so a
 * restored session behaves identically to a freshly-issued one.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("civicpulse_cookies", Context.MODE_PRIVATE)

    private val memoryCache = mutableMapOf<String, MutableList<Cookie>>()

    init {
        val raw = prefs.getStringSet(KEY_COOKIES, emptySet()) ?: emptySet()
        raw.forEach { encoded ->
            runCatching { decode(encoded) }.getOrNull()?.let { pair ->
                val (host, cookie) = pair
                memoryCache.getOrPut(host) { mutableListOf() }.add(cookie)
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val list = memoryCache.getOrPut(host) { mutableListOf() }
        cookies.forEach { newCookie ->
            list.removeAll { it.name == newCookie.name }
            list.add(newCookie)
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val stored = memoryCache[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val valid = stored.filter { it.expiresAt > now }
        if (valid.size != stored.size) {
            memoryCache[host] = valid.toMutableList()
            persist()
        }
        return valid
    }

    fun clear() {
        memoryCache.clear()
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    private fun persist() {
        val encoded = memoryCache.flatMap { entry ->
            entry.value.map { cookie -> encode(entry.key, cookie) }
        }.toSet()
        prefs.edit().putStringSet(KEY_COOKIES, encoded).apply()
    }

    // Fields joined with a separator unlikely to appear in a cookie name/value/path.
    private fun encode(host: String, cookie: Cookie): String = listOf(
        host, cookie.name, cookie.value, cookie.domain, cookie.path,
        cookie.expiresAt.toString(), cookie.secure.toString(), cookie.httpOnly.toString()
    ).joinToString(FIELD_SEPARATOR)

    private fun decode(raw: String): Pair<String, Cookie>? {
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size != 8) return null
        val host = parts[0]
        val name = parts[1]
        val value = parts[2]
        val domain = parts[3]
        val path = parts[4]
        val expiresAt = parts[5]
        val secure = parts[6]
        val httpOnly = parts[7]
        val cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path(path)
            .expiresAt(expiresAt.toLongOrNull() ?: Long.MAX_VALUE)
            .apply { if (secure.toBoolean()) secure() }
            .apply { if (httpOnly.toBoolean()) httpOnly() }
            .build()
        return host to cookie
    }

    companion object {
        private const val KEY_COOKIES = "cookies"
        private const val FIELD_SEPARATOR = "\u0001"
    }
}
