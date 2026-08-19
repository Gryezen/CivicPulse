package com.gryezen.civicpulse.ui.screens.account

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onLoggedOut: () -> Unit,
    onOpenServerSettings: () -> Unit
) {
    val state = viewModel.state

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Navy) }
        return
    }

    val user = state.user

    var name by remember(user) { mutableStateOf(user?.name.orEmpty()) }
    var region by remember(user) { mutableStateOf(user?.region.orEmpty()) }
    var education by remember(user) { mutableStateOf(user?.education?.takeIf { it.isNotBlank() } ?: EDUCATION_OPTIONS.first()) }
    var educationExpanded by remember { mutableStateOf(false) }
    var employed by remember(user) { mutableStateOf(user?.employed ?: true) }
    var occupation by remember(user) { mutableStateOf(user?.occupation.orEmpty()) }
    var language by remember(user) { mutableStateOf(user?.language?.takeIf { it.isNotBlank() } ?: LANGUAGE_OPTIONS.first()) }
    var languageExpanded by remember { mutableStateOf(false) }
    var email by remember(user) { mutableStateOf(user?.email.orEmpty()) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(Navy, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (user?.name?.firstOrNull() ?: user?.email?.firstOrNull() ?: 'A').uppercaseChar().toString(),
                        color = Color.White, style = MaterialTheme.typography.headlineMedium
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(user?.name?.takeIf { it.isNotBlank() } ?: "Anon Citizen", style = MaterialTheme.typography.titleLarge)
                    Text(user?.email?.takeIf { it.isNotBlank() } ?: "No email on file yet", color = TextLow)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.logout() }) { Text("Log out", color = Red) }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text("Profile details", style = MaterialTheme.typography.titleLarge)
            Text("As per your ID — used to route and follow up on your complaints.", color = TextLow, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(region, { region = it }, label = { Text("Region") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(expanded = educationExpanded, onExpandedChange = { educationExpanded = it }) {
                OutlinedTextField(
                    value = education, onValueChange = {}, readOnly = true,
                    label = { Text("Highest education") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = educationExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = educationExpanded, onDismissRequest = { educationExpanded = false }) {
                    EDUCATION_OPTIONS.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { education = opt; educationExpanded = false }) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Currently employed?")
                Switch(checked = employed, onCheckedChange = { employed = it })
            }
            if (employed) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(occupation, { occupation = it }, label = { Text("Current occupation") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = "Save profile", loading = state.savingProfile,
                    onClick = { viewModel.saveProfile(name, region, education, employed, occupation) }
                )
                if (state.profileSaved) {
                    Text("Saved ✓", color = com.gryezen.civicpulse.ui.theme.Green, modifier = Modifier.padding(start = 12.dp))
                }
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text("Language & communication", style = MaterialTheme.typography.titleLarge)
            Text("Used to pre-fill the complaint form and for status updates.", color = TextLow, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))

            ExposedDropdownMenuBox(expanded = languageExpanded, onExpandedChange = { languageExpanded = it }) {
                OutlinedTextField(
                    value = language, onValueChange = {}, readOnly = true,
                    label = { Text("Preferred language") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                    LANGUAGE_OPTIONS.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { language = opt; languageExpanded = false }) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(text = "Save preference", loading = state.savingLanguage, onClick = { viewModel.saveLanguage(language) })
                if (state.languageSaved) {
                    Text("Saved ✓", color = com.gryezen.civicpulse.ui.theme.Green, modifier = Modifier.padding(start = 12.dp))
                }
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text("Account & security", style = MaterialTheme.typography.titleLarge)
            Text("The email and password you log in with.", color = TextLow, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.saveEmail(email) }) { Text("Update email") }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                currentPassword, { currentPassword = it }, label = { Text("Current password") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                newPassword, { newPassword = it }, label = { Text("New password") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                confirmPassword, { confirmPassword = it }, label = { Text("Confirm new password") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation()
            )

            if (state.passwordError != null) {
                Text(state.passwordError, color = Red, modifier = Modifier.padding(top = 8.dp))
            }
            if (state.passwordSaved) {
                Text("Password updated ✓", color = com.gryezen.civicpulse.ui.theme.Green, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Change password", loading = state.savingPassword,
                onClick = { viewModel.changePassword(currentPassword, newPassword, confirmPassword) }
            )

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenServerSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Developer options — server settings")
            }
        }
    }
}
