package com.gryezen.civicpulse.ui.screens.officer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.OfficerSummary
import com.gryezen.civicpulse.data.repository.OfficerRepository
import kotlinx.coroutines.launch

data class OfficerUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val summary: OfficerSummary? = null,
    val queue: List<Complaint> = emptyList(),
    val broadCategoryFilter: String? = null, // null = all
    val onlyFlagged: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val bulkActionInProgress: Boolean = false,
    val bulkActionMessage: String? = null,
    val syncingPolicies: Boolean = false
)

/**
 * Powers the officer triage dashboard — officer.py's summary + queue +
 * bulk-action + resolve-with-photo + policy-sync endpoints. Only ever
 * reached from a screen gated on User.isOfficial (see CivicPulseNavHost);
 * the server independently enforces the same gate (_official_required),
 * so a 403 here just surfaces as a normal error state, not a crash.
 */
class OfficerViewModel(private val officerRepository: OfficerRepository) : ViewModel() {

    var state by mutableStateOf(OfficerUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            val summaryResult = officerRepository.summary()
            val queueResult = officerRepository.queue(
                broadCategory = state.broadCategoryFilter,
                onlyFlagged = state.onlyFlagged
            )

            val error = summaryResult.exceptionOrNull()?.message ?: queueResult.exceptionOrNull()?.message
            state = state.copy(
                loading = false,
                error = error,
                summary = summaryResult.getOrNull() ?: state.summary,
                queue = queueResult.getOrNull()?.items ?: state.queue
            )
        }
    }

    fun setBroadCategoryFilter(category: String?) {
        state = state.copy(broadCategoryFilter = category)
        refresh()
    }

    fun setOnlyFlagged(flagged: Boolean) {
        state = state.copy(onlyFlagged = flagged)
        refresh()
    }

    fun toggleSelected(id: String) {
        val current = state.selectedIds
        state = state.copy(selectedIds = if (id in current) current - id else current + id)
    }

    fun clearSelection() {
        state = state.copy(selectedIds = emptySet())
    }

    // officer.py's bulk_action() already defaults an omitted `officer` to
    // current_user.name server-side, so "assign to me" needs no client-side
    // identity plumbing at all.
    fun assignSelectedToMe() = runBulk { officerRepository.assign(it, officer = null) }

    fun escalateSelected() = runBulk { officerRepository.escalate(it) }

    fun resolveSelected() = runBulk { officerRepository.resolve(it) }

    private fun runBulk(action: suspend (List<String>) -> Result<com.gryezen.civicpulse.data.model.BulkActionResponse>) {
        val ids = state.selectedIds.toList()
        if (ids.isEmpty()) return
        state = state.copy(bulkActionInProgress = true, bulkActionMessage = null)
        viewModelScope.launch {
            action(ids)
                .onSuccess { state = state.copy(bulkActionMessage = "Updated ${it.updated} complaint(s).", selectedIds = emptySet()) }
                .onFailure { state = state.copy(error = it.message ?: "Bulk action failed") }
            state = state.copy(bulkActionInProgress = false)
            refresh()
        }
    }

    /** after-photo as a base64 data URL — see util/FileUtils.kt's encodeFileAsImageDataUrl(). */
    fun resolveWithPhoto(complaintId: String, afterPhotoDataUrl: String) {
        state = state.copy(bulkActionInProgress = true, bulkActionMessage = null)
        viewModelScope.launch {
            officerRepository.resolveWithPhoto(complaintId, afterPhotoDataUrl)
                .onSuccess { state = state.copy(bulkActionMessage = "Marked resolved with photo evidence — awaiting citizen confirmation.") }
                .onFailure { state = state.copy(error = it.message ?: "Could not resolve with photo") }
            state = state.copy(bulkActionInProgress = false)
            refresh()
        }
    }

    fun syncPolicies(source: String?) {
        state = state.copy(syncingPolicies = true, bulkActionMessage = null)
        viewModelScope.launch {
            officerRepository.syncPolicies(source)
                .onSuccess { state = state.copy(bulkActionMessage = "Policy sync: ${it.added} added, ${it.updated} updated, ${it.skipped} skipped.") }
                .onFailure { state = state.copy(error = it.message ?: "Policy sync failed") }
            state = state.copy(syncingPolicies = false)
        }
    }

    fun consumeMessages() {
        state = state.copy(error = null, bulkActionMessage = null)
    }
}
