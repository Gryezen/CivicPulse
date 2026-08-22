package com.gryezen.civicpulse.ui.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gryezen.civicpulse.data.model.RegisterRequest
import com.gryezen.civicpulse.data.repository.AuthRepository
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    var state by mutableStateOf(AuthUiState())
        private set

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            state = state.copy(error = "Enter your email and password.")
            return
        }
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            authRepository.login(email.trim(), password)
                .onSuccess { state = state.copy(loading = false, success = true) }
                .onFailure { state = state.copy(loading = false, error = it.message ?: "Login failed") }
        }
    }

    fun register(
        name: String,
        email: String,
        password: String,
        region: String,
        education: String,
        employed: Boolean,
        occupation: String,
        language: String,
        role: String = "citizen",
        employeeId: String = "",
        department: String = "",
        verificationCode: String = "",
        idDocumentDataUrl: String? = null
    ) {
        if (name.isBlank() || email.isBlank() || password.length < 6 || region.isBlank() || education.isBlank()) {
            state = state.copy(error = "Please fill in all required fields (password needs 6+ characters).")
            return
        }
        if (role == "official" && (employeeId.isBlank() || department.isBlank())) {
            state = state.copy(error = "Employee ID and department are required for an official account.")
            return
        }
        if (role == "official" && verificationCode.isBlank() && idDocumentDataUrl == null) {
            state = state.copy(error = "Enter your department's verification code, or attach an ID document photo for manual review.")
            return
        }
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            authRepository.register(
                RegisterRequest(
                    name = name.trim(),
                    email = email.trim(),
                    password = password,
                    region = region.trim(),
                    education = education,
                    employed = employed,
                    occupation = occupation.trim(),
                    language = language,
                    role = role,
                    employeeId = employeeId.trim().ifBlank { null },
                    department = department.trim().ifBlank { null },
                    verificationCode = verificationCode.trim().ifBlank { null },
                    idDocument = idDocumentDataUrl
                )
            ).onSuccess { state = state.copy(loading = false, success = true) }
                .onFailure { state = state.copy(loading = false, error = it.message ?: "Registration failed") }
        }
    }

    fun consumeError() {
        state = state.copy(error = null)
    }
}
