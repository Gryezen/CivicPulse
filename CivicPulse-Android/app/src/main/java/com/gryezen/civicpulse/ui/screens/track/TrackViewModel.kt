package com.gryezen.civicpulse.ui.screens.track

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gryezen.civicpulse.data.local.scoreDockets
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.ComplaintStatus
import com.gryezen.civicpulse.data.repository.ComplaintRepository
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    PRIORITY_DESC("Priority (high → low)"),
    NEWEST("Newest filed"),
    OLDEST("Oldest filed")
}

data class TrackUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val allDockets: List<Complaint> = emptyList(),
    val categories: List<String> = emptyList(),
    val sort: SortMode = SortMode.PRIORITY_DESC,
    val categoryFilter: String? = null, // null = all categories
    val statusFilter: ComplaintStatus? = null, // null = all statuses
    val docketFilterId: String? = null,
    val nlpQuery: String? = null,
    val notFound: Boolean = false
)

/**
 * Powers the "Complaints & Policies" queue: a docket-ID lookup, a free-text
 * ("NLP") search, and a sortable/filterable browse of the whole queue —
 * mirrors track.html's mockDockets + scoreDockets() + renderQueue() exactly,
 * since none of it is backed by a real endpoint yet (see ComplaintRepository).
 */
class TrackViewModel(private val complaintRepository: ComplaintRepository) : ViewModel() {

    var state by mutableStateOf(TrackUiState())
        private set

    init {
        loadQueue()
    }

    /** Re-pulls the queue — used to pick up newly-filed complaints when the screen is revisited. */
    fun refresh() = loadQueue()

    private fun loadQueue() {
        viewModelScope.launch {
            complaintRepository.queue()
                .onSuccess { dockets ->
                    state = state.copy(
                        loading = false,
                        allDockets = dockets,
                        categories = dockets.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                    )
                }
                .onFailure { state = state.copy(loading = false, error = it.message ?: "Could not load the queue") }
        }
    }

    /** Docket-ID lookup — jumps the queue to just that one entry, like doTrack() on track.html. */
    fun lookup(rawId: String) {
        val id = rawId.trim().uppercase()
        if (id.isBlank()) return
        val found = state.allDockets.any { it.id == id }
        state = state.copy(
            nlpQuery = null,
            docketFilterId = if (found) id else null,
            notFound = !found
        )
    }

    /** Free-text description search — client-side keyword ranking, like doNlpSearch(). */
    fun searchText(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isBlank()) return
        state = state.copy(docketFilterId = null, notFound = false, nlpQuery = q)
    }

    fun tryDemoDocket() = lookup("CP-5102")

    fun clearFilter() {
        state = state.copy(docketFilterId = null, nlpQuery = null, notFound = false)
    }

    fun setSort(sort: SortMode) { state = state.copy(sort = sort) }
    fun setCategoryFilter(category: String?) { state = state.copy(categoryFilter = category) }
    fun setStatusFilter(status: ComplaintStatus?) { state = state.copy(statusFilter = status) }

    /** Same precedence as track.html's renderQueue(): docket filter > NLP search > category/status filters. */
    fun visibleDockets(): List<Complaint> {
        val s = state
        var entries = s.allDockets

        when {
            s.docketFilterId != null -> {
                entries = entries.filter { it.id == s.docketFilterId }
            }
            s.nlpQuery != null -> {
                val scores = scoreDockets(s.nlpQuery, s.allDockets.associateBy { it.id })
                entries = entries.filter { (scores[it.id] ?: 0) > 0 }
                if (s.categoryFilter != null) entries = entries.filter { it.category == s.categoryFilter }
                if (s.statusFilter != null) entries = entries.filter { it.statusEnum == s.statusFilter }
                return entries.sortedByDescending { scores[it.id] ?: 0 }
            }
            else -> {
                if (s.categoryFilter != null) entries = entries.filter { it.category == s.categoryFilter }
                if (s.statusFilter != null) entries = entries.filter { it.statusEnum == s.statusFilter }
            }
        }

        return when (s.sort) {
            SortMode.PRIORITY_DESC -> entries.sortedByDescending { it.priority }
            SortMode.NEWEST -> entries.sortedByDescending { it.filed }
            SortMode.OLDEST -> entries.sortedBy { it.filed }
        }
    }

    fun matchScoreFor(docketId: String): Int? {
        val q = state.nlpQuery ?: return null
        return scoreDockets(q, state.allDockets.associateBy { it.id })[docketId]
    }
}
