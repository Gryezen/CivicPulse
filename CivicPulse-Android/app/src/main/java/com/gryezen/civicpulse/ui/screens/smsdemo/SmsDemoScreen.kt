package com.gryezen.civicpulse.ui.screens.smsdemo

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.ui.components.PrimaryButton
import com.gryezen.civicpulse.ui.components.SectionHeader
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Red
import com.gryezen.civicpulse.ui.theme.TextLow

/**
 * ivr.py's /api/ivr/demo, rendered as a chat widget — same shape as
 * templates/sms-demo.html. Lets someone try the STATUS / HELP / CONFIRM /
 * DISPUTE command language without a real SMS gateway. Available to any
 * signed-in user (not officer/admin-gated) — matches the website, where
 * /sms-demo is just @login_required.
 */
@Composable
fun SmsDemoScreen(viewModel: SmsDemoViewModel, prefillPhone: String) {
    val state = viewModel.state
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(prefillPhone) {
        if (state.phone.isBlank() && prefillPhone.isNotBlank()) viewModel.setPhone(prefillPhone)
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp, 16.dp)) {
            SectionHeader(
                eyebrow = "Api /api/ivr/demo",
                title = "SMS status-check demo",
                subtitle = "Stands in for a real SMS/IVR gateway — no carrier account involved on either side. Try STATUS, HELP, or STATUS <id>."
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = state.phone,
                onValueChange = { viewModel.setPhone(it) },
                label = { Text("Phone number linked to your account") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))

            if (state.error != null) {
                Text(state.error, color = Red, modifier = Modifier.padding(bottom = 10.dp))
            }

            Card(
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (state.messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Send STATUS or HELP to get started.", color = TextLow, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(state = listState, contentPadding = PaddingValues(14.dp), modifier = Modifier.fillMaxSize()) {
                        items(state.messages) { bubble ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                horizontalArrangement = if (bubble.outgoing) Arrangement.End else Arrangement.Start
                            ) {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (bubble.outgoing) Navy else MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Text(
                                        bubble.text,
                                        modifier = Modifier.padding(12.dp),
                                        color = if (bubble.outgoing) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        if (state.sending) {
                            item {
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.height(18.dp).widthIn(max = 18.dp), color = Navy, strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("STATUS", "HELP").forEach { quick ->
                    TextButton(onClick = { viewModel.send(quick) }) { Text(quick) }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Type a command…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                PrimaryButton(
                    text = "Send",
                    loading = state.sending,
                    onClick = { viewModel.send(draft); draft = "" }
                )
            }
        }
    }
}
