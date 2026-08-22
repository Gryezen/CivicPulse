package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.model.IvrDemoRequest
import com.gryezen.civicpulse.data.remote.ApiClient
import com.gryezen.civicpulse.data.remote.parseErrorMessage

/**
 * Talks to ivr.py's POST /api/ivr/demo — a logged-in stand-in for the real,
 * gateway-agnostic /webhook/ivr/inbound (no telecom account involved on
 * either side; see ivr.py's module docstring for what this honestly is and
 * isn't). Same STATUS / STATUS <id-fragment> / HELP / CONFIRM / DISPUTE
 * command parsing a real SMS or IVR call would hit.
 */
class IvrRepository(private val apiClient: ApiClient) {

    suspend fun send(phone: String, text: String): Result<String> = runCatching {
        val response = apiClient.service.ivrDemo(IvrDemoRequest(phone = phone, text = text))
        if (!response.isSuccessful) error(response.parseErrorMessage("The demo endpoint didn't respond"))
        response.body()?.reply ?: error("Empty response from server")
    }
}
