package com.gryezen.civicpulse.data.repository

import com.gryezen.civicpulse.data.local.PreferencesManager
import com.gryezen.civicpulse.data.model.AccountTypeChangeRequest
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

    /**
     * citizen<->official (never "admin" — see admin.py's own docstring on
     * why that tier is DB/seed-only). Needs a password re-check since
     * it's a privilege change, not a profile edit — see auth.py's
     * change_account_type().
     */
    suspend fun changeAccountType(
        targetRole: String,
        currentPassword: String,
        employeeId: String? = null,
        department: String? = null,
        verificationCode: String? = null,
        idDocumentDataUrl: String? = null
    ): Result<User> = runCatching {
        val response = apiClient.service.changeAccountType(
            AccountTypeChangeRequest(
                targetRole = targetRole,
                currentPassword = currentPassword,
                employeeId = employeeId?.ifBlank { null },
                department = department?.ifBlank { null },
                verificationCode = verificationCode?.ifBlank { null },
                idDocument = idDocumentDataUrl
            )
        )
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not change account type"))
        val user = response.body() ?: error("Empty response from server")
        onAuthenticated(user)
        user
    }

    /** Re-signals an admin for a pending_review official account — rate-limited to once/24h server-side. */
    suspend fun resendVerification(): Result<User> = runCatching {
        val response = apiClient.service.resendVerification()
        if (!response.isSuccessful) error(response.parseErrorMessage("Could not resend verification request"))
        val user = response.body() ?: error("Empty response from server")
        onAuthenticated(user)
        user
    }

    private suspend fun onAuthenticated(user: User) {
        preferences.setLoggedInHint(true)
        preferences.cacheIdentity(user.name, user.email)
    }
}
