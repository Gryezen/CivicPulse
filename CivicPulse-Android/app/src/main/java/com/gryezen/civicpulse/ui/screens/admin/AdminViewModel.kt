package com.gryezen.civicpulse.ui.screens.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gryezen.civicpulse.data.model.User
import com.gryezen.civicpulse.data.repository.AdminRepository
import kotlinx.coroutines.launch

data class AdminUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val pending: List<User> = emptyList(),
    val actionInProgressId: String? = null,
    val message: String? = null
)

/**
 * Powers the admin review queue for officials stuck in `pending_review`
 * (no/wrong verification code, an ID document attached instead) — see
 * admin.py's own docstring for exactly what "approve" honestly checks
 * (a human looking at a photo, not real KYC). Only reachable for a
 * signed-in admin (User.isAdmin) — see CivicPulseNavHost.
 */
class AdminViewModel(private val adminRepository: AdminRepository) : ViewModel() {

    var state by mutableStateOf(AdminUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            adminRepository.pendingOfficials()
                .onSuccess { state = state.copy(loading = false, pending = it) }
                .onFailure { state = state.copy(loading = false, error = it.message ?: "Could not load pending officials") }
        }
    }

    fun approve(userId: String) {
        state = state.copy(actionInProgressId = userId, message = null)
        viewModelScope.launch {
            adminRepository.approve(userId)
                .onSuccess { state = state.copy(message = "${it.name.ifBlank { it.email }} approved.", pending = state.pending.filterNot { u -> u.id == userId }) }
                .onFailure { state = state.copy(error = it.message ?: "Could not approve") }
            state = state.copy(actionInProgressId = null)
        }
    }

    fun reject(userId: String) {
        state = state.copy(actionInProgressId = userId, message = null)
        viewModelScope.launch {
            adminRepository.reject(userId)
                .onSuccess { state = state.copy(message = "${it.name.ifBlank { it.email }} rejected.", pending = state.pending.filterNot { u -> u.id == userId }) }
                .onFailure { state = state.copy(error = it.message ?: "Could not reject") }
            state = state.copy(actionInProgressId = null)
        }
    }

    fun consumeMessages() {
        state = state.copy(error = null, message = null)
    }
}
