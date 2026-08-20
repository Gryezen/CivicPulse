package com.gryezen.civicpulse.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.CivicPulseApp
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Paper

/**
 * Confirms the session cookie is still valid via GET /api/user/me (the real
 * source of truth) rather than trusting the cached "logged in" hint alone,
 * then hands off to [onResolved].
 */
@Composable
fun SplashScreen(app: CivicPulseApp, onResolved: (loggedIn: Boolean) -> Unit) {
    LaunchedEffect(Unit) {
        val result = app.authRepository.me()
        onResolved(result.isSuccess)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "CivicPulse",
                color = Paper,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Box(Modifier.padding(top = 24.dp)) {
                CircularProgressIndicator(color = Paper)
            }
        }
    }
}
