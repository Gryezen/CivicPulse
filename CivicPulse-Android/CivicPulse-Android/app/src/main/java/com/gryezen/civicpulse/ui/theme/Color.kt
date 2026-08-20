package com.gryezen.civicpulse.ui.theme

import androidx.compose.ui.graphics.Color

// Mirrors Frontend/civicpulse/static/style.css custom properties 1:1 so the
// Android app and the deployed site read as the same product.
val Paper = Color(0xFFF5F5F2)
val PaperDim = Color(0xFFEBEAE4)
val Surface = Color(0xFFFFFFFF)
val Hairline = Color(0xFFB8B8B0)
val HairlineDark = Color(0xFF8A8A82)

val Ink = Color(0xFF1A1A1A)
val TextHi = Color(0xFF1A1A1A)
val TextMid = Color(0xFF4A4A46)
val TextLow = Color(0xFF6C6C64)

val Saffron = Color(0xFFFF9933)
val SaffronDim = Color(0xFFCC7A29)
val Green = Color(0xFF0F7A1F)
val GreenDim = Color(0xFF0C5F19)
val Navy = Color(0xFF0B2F8F)
val NavyDark = Color(0xFF081F5E)
val Red = Color(0xFFB3261E)
val RedDim = Color(0xFF8C1E18)

// The site aliases "marigold" to navy (see style.css :root) — the app's
// primary accent follows the same alias so the two stay visually identical.
val Marigold = Navy
val MarigoldDim = NavyDark
