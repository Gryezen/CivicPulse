package com.gryezen.civicpulse.ui.screens.complaint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gryezen.civicpulse.data.local.PreferencesManager
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
        proofFiles: List<File>,
        beforePhoto: File? = null
    ) {
        if (title.isBlank() || dateFrom.isBlank() || dateTo.isBlank() || body.isBlank()) {
            state = state.copy(error = "Fill in the title, dates, and description before submitting.")
            return
        }
        state = state.copy(submitting = true, error = null)
        viewModelScope.launch {
            // uploads.py only accepts image data; encoding is blocking file
            // I/O, so it's done off the main thread same as the picker.
            val beforePhotoDataUrl = beforePhoto?.let {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.gryezen.civicpulse.util.encodeFileAsImageDataUrl(it)
                }
            }
            val newComplaint = NewComplaint(
                title = title.trim(),
                dateFrom = dateFrom,
                dateTo = dateTo,
                authorityLevel = authorityLevel,
                language = language,
                body = body.trim(),
                proofFiles = proofFiles,
                beforePhotoDataUrl = beforePhotoDataUrl
            )
            // ComplaintRepository.fileComplaint() already falls back to the
            // local classifier and persists the result if the real endpoint
            // isn't reachable, so this is a straight success/failure here.
            complaintRepository.fileComplaint(newComplaint)
                .onSuccess { state = state.copy(submitting = false, result = it) }
                .onFailure { state = state.copy(submitting = false, error = it.message ?: "Could not file complaint") }
        }
    }

    fun reset() {
        state = FileComplaintUiState()
    }
}
