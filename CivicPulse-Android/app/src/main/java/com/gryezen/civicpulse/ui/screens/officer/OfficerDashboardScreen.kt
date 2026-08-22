package com.gryezen.civicpulse.ui.screens.officer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.ui.components.DropdownField
import com.gryezen.civicpulse.ui.components.GhostButton
import com.gryezen.civicpulse.ui.components.PriorityBadge
import com.gryezen.civicpulse.ui.components.PrimaryButton
import com.gryezen.civicpulse.ui.components.SectionHeader
import com.gryezen.civicpulse.ui.components.StatusStamp
import com.gryezen.civicpulse.ui.theme.Green
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Red
import com.gryezen.civicpulse.ui.theme.TextLow
import com.gryezen.civicpulse.util.encodeFileAsImageDataUrl
import com.gryezen.civicpulse.util.resolveUrisToCacheFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BROAD_CATEGORIES = listOf(
    "Crime & Public Safety", "Healthcare & Welfare", "Infrastructure & Utilities",
    "Corruption & Vigilance", "General Governance"
)

/**
 * Officer triage dashboard — mirrors officer.py's summary/queue/bulk/
 * resolve-with-photo/policy-sync. Only reachable for a signed-in official
 * (User.isOfficial) — see CivicPulseNavHost, which gates the route the
 * same way officer.py gates its API.
 */
@Composable
fun OfficerDashboardScreen(viewModel: OfficerViewModel) {
    val state = viewModel.state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resolvePhotoTargetId by remember { mutableStateOf<String?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val targetId = resolvePhotoTargetId
        if (uri == null || targetId == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) { resolveUrisToCacheFiles(context, listOf(uri)) }.firstOrNull()
            val dataUrl = file?.let { withContext(Dispatchers.IO) { encodeFileAsImageDataUrl(it) } }
            if (dataUrl != null) viewModel.resolveWithPhoto(targetId, dataUrl)
            resolvePhotoTargetId = null
        }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 32.dp)
        ) {
            item {
                SectionHeader(
                    eyebrow = "Api /api/officer/*",
                    title = "Officer triage queue",
                    subtitle = "Audit-tier and threat-flagged cases first, then corruption-flagged, then priority. Two-party closure means 'resolve' here only proposes a fix — the citizen still has to confirm it."
                )
                Spacer(Modifier.height(16.dp))
            }

            if (state.error != null) {
                item { Text(state.error, color = Red, modifier = Modifier.padding(bottom = 12.dp)) }
            }
            if (state.bulkActionMessage != null) {
                item { Text(state.bulkActionMessage, color = Green, modifier = Modifier.padding(bottom = 12.dp)) }
            }

            state.summary?.let { summary ->
                item {
                    Card(
                        shape = RoundedCornerShape(2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("QUEUE SNAPSHOT", style = MaterialTheme.typography.labelSmall, color = TextLow)
                            Spacer(Modifier.height(10.dp))
                            StatRow("Total", summary.total.toString())
                            StatRow("Unresolved", summary.unresolved.toString())
                            StatRow("Needs review", summary.needsReview.toString())
                            StatRow("Corruption-flagged", summary.corruptionFlag.toString())
                            StatRow("Threat-flagged", summary.threatFlag.toString())
                            StatRow("Audit tier", summary.auditTier.toString())
                            StatRow("Wellbeing check-ins", summary.wellbeingRisk.toString())
                            StatRow("Auto-resolved", "${summary.autoResolved} (${(summary.autoResolvedShareOfHandled * 100).toInt()}% of handled)")
                            if (summary.systemicAlerts.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(10.dp))
                                Text("⚠ SYSTEMIC ALERTS", style = MaterialTheme.typography.labelSmall, color = Red, fontWeight = FontWeight.Bold)
                                summary.systemicAlerts.forEach { alert ->
                                    Text(
                                        "${alert.department}: ${alert.recentCount} in last 30d vs ${alert.baselineAverage} baseline (${alert.deviationRatio}×)",
                                        style = MaterialTheme.typography.bodySmall, color = TextLow,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                DropdownField(
                    label = "Broad category",
                    value = state.broadCategoryFilter ?: "All categories",
                    options = listOf("All categories") + BROAD_CATEGORIES,
                    onSelect = { viewModel.setBroadCategoryFilter(if (it == "All categories") null else it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Only flagged (corruption / threat / audit / wellbeing / needs-review)")
                    Switch(checked = state.onlyFlagged, onCheckedChange = { viewModel.setOnlyFlagged(it) })
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.syncPolicies(null) }) {
                    Text(if (state.syncingPolicies) "Syncing policies…" else "Sync policy table now")
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.selectedIds.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(2.dp),
                        colors = CardDefaults.cardColors(containerColor = Navy.copy(alpha = 0.06f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${state.selectedIds.size} selected", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrimaryButton(text = "Assign to me", loading = state.bulkActionInProgress, onClick = { viewModel.assignSelectedToMe() })
                                GhostButton(text = "Escalate", onClick = { viewModel.escalateSelected() })
                                GhostButton(text = "Resolve", onClick = { viewModel.resolveSelected() })
                            }
                            TextButton(onClick = { viewModel.clearSelection() }) { Text("Clear selection") }
                        }
                    }
                }
            }

            if (state.loading && state.queue.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Navy)
                    }
                }
            } else if (state.queue.isEmpty()) {
                item { Text("Nothing in the queue for these filters.", color = TextLow, modifier = Modifier.padding(vertical = 16.dp)) }
            } else {
                items(state.queue, key = { it.id }) { complaint ->
                    OfficerQueueCard(
                        complaint = complaint,
                        selected = complaint.id in state.selectedIds,
                        expanded = expandedId == complaint.id,
                        onToggleSelect = { viewModel.toggleSelected(complaint.id) },
                        onToggleExpand = { expandedId = if (expandedId == complaint.id) null else complaint.id },
                        onResolveWithPhoto = { resolvePhotoTargetId = complaint.id; photoPicker.launch("image/*") }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextLow, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OfficerQueueCard(
    complaint: Complaint,
    selected: Boolean,
    expanded: Boolean,
    onToggleSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onResolveWithPhoto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Navy.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PriorityBadge(complaint.priority)
                        StatusStamp(complaint.statusEnum)
                    }
                    Text(complaint.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                    Text("${complaint.id} · ${complaint.broadCategory} · ${complaint.department}", color = TextLow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    if (complaint.aiBrief.isNotBlank()) {
                        Text("🤖 ${complaint.aiBrief}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        if (complaint.corruptionFlag) FlagChip("Corruption", Red)
                        if (complaint.threatFlag) FlagChip("Threat", Red)
                        if (complaint.auditTier) FlagChip("Audit tier", Red)
                        if (complaint.wellbeingRisk) FlagChip("Wellbeing check-in", Red)
                        if (complaint.needsReview) FlagChip("Needs review", TextLow)
                        if (complaint.suspectedCoordinated) FlagChip("Suspected coordinated", TextLow)
                        if (complaint.suspectedTargeting) FlagChip("Suspected targeting", TextLow)
                    }
                }
                TextButton(onClick = onToggleExpand) { Text(if (expanded) "Less" else "More") }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                if (complaint.body.isNotBlank()) Text(complaint.body, style = MaterialTheme.typography.bodyMedium)
                if (complaint.assignedOfficer.isNotBlank()) Text("Assigned: ${complaint.assignedOfficer}", color = TextLow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                if (complaint.corroborationCount > 1) Text("${complaint.corroborationCount} citizens corroborated this", color = TextLow, style = MaterialTheme.typography.labelSmall)
                if (complaint.disputeCount > 0) Text("Reopened ${complaint.disputeCount} time(s)", color = Red, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
                GhostButton(text = "Resolve with after-photo", onClick = onResolveWithPhoto)
            }
        }
    }
}

@Composable
private fun FlagChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .wrapContentWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Clip
        )
    }
}
