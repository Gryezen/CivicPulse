package com.gryezen.civicpulse.ui.screens.complaint

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.ui.components.PriorityBadge
import com.gryezen.civicpulse.ui.components.PrimaryButton
import com.gryezen.civicpulse.ui.components.SectionHeader
import com.gryezen.civicpulse.ui.theme.Green
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Red
import com.gryezen.civicpulse.ui.theme.TextLow
import com.gryezen.civicpulse.util.resolveUrisToCacheFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val AUTHORITY_OPTIONS = listOf(
    "Municipal Corporation Services", "Ward / Panchayat Office", "District Administration",
    "State Government Department", "Central Government Department",
    "Public Sector Utility (water / power / gas)"
)
private val LANGUAGE_OPTIONS = listOf(
    "English", "Hindi", "Tamil", "Telugu", "Kannada", "Malayalam",
    "Bengali", "Marathi", "Gujarati", "Punjabi", "Urdu", "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileComplaintScreen(viewModel: FileComplaintViewModel, onTrackDocket: (String) -> Unit, onFileAnother: () -> Unit) {
    val state = viewModel.state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var authority by remember { mutableStateOf(AUTHORITY_OPTIONS.first()) }
    var authorityExpanded by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(LANGUAGE_OPTIONS.first()) }
    var languageExpanded by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<File>>(emptyList()) }
    var resolvingAttachments by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        val capped = uris.take(5)
        if (capped.isEmpty()) return@rememberLauncherForActivityResult
        resolvingAttachments = true
        scope.launch {
            val files = withContext(Dispatchers.IO) { resolveUrisToCacheFiles(context, capped) }
            attachments = files
            resolvingAttachments = false
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (state.result != null) {
                ComplaintSuccessCard(
                    docketId = state.result.id,
                    category = state.result.category,
                    department = state.result.department,
                    priority = state.result.priority,
                    language = state.result.language,
                    onTrack = { onTrackDocket(state.result.id) },
                    onFileAnother = {
                        title = ""; dateFrom = ""; dateTo = ""; body = ""; attachments = emptyList()
                        viewModel.reset(); onFileAnother()
                    }
                )
                return@Scaffold
            }

            SectionHeader(
                eyebrow = "Api /api/create/complaint",
                title = "File a new complaint",
                subtitle = "Give us the facts — where, when, and what happened. AI handles the sorting from here."
            )
            Spacer(Modifier.height(24.dp))

            if (state.error != null) {
                Text(state.error, color = Red, modifier = Modifier.padding(bottom = 12.dp))
            }

            OutlinedTextField(
                title, { title = it }, label = { Text("Title") },
                placeholder = { Text("e.g. Streetlight not working on 4th Cross Road") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    dateFrom, { dateFrom = it }, label = { Text("Issue started (YYYY-MM-DD)") },
                    modifier = Modifier.weight(1f), singleLine = true
                )
                OutlinedTextField(
                    dateTo, { dateTo = it }, label = { Text("Ongoing until") },
                    modifier = Modifier.weight(1f), singleLine = true
                )
            }
            Spacer(Modifier.height(14.dp))

            ExposedDropdownMenuBox(expanded = authorityExpanded, onExpandedChange = { authorityExpanded = it }) {
                OutlinedTextField(
                    value = authority, onValueChange = {}, readOnly = true,
                    label = { Text("Concerning authority level") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authorityExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = authorityExpanded, onDismissRequest = { authorityExpanded = false }) {
                    AUTHORITY_OPTIONS.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt) }, onClick = { authority = opt; authorityExpanded = false })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            ExposedDropdownMenuBox(expanded = languageExpanded, onExpandedChange = { languageExpanded = it }) {
                OutlinedTextField(
                    value = language, onValueChange = {}, readOnly = true,
                    label = { Text("Language of complaint") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                    LANGUAGE_OPTIONS.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt) }, onClick = { language = opt; languageExpanded = false })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                body, { if (it.length <= 750) body = it }, label = { Text("What happened") },
                placeholder = { Text("Describe the issue in as much detail as you can — location landmarks help a lot.") },
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )
            Text("${body.length}/750 characters", style = MaterialTheme.typography.labelSmall, color = TextLow, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))

            TextButton(onClick = { filePicker.launch("*/*") }) {
                Text(
                    when {
                        resolvingAttachments -> "Processing attachments…"
                        attachments.isEmpty() -> "Attach proof (photo, video, or audio)"
                        else -> "${attachments.size} file(s) attached — tap to change"
                    }
                )
            }
            Text("JPEG, PNG, MP4, WAV · up to 5 files", style = MaterialTheme.typography.labelSmall, color = TextLow)

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Submit complaint",
                loading = state.submitting,
                enabled = !resolvingAttachments,
                onClick = {
                    viewModel.submit(
                        title = title, dateFrom = dateFrom, dateTo = dateTo,
                        authorityLevel = authority, language = language, body = body,
                        proofFiles = attachments
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
    }
}

@Composable
private fun ComplaintSuccessCard(
    docketId: String,
    category: String,
    department: String,
    priority: Int,
    language: String,
    onTrack: () -> Unit,
    onFileAnother: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("RECEIVED", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Complaint filed", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "Your docket is $docketId. Track its status any time — no calls needed.",
            style = MaterialTheme.typography.bodyMedium, color = TextLow,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("NLP CLASSIFICATION (AUTOMATIC)", style = MaterialTheme.typography.labelSmall, color = TextLow)
                Spacer(Modifier.height(14.dp))
                InfoRow("Category", category)
                InfoRow("Routed to", department)
                InfoRow("Language detected", language)
                Spacer(Modifier.height(6.dp))
                PriorityBadge(priority)
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(text = "Track this docket", onClick = onTrack)
            TextButton(onClick = onFileAnother) { Text("File another complaint") }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLow)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
