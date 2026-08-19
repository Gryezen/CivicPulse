package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.local.PreferencesManager
import com.gryezen.civicpulse.data.model.ChangePasswordRequest
import com.gryezen.civicpulse.data.model.LoginRequest
import com.gryezen.civicpulse.data.model.RegisterRequest
import com.gryezen.civicpulse.data.model.UpdateProfileRequest
import com.gryezen.civicpulse.data.model.User
import com.gryezen.civicpulse.data.remote.ApiClient
import com.gryezen.civicpulse.data.remote.parseErrorMessage

/** Talks to the real, live auth.py endpoints — see ApiService.kt for the confirmed contract. */
class AuthRepository(
    private val apiClient: ApiClient,
    private val preferences: PreferencesManager
) {
    suspend fun register(request: RegisterRequest): Result<User> = runCatching {
        val response = apiClient.service.register(request)
        if (!response.isSuccessful) error(response.parseErrorMessage("Registration failed"))
        val user = response.body() ?: error("Empty response from server")
        onAuthenticated(user)
        user
    }

    suspend fun login(email: String, password: String): Result<User> = runCatching {
        val response = apiClient.service.login(LoginRequest(email, password))
        if (!response.isSuccessful) error(response.parseErrorMessage("Login failed"))
        val user = response.body() ?: error("Empty response from server")
        onAuthenticated(user)
        user
    }

    suspend fun logout(): Result<Unit> = runCatching {
        runCatching { apiClient.service.logout() }
        apiClient.clearSession()
        preferences.clearSessionState()
    }

    suspend fun me(): Result<User> = runCatching {
        val response = apiClient.service.me()
        if (!response.isSuccessful) error("Not logged in")
        val user = response.body() ?: error("Empty response from server")
        onAuthenticated(user)
        user
    }

    suspend fun updateProfile(request: UpdateProfileRequest): Result<User> = runCatching {
        val response = apiClient.service.updateProfile(request)
        if (!response.isSuccessful) error(response.parseErrorMessage("Update failed"))
        val user = response.body() ?: error("Empty response from server")
        onAuthenticated(user)
        user
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        val response = apiClient.service.changePassword(ChangePasswordRequest(currentPassword, newPassword))
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not change password"))
    }

    private suspend fun onAuthenticated(user: User) {
        preferences.setLoggedInHint(true)
        preferences.cacheIdentity(user.name, user.email)
    }
}
