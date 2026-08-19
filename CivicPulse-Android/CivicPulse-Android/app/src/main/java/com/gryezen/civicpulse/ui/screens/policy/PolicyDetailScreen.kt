package com.gryezen.civicpulse.ui.screens.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.data.model.Policy
import com.gryezen.civicpulse.data.model.RoadmapStep
import com.gryezen.civicpulse.data.model.RoadmapStepStatus
import com.gryezen.civicpulse.ui.components.PrimaryButton
import com.gryezen.civicpulse.ui.theme.Green
import com.gryezen.civicpulse.ui.theme.GreenDim
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.TextLow

@Composable
fun PolicyDetailScreen(viewModel: PolicyViewModel, slug: String, onFileComplaint: () -> Unit) {
    val state = viewModel.detailState

    LaunchedEffect(slug) { viewModel.loadDetail(slug) }

    Scaffold { padding ->
        if (state.loading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator(color = Navy) }
            return@Scaffold
        }

        if (state.policy == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.padding(top = 60.dp))
                Text("Policy not found", style = MaterialTheme.typography.titleLarge)
                Text(
                    state.error ?: "That link may be out of date.",
                    color = TextLow, modifier = Modifier.padding(top = 8.dp)
                )
            }
            return@Scaffold
        }

        val policy = state.policy
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(policy.source.uppercase(), style = MaterialTheme.typography.labelSmall, color = Navy)
            Text(policy.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
            Text(policy.summary, style = MaterialTheme.typography.bodyLarge, color = TextLow)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(policy.category, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(28.dp))
            Text("Who it's for", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(2.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("ELIGIBILITY", style = MaterialTheme.typography.labelSmall, color = TextLow)
                    Text(policy.eligibility, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Roadmap to implementation", style = MaterialTheme.typography.titleLarge)
            Text(
                "What actually happens after a related complaint or application goes in, step by step.",
                color = TextLow, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            policy.roadmap.forEachIndexed { i, roadmapStep ->
                RoadmapStepRow(roadmapStep, isLast = i == policy.roadmap.lastIndex)
            }

            Spacer(Modifier.height(28.dp))
            Card(
                shape = RoundedCornerShape(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text("Think this applies to you?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "File a complaint mentioning your situation and PolicyGyaan will flag it against schemes like this one automatically.",
                        color = TextLow, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                    )
                    PrimaryButton(text = "File a complaint →", onClick = onFileComplaint)
                }
            }
        }
    }
}

@Composable
private fun RoadmapStepRow(step: RoadmapStep, isLast: Boolean) {
    val (dotColor, phaseColor, statusLabel) = when (step.status) {
        RoadmapStepStatus.done -> Triple(Green, GreenDim, "Usually done for you")
        RoadmapStepStatus.current -> Triple(Navy, Navy, "Where most cases are")
        RoadmapStepStatus.upcoming -> Triple(TextLow, MaterialTheme.colorScheme.onSurface, "Next step")
    }

    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(
                        when (step.status) {
                            RoadmapStepStatus.done -> Green.copy(alpha = 0.08f)
                            RoadmapStepStatus.current -> Navy
                            RoadmapStepStatus.upcoming -> MaterialTheme.colorScheme.surface
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (step.status == RoadmapStepStatus.done) "✓" else "",
                    color = if (step.status == RoadmapStepStatus.current) Color.White else dotColor,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(38.dp)
                        .background(if (step.status == RoadmapStepStatus.done) Green else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Column(Modifier.padding(start = 16.dp, bottom = 26.dp)) {
            Text(statusLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = phaseColor)
            Text(step.phase, style = MaterialTheme.typography.titleMedium, color = phaseColor, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
            Text(step.detail, style = MaterialTheme.typography.bodyMedium, color = TextLow)
        }
    }
}
