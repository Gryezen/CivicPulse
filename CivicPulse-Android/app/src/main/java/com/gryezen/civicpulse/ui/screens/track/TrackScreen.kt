package com.gryezen.civicpulse.ui.screens.track

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.data.local.stageLabel
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.ComplaintStatus
import com.gryezen.civicpulse.ui.components.DropdownField
import com.gryezen.civicpulse.ui.components.PriorityBadge
import com.gryezen.civicpulse.ui.components.PrimaryButton
import com.gryezen.civicpulse.ui.components.SectionHeader
import com.gryezen.civicpulse.ui.components.StatusStamp
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Red
import com.gryezen.civicpulse.ui.theme.TextLow

/**
 * "Complaints & Policies" — docket lookup, free-text search, and a
 * sortable/filterable public queue, mirroring track.html end to end (see
 * TrackViewModel's doc comment for exactly what's mocked vs. real).
 */
@Composable
fun TrackScreen(viewModel: TrackViewModel, initialDocketId: String? = null, onBrowsePolicies: () -> Unit = {}) {
    val state = viewModel.state
    var docketQuery by remember { mutableStateOf(initialDocketId.orEmpty()) }
    var nlpQuery by remember { mutableStateOf("") }
    var expandedDocketId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(initialDocketId) {
        if (!initialDocketId.isNullOrBlank()) viewModel.lookup(initialDocketId)
    }

    Scaffold { padding ->
        if (state.loading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator(color = Navy) }
            return@Scaffold
        }

        val visible = viewModel.visibleDockets()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 32.dp)
        ) {
            item {
                SectionHeader(
                    eyebrow = "NLP-ranked · Api /api/admin/view/complaint/<complaintID>",
                    title = "Complaint priority queue",
                    subtitle = "Every filed complaint is scored on urgency, category, and department fit, then ranked here. Look up a docket, or browse the live queue below."
                )
                TextButton(onClick = onBrowsePolicies, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Looking for a scheme or policy instead? Browse PolicyGyaan →")
                }
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = docketQuery, onValueChange = { docketQuery = it },
                        label = { Text("Docket ID, e.g. CP-5102") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    PrimaryButton(text = "Look up", onClick = { viewModel.lookup(docketQuery) })
                }
                TextButton(onClick = { docketQuery = "CP-5102"; viewModel.tryDemoDocket() }) {
                    Text("Try a sample docket")
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = nlpQuery, onValueChange = { nlpQuery = it },
                        label = { Text("Describe the issue — no docket needed") },
                        placeholder = { Text("e.g. streetlight near Anna Nagar") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    PrimaryButton(text = "Search", onClick = { viewModel.searchText(nlpQuery) })
                }
                Text(
                    "Matched by the same model that ranks the queue below.",
                    style = MaterialTheme.typography.labelSmall, color = TextLow,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(20.dp))
            }

            if (state.notFound) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("NOT FOUND", color = Red, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("No docket matches that number", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                            Text("Double-check the ID from your confirmation message, or file a new complaint.", color = TextLow, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            item {
                // Toolbar: sort + category + status filters, matching track.html's queue-toolbar.
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    DropdownField(
                        label = "Sort by",
                        value = state.sort.label,
                        options = SortMode.entries.map { it.label },
                        onSelect = { label -> SortMode.entries.find { it.label == label }?.let(viewModel::setSort) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        DropdownField(
                            label = "Category",
                            value = state.categoryFilter ?: "All categories",
                            options = listOf("All categories") + state.categories,
                            onSelect = { viewModel.setCategoryFilter(if (it == "All categories") null else it) },
                            modifier = Modifier.weight(1f)
                        )
                        DropdownField(
                            label = "Status",
                            value = state.statusFilter?.let { stageLabel(it.name) } ?: "All statuses",
                            options = listOf("All statuses") + ComplaintStatus.entries.map { stageLabel(it.name) },
                            onSelect = { selected ->
                                val status = ComplaintStatus.entries.find { stageLabel(it.name) == selected }
                                viewModel.setStatusFilter(status)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (state.docketFilterId != null || state.nlpQuery != null) {
                        TextButton(onClick = { viewModel.clearFilter(); docketQuery = ""; nlpQuery = "" }) {
                            Text(if (state.nlpQuery != null) "Clear search ✕" else "Clear docket filter ✕")
                        }
                    }

                    Text(
                        if (state.nlpQuery != null) "${visible.size} match${if (visible.size == 1) "" else "es"} for \"${state.nlpQuery}\""
                        else "${visible.size} docket${if (visible.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall, color = TextLow,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (visible.isEmpty()) {
                item {
                    Text(
                        if (state.nlpQuery != null) "No dockets match \"${state.nlpQuery}\". Try different words, or look up by docket ID above."
                        else "No dockets match these filters.",
                        color = TextLow, modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(visible, key = { it.id }) { docket ->
                    QueueItem(
                        docket = docket,
                        matchScore = viewModel.matchScoreFor(docket.id),
                        expanded = expandedDocketId == docket.id,
                        onToggle = { expandedDocketId = if (expandedDocketId == docket.id) null else docket.id }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItem(docket: Complaint, matchScore: Int?, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle
                ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    PriorityBadge(docket.priority)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(docket.id, style = MaterialTheme.typography.labelSmall, color = TextLow)
                        Spacer(Modifier.width(10.dp))
                        StatusStamp(docket.statusEnum)
                    }
                    Text(docket.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        if (docket.category.isNotBlank()) AssistChip(onClick = {}, label = { Text(docket.category, style = MaterialTheme.typography.labelSmall) })
                        if (docket.languageNative.isNotBlank()) AssistChip(onClick = {}, label = { Text("${docket.languageNative} · ${docket.language}", style = MaterialTheme.typography.labelSmall) })
                    }
                    if (matchScore != null && matchScore > 0) {
                        Text("🔎 Match score $matchScore", style = MaterialTheme.typography.labelSmall, color = Navy, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (docket.filedDisplay.isNotBlank()) {
                        Text("Filed ${docket.filedDisplay}", style = MaterialTheme.typography.labelSmall, color = TextLow, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                IconButton(onClick = onToggle) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = "Toggle details")
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                StageRail(docket.statusEnum)
                Spacer(Modifier.height(14.dp))
                if (docket.body.isNotBlank()) {
                    Text("COMPLAINT DETAILS", style = MaterialTheme.typography.labelSmall, color = TextLow)
                    Text(docket.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                }
                if (docket.department.isNotBlank()) Text("Routed to ${docket.department}", color = TextLow, style = MaterialTheme.typography.bodyMedium)
                if (docket.authority.isNotBlank()) Text(docket.authority, color = TextLow, style = MaterialTheme.typography.bodyMedium)
                if (docket.files > 0) Text("${docket.files} proof file(s) attached", color = TextLow, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                if (docket.note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(2.dp)) {
                        Text("💬 Next update: ${docket.note}", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** received → AI triage → assigned → resolved, matching track.html's status-rail. */
@Composable
private fun StageRail(current: ComplaintStatus) {
    val stages = listOf(ComplaintStatus.received, ComplaintStatus.processing, ComplaintStatus.assigned, ComplaintStatus.resolved)
    val currentIdx = stages.indexOf(current)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        stages.forEachIndexed { i, stage ->
            val done = i < currentIdx
            val isCurrent = i == currentIdx
            val color = when {
                done -> com.gryezen.civicpulse.ui.theme.Green
                isCurrent -> Navy
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(if (done) "✓" else "${i + 1}", color = color, style = MaterialTheme.typography.labelLarge)
                Text(stageLabel(stage.name), style = MaterialTheme.typography.labelSmall, color = if (isCurrent) Navy else TextLow, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

