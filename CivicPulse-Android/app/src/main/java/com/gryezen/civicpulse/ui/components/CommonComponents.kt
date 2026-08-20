package com.gryezen.civicpulse.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gryezen.civicpulse.data.model.ComplaintStatus
import com.gryezen.civicpulse.ui.theme.Green
import com.gryezen.civicpulse.ui.theme.MonoFamily
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Red
import com.gryezen.civicpulse.ui.theme.Saffron
import com.gryezen.civicpulse.ui.theme.SaffronDim
import com.gryezen.civicpulse.ui.theme.TextLow

/** Mirrors the `.stamp` chips used across dashboard.html / track.html. */
@Composable
fun StatusStamp(status: ComplaintStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        ComplaintStatus.received -> "Received" to Saffron
        ComplaintStatus.processing -> "AI triage" to Navy
        ComplaintStatus.assigned -> "Assigned" to SaffronDim
        ComplaintStatus.resolved -> "Resolved" to Green
    }
    Box(
        modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

enum class PriorityBand(val label: String, val color: Color) {
    URGENT("Urgent", Red),
    HIGH("High", SaffronDim),
    MEDIUM("Medium", Navy),
    LOW("Low", TextLow);

    companion object {
        fun fromScore(score: Int) = when {
            score >= 85 -> URGENT
            score >= 65 -> HIGH
            score >= 40 -> MEDIUM
            else -> LOW
        }
    }
}

/** Bordered pill matching `.ai-priority-badge` / `.qi-priority` on the web build. */
@Composable
fun PriorityBadge(score: Int, modifier: Modifier = Modifier) {
    val band = PriorityBand.fromScore(score)
    Box(
        modifier = modifier
            .border(1.5.dp, band.color, RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${band.label} · $score/100",
            color = band.color,
            fontFamily = MonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

/** A pared-down eyebrow + title header, matching `.eyebrow` + `<h1>` pairs across the site. */
@Composable
fun SectionHeader(eyebrow: String, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = eyebrow.uppercase(),
            fontFamily = MonoFamily,
            fontSize = 11.sp,
            color = TextLow,
            letterSpacing = 1.sp
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextLow)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White),
        shape = RoundedCornerShape(2.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(text)
    }
}

@Composable
fun NavRow(label: String, sublabel: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (sublabel != null) Text(sublabel, style = MaterialTheme.typography.bodyMedium, color = TextLow)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextLow)
    }
}

/**
 * A plain, non-experimental dropdown field: a read-only [OutlinedTextField]
 * with a [DropdownMenu] anchored underneath. Deliberately avoids
 * ExposedDropdownMenuBox/ExposedDropdownMenu/menuAnchor() — that trio's
 * signature has changed across Material3 releases (menuAnchor() went from
 * no-arg to requiring a MenuAnchorType, for one), so this sidesteps the
 * version churn entirely with APIs that have been stable for years.
 */
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        // Drawn last within the Box, so it sits on top and intercepts the
        // tap before the (read-only) text field underneath ever sees it.
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(fraction = 0.9f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
