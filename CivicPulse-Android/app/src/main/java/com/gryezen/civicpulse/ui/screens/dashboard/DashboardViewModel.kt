package com.gryezen.civicpulse.ui.screens.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gryezen.civicpulse.data.local.PreferencesManager
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.DashboardStats
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.data.repository.ComplaintRepository
import com.gryezen.civicpulse.data.repository.PolicyRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val complaints: List<Complaint> = emptyList(),
    val policies: List<Policy> = emptyList(),
    val stats: DashboardStats = DashboardStats()
)

class DashboardViewModel(
    private val complaintRepository: ComplaintRepository,
    private val policyRepository: PolicyRepository,
    preferencesManager: PreferencesManager
) : ViewModel() {

    var state by mutableStateOf(DashboardUiState())
        private set

    val displayName: StateFlow<String> = preferencesManager.cachedDisplayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Citizen")

    init {
        refresh()
    }

    fun refresh() {
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            val complaintsResult = complaintRepository.myComplaints()
            val policiesResult = policyRepository.recommended()

            val complaints = complaintsResult.getOrNull()
            val policies = policiesResult.getOrNull()

            if (complaints == null && policies == null) {
                state = state.copy(
                    loading = false,
                    error = complaintsResult.exceptionOrNull()?.message ?: "Could not reach the server"
                )
                return@launch
            }

            state = state.copy(
                loading = false,
                error = null,
                complaints = complaints.orEmpty(),
                policies = policies.orEmpty(),
                stats = DashboardStats.from(complaints.orEmpty())
            )
        }
    }
}
