package com.gryezen.civicpulse.ui.screens.policy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.ui.components.SectionHeader
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Red
import com.gryezen.civicpulse.ui.theme.TextLow

/** PolicyGyaan browsing — mirrors dashboard.html's "Recommended for you" cards + policy.html's dataset. */
@Composable
fun PolicyListScreen(viewModel: PolicyViewModel, onOpenPolicy: (String) -> Unit) {
    val state = viewModel.listState

    Scaffold { padding ->
        if (state.loading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator(color = Navy) }
            return@Scaffold
        }

        val visible = viewModel.visiblePolicies()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 32.dp)
        ) {
            item {
                SectionHeader(
                    eyebrow = "PolicyGyaan · Api /api/policies/",
                    title = "Policies & schemes for you",
                    subtitle = "Government schemes and grievance SLAs matched to what you've filed or searched for."
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Search policies") },
                    placeholder = { Text("e.g. water, housing, potholes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))

                if (state.error != null) {
                    Text(state.error, color = Red, modifier = Modifier.padding(bottom = 12.dp))
                }
            }

            if (visible.isEmpty()) {
                item { Text("No policies match your search.", color = TextLow, modifier = Modifier.padding(vertical = 16.dp)) }
            } else {
                items(visible, key = { it.slug }) { policy -> PolicyCard(policy) { onOpenPolicy(policy.slug) } }
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
            Text("View details →", style = MaterialTheme.typography.labelLarge, color = Navy, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
