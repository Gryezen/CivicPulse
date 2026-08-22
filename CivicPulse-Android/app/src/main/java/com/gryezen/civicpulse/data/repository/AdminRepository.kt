package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.model.User
import com.gryezen.civicpulse.data.remote.ApiClient
import com.gryezen.civicpulse.data.remote.parseErrorMessage

/**
 * Talks to admin.py — human review of officials stuck in the
 * `pending_review` verification path (no verification code, an ID
 * document photo instead). Gated server-side on User.isAdmin, a third
 * tier above official that's never self-registerable — see models.py's
 * own comments. Only ever reached from a screen gated on User.isAdmin.
 */
class AdminRepository(private val apiClient: ApiClient) {

    suspend fun pendingOfficials(): Result<List<User>> = runCatching {
        val response = apiClient.service.pendingOfficials()
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not load pending officials"))
        response.body().orEmpty()
    }

    suspend fun approve(userId: String): Result<User> = runCatching {
        val response = apiClient.service.approveOfficial(userId)
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not approve this official"))
        response.body() ?: error("Empty response from server")
    }

    suspend fun reject(userId: String): Result<User> = runCatching {
        val response = apiClient.service.rejectOfficial(userId)
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not reject this official"))
        response.body() ?: error("Empty response from server")
    }
}
