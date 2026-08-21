package com.gryezen.civicpulse.ui.screens.policy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gryezen.civicpulse.data.local.scorePolicies
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.data.repository.PolicyRepository
import kotlinx.coroutines.launch

data class PolicyListUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val policies: List<Policy> = emptyList(),
    val query: String = ""
)

data class PolicyDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val policy: Policy? = null
)

class PolicyViewModel(private val policyRepository: PolicyRepository) : ViewModel() {

    var listState by mutableStateOf(PolicyListUiState())
        private set

    var detailState by mutableStateOf(PolicyDetailUiState())
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            // Paint from the last-synced cache instantly (shared with the
            // Dashboard's recommendations — see PolicyRepository), then
            // refresh over the network. Avoids a blank spinner on slow/2G
            // connections when we likely already have this exact data.
            val cached = policyRepository.cachedRecommended()
            if (!cached.isNullOrEmpty()) {
                listState = listState.copy(loading = false, policies = cached)
            }

            policyRepository.recommended()
                .onSuccess { listState = listState.copy(loading = false, policies = it) }
                .onFailure {
                    if (cached.isNullOrEmpty()) {
                        listState = listState.copy(loading = false, error = it.message ?: "Could not load policies")
                    }
                }
        }
    }

    fun loadDetail(slug: String) {
        detailState = PolicyDetailUiState(loading = true)
        viewModelScope.launch {
            policyRepository.bySlug(slug)
                .onSuccess { detailState = PolicyDetailUiState(loading = false, policy = it) }
                .onFailure { detailState = PolicyDetailUiState(loading = false, error = it.message ?: "Policy not found") }
        }
    }

    fun setQuery(query: String) {
        listState = listState.copy(query = query)
    }

    /** Same stand-in as main.js's scorePolicies() — keyword overlap, no backend search yet. */
    fun visiblePolicies(): List<Policy> {
        val q = listState.query.trim()
        if (q.isBlank()) return listState.policies
        return scorePolicies(q, listState.policies).filter { it.second > 0 }.map { it.first }
    }
}
