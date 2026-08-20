package com.gryezen.civicpulse.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.ui.components.DropdownField
import com.gryezen.civicpulse.ui.components.PrimaryButton
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Red
import com.gryezen.civicpulse.ui.theme.TextLow

private val EDUCATION_OPTIONS = listOf(
    "Below 10th", "10th pass", "12th pass", "Diploma", "Undergraduate", "Postgraduate & above"
)
private val LANGUAGE_OPTIONS = listOf(
    "English", "Hindi", "Tamil", "Telugu", "Kannada", "Malayalam",
    "Bengali", "Marathi", "Gujarati", "Punjabi", "Urdu"
)

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthenticated: () -> Unit,
    onTrackWithoutAccount: () -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) } // 0 = login, 1 = register
    val state = viewModel.state

    LaunchedEffect(state.success) {
        if (state.success) onAuthenticated()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text("CivicPulse", style = MaterialTheme.typography.headlineMedium, color = Navy, fontWeight = FontWeight.ExtraBold)
            Text(
                "Your complaint shouldn't need a follow-up call.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextLow,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0; viewModel.consumeError() },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Log in") }
                SegmentedButton(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1; viewModel.consumeError() },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Register") }
            }

            Spacer(Modifier.height(24.dp))

            if (state.error != null) {
                Text(state.error, color = Red, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
            }

            if (tabIndex == 0) {
                LoginForm(loading = state.loading, onSubmit = viewModel::login)
            } else {
                RegisterForm(loading = state.loading, onSubmit = viewModel::register)
            }

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onTrackWithoutAccount, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Just tracking a complaint? Look it up without an account →")
            }
        }
    }
}

@Composable
private fun LoginForm(loading: Boolean, onSubmit: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Log in",
            loading = loading,
            onClick = { onSubmit(email, password) },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        )
    }
}

@Composable
private fun RegisterForm(
    loading: Boolean,
    onSubmit: (name: String, email: String, password: String, region: String, education: String, employed: Boolean, occupation: String, language: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var education by remember { mutableStateOf(EDUCATION_OPTIONS.first()) }
    var employed by remember { mutableStateOf(true) }
    var occupation by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(LANGUAGE_OPTIONS.first()) }

    Column {
        OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            password, { password = it }, label = { Text("Password (6+ characters)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(region, { region = it }, label = { Text("Region — District / City, State") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(14.dp))

        DropdownField(
            label = "Highest education",
            value = education,
            options = EDUCATION_OPTIONS,
            onSelect = { education = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Currently employed?", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = employed, onCheckedChange = { employed = it })
        }

        if (employed) {
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                occupation, { occupation = it },
                label = { Text("Current occupation") },
                placeholder = { Text("e.g. Auto driver, Teacher, Shopkeeper") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }

        Spacer(Modifier.height(14.dp))
        DropdownField(
            label = "Preferred language",
            value = language,
            options = LANGUAGE_OPTIONS,
            onSelect = { language = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Create account",
            loading = loading,
            onClick = { onSubmit(name, email, password, region, education, employed, occupation, language) },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        )
    }
}
