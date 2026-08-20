package com.gryezen.civicpulse.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

/**
 * auth.py's `_err()` returns `{"error": "message"}` with a 4xx/5xx status —
 * see auth.py's module docstring. This pulls that message out for display,
 * falling back to a generic one if the body is missing or not JSON.
 */
private val errorJson = Json { ignoreUnknownKeys = true }

fun Response<*>.parseErrorMessage(fallback: String = "Request failed (${code()})"): String {
    val raw = runCatching { errorBody()?.string() }.getOrNull()
    if (raw.isNullOrBlank()) return fallback
    return runCatching {
        errorJson.parseToJsonElement(raw).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull() ?: fallback
}
