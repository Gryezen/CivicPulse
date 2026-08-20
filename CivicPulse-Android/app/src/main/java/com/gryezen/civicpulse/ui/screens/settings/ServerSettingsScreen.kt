package com.gryezen.civicpulse.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.CivicPulseApp
import com.gryezen.civicpulse.ui.components.PrimaryButton
import com.gryezen.civicpulse.ui.components.SectionHeader
import com.gryezen.civicpulse.ui.theme.Red
import com.gryezen.civicpulse.ui.theme.TextLow
import kotlinx.coroutines.launch

/**
 * The Render deployment / Supabase-backed Postgres migration is being worked
 * on in parallel. Rather than hardcode a URL that will go stale, this screen
 * lets whoever's testing the app point it at any backend without a rebuild.
 */
@Composable
fun ServerSettingsScreen(app: CivicPulseApp) {
    val scope = rememberCoroutineScope()
    val savedUrl by app.preferencesManager.baseUrl.collectAsState(initial = "")
    var url by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(savedUrl) {
        if (url.isEmpty() && savedUrl.isNotEmpty()) url = savedUrl
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            SectionHeader(
                eyebrow = "Developer options",
                title = "Server settings",
                subtitle = "Point the app at the backend you're currently testing — the Render URL / Supabase-backed API is still being finalized."
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it; saved = false },
                label = { Text("Base URL") },
                placeholder = { Text("https://civicpulse-india.onrender.com/") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "Currently: $savedUrl",
                style = MaterialTheme.typography.labelSmall,
                color = TextLow,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )

            PrimaryButton(
                text = if (saved) "Saved ✓" else "Save",
                onClick = {
                    scope.launch {
                        app.preferencesManager.setBaseUrl(url.trim())
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            )

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text("Locally-filed complaints", style = MaterialTheme.typography.titleMedium)
            Text(
                "Complaints filed on this device are stored here until the real complaint list/lookup endpoints exist (see ComplaintRepository.kt). Clearing this only affects this device.",
                style = MaterialTheme.typography.bodyMedium, color = TextLow,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
            var cleared by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { scope.launch { app.filedComplaintsStore.clear(); cleared = true } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (cleared) "Cleared ✓" else "Clear locally-filed complaints", color = if (cleared) TextLow else Red)
            }
        }
    }
}
