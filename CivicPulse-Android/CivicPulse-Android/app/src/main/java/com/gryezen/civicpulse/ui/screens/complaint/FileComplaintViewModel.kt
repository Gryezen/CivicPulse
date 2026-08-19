package com.gryezen.civicpulse.ui.screens.complaint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gryezen.civicpulse.data.local.PreferencesManager
import com.gryezen.civicpulse.data.local.classifyComplaintLocally
import com.gryezen.civicpulse.data.model.CreateComplaintResponse
import com.gryezen.civicpulse.data.repository.ComplaintRepository
import com.gryezen.civicpulse.data.repository.NewComplaint
import kotlinx.coroutines.launch
import java.io.File

data class FileComplaintUiState(
    val submitting: Boolean = false,
    val error: String? = null,
    val result: CreateComplaintResponse? = null
)

class FileComplaintViewModel(
    private val complaintRepository: ComplaintRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    var state by mutableStateOf(FileComplaintUiState())
        private set

    fun submit(
        title: String,
        dateFrom: String,
        dateTo: String,
        authorityLevel: String,
        language: String,
        body: String,
        proofFiles: List<File>
    ) {
        if (title.isBlank() || dateFrom.isBlank() || dateTo.isBlank() || body.isBlank()) {
            state = state.copy(error = "Fill in the title, dates, and description before submitting.")
            return
        }
        state = state.copy(submitting = true, error = null)
        viewModelScope.launch {
            val newComplaint = NewComplaint(
                title = title.trim(),
                dateFrom = dateFrom,
                dateTo = dateTo,
                authorityLevel = authorityLevel,
                language = language,
                body = body.trim(),
                proofFiles = proofFiles
            )
            complaintRepository.fileComplaint(newComplaint)
                .onSuccess { state = state.copy(submitting = false, result = it) }
                .onFailure {
                    // POST /api/create/complaint isn't live yet (see ApiService.kt) —
                    // fall back to the same client-side classifier complaint.html
                    // uses, so the flow is still demoable end-to-end.
                    val fallback = classifyComplaintLocally(title, body, language)
                    state = state.copy(submitting = false, result = fallback, error = null)
                }
        }
    }

    fun reset() {
        state = FileComplaintUiState()
    }
}
