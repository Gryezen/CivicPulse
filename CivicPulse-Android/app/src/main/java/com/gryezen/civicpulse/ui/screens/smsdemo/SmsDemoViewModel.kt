package com.gryezen.civicpulse.ui.screens.smsdemo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gryezen.civicpulse.data.repository.IvrRepository
import kotlinx.coroutines.launch

data class SmsBubble(val text: String, val outgoing: Boolean)

data class SmsDemoUiState(
    val phone: String = "",
    val messages: List<SmsBubble> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null
)

/**
 * Backs the in-app SMS/IVR demo — ivr.py's POST /api/ivr/demo, same
 * handle_inbound() command logic (STATUS / STATUS <id-fragment> / HELP /
 * CONFIRM / DISPUTE) a real telecom gateway webhook would call. This demo
 * endpoint needs an explicit phone number in the request body (not the
 * logged-in session's identity) to accurately simulate what a real SMS
 * gateway actually hands the server — see ivr.py's own docstring.
 */
class SmsDemoViewModel(private val ivrRepository: IvrRepository) : ViewModel() {

    var state by mutableStateOf(SmsDemoUiState())
        private set

    fun setPhone(phone: String) {
        state = state.copy(phone = phone)
    }

    fun send(text: String) {
        if (text.isBlank()) return
        if (state.phone.isBlank()) {
            state = state.copy(error = "Enter the phone number linked to your account first (see Account > Phone).")
            return
        }
        state = state.copy(
            messages = state.messages + SmsBubble(text, outgoing = true),
            sending = true,
            error = null
        )
        viewModelScope.launch {
            ivrRepository.send(state.phone, text)
                .onSuccess { reply -> state = state.copy(messages = state.messages + SmsBubble(reply, outgoing = false)) }
                .onFailure { state = state.copy(error = it.message ?: "Something went wrong") }
            state = state.copy(sending = false)
        }
    }
}
