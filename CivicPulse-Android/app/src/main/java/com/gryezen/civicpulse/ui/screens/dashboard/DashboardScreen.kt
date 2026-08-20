package com.gryezen.civicpulse.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.data.model.Complaint
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.ui.components.PriorityBadge
import com.gryezen.civicpulse.ui.components.StatusStamp
import com.gryezen.civicpulse.ui.theme.Green
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Saffron
import com.gryezen.civicpulse.ui.theme.TextLow

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onFileComplaint: () -> Unit,
    onOpenComplaint: (String) -> Unit,
    onOpenPolicy: (String) -> Unit,
    onBrowsePolicies: () -> Unit
) {
    val state = viewModel.state
    // Re-pulls whenever this screen re-enters composition (e.g. returning
    // from "File complaint" via the bottom nav), so a just-filed complaint
    // shows up immediately instead of waiting for the next cold start.
    LaunchedEffect(Unit) { viewModel.refresh() }
    val name by viewModel.displayName.collectAsState()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onFileComplaint,
                containerColor = Navy,
                contentColor = androidx.compose.ui.graphics.Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("File complaint") }
            )
        }
    ) { padding ->
        if (state.loading && state.complaints.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Navy)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp)
        ) {
            item {
                Text("Welcome back, ${name.substringBefore(' ')}.", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Here's everything you've filed, and what's relevant to you right now.",
                    style = MaterialTheme.typography.bodyMedium, color = TextLow,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                )
            }

            if (state.error != null) {
                item {
                    Text(
                        state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            item {
                StatRow(
                    total = state.stats.total, received = state.stats.received,
                    processing = state.stats.processing, resolved = state.stats.resolved
                )
                Spacer(Modifier.height(28.dp))
            }

            item {
                Text("Your complaints", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            }

            if (state.complaints.isEmpty()) {
                item { EmptyCard("No complaints filed yet. Tap \"File complaint\" to submit your first one.") }
            } else {
                items(state.complaints, key = { it.id }) { complaint ->
                    ComplaintRow(complaint) { onOpenComplaint(complaint.id) }
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recommended for you", style = MaterialTheme.typography.titleLarge)
                    androidx.compose.material3.TextButton(onClick = onBrowsePolicies) { Text("Browse all →") }
                }
                Spacer(Modifier.height(4.dp))
            }

            if (state.policies.isEmpty()) {
                item { EmptyCard("No recommendations yet.") }
            } else {
                items(state.policies.take(3), key = { it.slug }) { policy -> PolicyCard(policy) { onOpenPolicy(policy.slug) } }
            }
        }
    }
}

@Composable
private fun StatRow(total: Int, received: Int, processing: Int, resolved: Int) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxWidth().height(88.dp)
    ) {
        item { StatCell("Total", total, MaterialTheme.colorScheme.onSurface) }
        item { StatCell("Received", received, Saffron) }
        item { StatCell("In review", processing, Green) }
        item { StatCell("Resolved", resolved, Navy) }
    }
}

@Composable
private fun StatCell(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .padding(4.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
            .padding(14.dp)
    ) {
        Text("$value", color = color, style = MaterialTheme.typography.headlineMedium)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLow)
    }
}

@Composable
private fun ComplaintRow(complaint: Complaint, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(complaint.id, style = MaterialTheme.typography.labelSmall, color = TextLow)
                    Text(complaint.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                }
                StatusStamp(complaint.statusEnum)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (complaint.authority.isNotBlank()) Text(complaint.authority, style = MaterialTheme.typography.labelSmall, color = TextLow)
                if (complaint.filedDisplay.isNotBlank()) {
                    Text("Filed ${complaint.filedDisplay}", style = MaterialTheme.typography.labelSmall, color = TextLow)
                }
            }
        }
    }
}

@Composable
private fun PolicyCard(policy: Policy, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(policy.source.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLow)
            Text(policy.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
            Text(policy.summary, style = MaterialTheme.typography.bodyMedium, color = TextLow)
            Text("View details →", style = MaterialTheme.typography.labelLarge, color = Navy, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextLow, style = MaterialTheme.typography.bodyMedium)
    }
}
