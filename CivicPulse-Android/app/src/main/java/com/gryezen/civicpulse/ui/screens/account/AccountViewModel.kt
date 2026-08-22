package com.gryezen.civicpulse.ui.screens.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gryezen.civicpulse.data.local.PreferencesManager
import com.gryezen.civicpulse.data.model.UpdateProfileRequest
import com.gryezen.civicpulse.data.model.User
import com.gryezen.civicpulse.data.repository.AuthRepository
import kotlinx.coroutines.launch

data class AccountUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val error: String? = null,
    val savingProfile: Boolean = false,
    val savingLanguage: Boolean = false,
    val savingPassword: Boolean = false,
    val profileSaved: Boolean = false,
    val languageSaved: Boolean = false,
    val passwordSaved: Boolean = false,
    val passwordError: String? = null,
    val loggedOut: Boolean = false,
    val changingAccountType: Boolean = false,
    val accountTypeError: String? = null,
    val resendingVerification: Boolean = false,
    val resendError: String? = null,
    val resendMessage: String? = null
)

class AccountViewModel(
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    var state by mutableStateOf(AccountUiState())
        private set

    init {
        load()
    }

    fun load() {
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            authRepository.me()
                .onSuccess { state = state.copy(loading = false, user = it) }
                .onFailure { state = state.copy(loading = false, error = it.message ?: "Could not load your profile") }
        }
    }

    fun saveProfile(name: String, region: String, education: String, employed: Boolean, occupation: String, phone: String) {
        state = state.copy(savingProfile = true, profileSaved = false)
        viewModelScope.launch {
            authRepository.updateProfile(
                UpdateProfileRequest(
                    name = name.trim(),
                    region = region.trim(),
                    education = education,
                    employed = employed,
                    occupation = occupation.trim(),
                    phone = phone.trim()
                )
            ).onSuccess { state = state.copy(savingProfile = false, user = it, profileSaved = true) }
                .onFailure { state = state.copy(savingProfile = false, error = it.message) }
        }
    }

    fun saveLanguage(language: String) {
        state = state.copy(savingLanguage = true, languageSaved = false)
        viewModelScope.launch {
            authRepository.updateProfile(UpdateProfileRequest(language = language))
                .onSuccess { state = state.copy(savingLanguage = false, user = it, languageSaved = true) }
                .onFailure { state = state.copy(savingLanguage = false, error = it.message) }
        }
    }

    fun saveEmail(email: String) {
        viewModelScope.launch {
            authRepository.updateProfile(UpdateProfileRequest(email = email.trim()))
                .onSuccess { state = state.copy(user = it) }
                .onFailure { state = state.copy(error = it.message) }
        }
    }

    fun changePassword(current: String, new: String, confirm: String) {
        if (new != confirm) {
            state = state.copy(passwordError = "New passwords don't match.")
            return
        }
        if (new.length < 6) {
            state = state.copy(passwordError = "New password must be at least 6 characters.")
            return
        }
        state = state.copy(savingPassword = true, passwordError = null, passwordSaved = false)
        viewModelScope.launch {
            authRepository.changePassword(current, new)
                .onSuccess { state = state.copy(savingPassword = false, passwordSaved = true) }
                .onFailure { state = state.copy(savingPassword = false, passwordError = it.message) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            state = state.copy(loggedOut = true)
        }
    }

    /** citizen<->official — see AuthRepository.changeAccountType() for the exact rules. */
    fun changeAccountType(
        targetRole: String,
        currentPassword: String,
        employeeId: String = "",
        department: String = "",
        verificationCode: String = "",
        idDocumentDataUrl: String? = null
    ) {
        state = state.copy(changingAccountType = true, accountTypeError = null)
        viewModelScope.launch {
            authRepository.changeAccountType(targetRole, currentPassword, employeeId, department, verificationCode, idDocumentDataUrl)
                .onSuccess { state = state.copy(changingAccountType = false, user = it) }
                .onFailure { state = state.copy(changingAccountType = false, accountTypeError = it.message ?: "Could not change account type") }
        }
    }

    fun resendVerification() {
        state = state.copy(resendingVerification = true, resendError = null, resendMessage = null)
        viewModelScope.launch {
            authRepository.resendVerification()
                .onSuccess { state = state.copy(resendingVerification = false, user = it, resendMessage = "Verification request resent.") }
                .onFailure { state = state.copy(resendingVerification = false, resendError = it.message ?: "Could not resend") }
        }
    }

    fun consumeSaveFlags() {
        state = state.copy(profileSaved = false, languageSaved = false, passwordSaved = false)
    }
}
