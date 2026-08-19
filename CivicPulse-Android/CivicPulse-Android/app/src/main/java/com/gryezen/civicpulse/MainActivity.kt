package com.gryezen.civicpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gryezen.civicpulse.ui.navigation.CivicPulseNavHost
import com.gryezen.civicpulse.ui.theme.CivicPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as CivicPulseApp

        setContent {
            CivicPulseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CivicPulseNavHost(app)
                }
            }
        }
    }
}
