package com.gryezen.civicpulse.ui.screens.admin

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.data.model.User
import com.gryezen.civicpulse.ui.components.GhostButton
import com.gryezen.civicpulse.ui.components.PrimaryButton
import com.gryezen.civicpulse.ui.components.SectionHeader
import com.gryezen.civicpulse.ui.theme.Green
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Red
import com.gryezen.civicpulse.ui.theme.TextLow

/**
 * Admin review queue — admin.py's GET /api/admin/pending-officials plus
 * approve/reject. Only reachable for a signed-in admin (User.isAdmin) —
 * see CivicPulseNavHost, which gates the route the same way admin.py
 * gates its API. Admin accounts are never self-registered (see admin.py's
 * own docstring), so there's no "become an admin" flow anywhere in the app.
 */
@Composable
fun AdminScreen(viewModel: AdminViewModel) {
    val state = viewModel.state

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 32.dp)
        ) {
            item {
                SectionHeader(
                    eyebrow = "Api /api/admin/*",
                    title = "Official verification review",
                    subtitle = "These accounts had no matching department code, so they attached an ID document instead. This is a human sanity-check on a photo — not real government ID verification. Say so if asked."
                )
                Spacer(Modifier.height(16.dp))
            }

            if (state.error != null) {
                item { Text(state.error, color = Red, modifier = Modifier.padding(bottom = 12.dp)) }
            }
            if (state.message != null) {
                item { Text(state.message, color = Green, modifier = Modifier.padding(bottom = 12.dp)) }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Navy)
                    }
                }
            } else if (state.pending.isEmpty()) {
                item { Text("No officials waiting on review right now.", color = TextLow, modifier = Modifier.padding(vertical = 16.dp)) }
            } else {
                items(state.pending, key = { it.id ?: it.email }) { official ->
                    PendingOfficialCard(
                        official = official,
                        actionInProgress = state.actionInProgressId == official.id,
                        onApprove = { official.id?.let(viewModel::approve) },
                        onReject = { official.id?.let(viewModel::reject) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingOfficialCard(official: User, actionInProgress: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(official.name.ifBlank { "(no name on file)" }, style = MaterialTheme.typography.titleMedium)
            Text(official.email, color = TextLow, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("Employee ID: ${official.employeeId.ifBlank { "—" }}", style = MaterialTheme.typography.bodyMedium)
            Text("Department: ${official.department.ifBlank { "—" }}", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (official.hasIdDocument) "ID document attached" else "No ID document on file",
                color = if (official.hasIdDocument) TextLow else Red,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(text = "Approve", loading = actionInProgress, onClick = onApprove)
                GhostButton(text = "Reject", onClick = onReject)
            }
        }
    }
}
