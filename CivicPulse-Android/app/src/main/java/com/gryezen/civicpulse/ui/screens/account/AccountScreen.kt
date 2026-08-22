package com.gryezen.civicpulse.ui.screens.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.ui.components.DropdownField
import com.gryezen.civicpulse.ui.components.GhostButton
import com.gryezen.civicpulse.ui.components.NavRow
import com.gryezen.civicpulse.ui.components.PrimaryButton
import com.gryezen.civicpulse.ui.theme.Green
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Red
import com.gryezen.civicpulse.ui.theme.Saffron
import com.gryezen.civicpulse.ui.theme.TextLow
import com.gryezen.civicpulse.util.encodeFileAsImageDataUrl
import com.gryezen.civicpulse.util.resolveUrisToCacheFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EDUCATION_OPTIONS = listOf(
    "Below 10th", "10th pass", "12th pass", "Diploma", "Undergraduate", "Postgraduate & above"
)
private val LANGUAGE_OPTIONS = listOf(
    "English", "Hindi", "Tamil", "Telugu", "Kannada", "Malayalam",
    "Bengali", "Marathi", "Gujarati", "Punjabi", "Urdu"
)

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onLoggedOut: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onOpenOfficerDashboard: () -> Unit = {},
    onOpenAdminDashboard: () -> Unit = {},
    onOpenSmsDemo: () -> Unit = {}
) {
    val state = viewModel.state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    var employed by remember(user) { mutableStateOf(user?.employed ?: true) }
    var occupation by remember(user) { mutableStateOf(user?.occupation.orEmpty()) }
    var phone by remember(user) { mutableStateOf(user?.phone.orEmpty()) }
    var language by remember(user) { mutableStateOf(user?.language?.takeIf { it.isNotBlank() } ?: LANGUAGE_OPTIONS.first()) }
    var email by remember(user) { mutableStateOf(user?.email.orEmpty()) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Account-type change (citizen<->official) — see AuthRepository.changeAccountType().
    var switchTargetRole by remember(user) { mutableStateOf(if (user?.role == "official") "citizen" else "official") }
    var switchPassword by remember { mutableStateOf("") }
    var switchEmployeeId by remember { mutableStateOf("") }
    var switchDepartment by remember { mutableStateOf("") }
    var switchVerificationCode by remember { mutableStateOf("") }
    var switchIdDocumentDataUrl by remember { mutableStateOf<String?>(null) }
    var resolvingSwitchDocument by remember { mutableStateOf(false) }

    val switchDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        resolvingSwitchDocument = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { resolveUrisToCacheFiles(context, listOf(uri)) }.firstOrNull()
            switchIdDocumentDataUrl = file?.let { withContext(Dispatchers.IO) { encodeFileAsImageDataUrl(it) } }
            resolvingSwitchDocument = false
        }
    }

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

            if (user?.role == "official") {
                Spacer(Modifier.height(6.dp))
                val statusColor = when (user.verificationStatus) {
                    "auto_verified", "approved" -> Green
                    "pending_review" -> Saffron
                    "rejected" -> Red
                    else -> TextLow
                }
                Text(
                    "Official account · ${user.department.ifBlank { "no department" }} · ${user.verificationStatus.replace('_', ' ')}",
                    color = statusColor, style = MaterialTheme.typography.labelMedium
                )
                if (user.verificationStatus == "pending_review") {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { viewModel.resendVerification() }, enabled = !state.resendingVerification) {
                            Text(if (state.resendingVerification) "Resending…" else "Resend verification request")
                        }
                        if (state.resendMessage != null) Text(state.resendMessage, color = Green, style = MaterialTheme.typography.labelSmall)
                    }
                    if (state.resendError != null) Text(state.resendError, color = Red, style = MaterialTheme.typography.labelSmall)
                }
            }

            if (user?.isOfficial == true || user?.isAdmin == true) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                if (user.isOfficial) {
                    NavRow(
                        label = "Officer dashboard",
                        sublabel = "Triage queue, bulk actions, resolve with photo",
                        onClick = onOpenOfficerDashboard
                    )
                }
                if (user.isAdmin) {
                    NavRow(
                        label = "Admin — official verification",
                        sublabel = "Approve or reject pending official accounts",
                        onClick = onOpenAdminDashboard
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            NavRow(
                label = "SMS status-check demo",
                sublabel = "Try the STATUS/HELP command language without a real SMS gateway",
                onClick = onOpenSmsDemo
            )

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

            DropdownField(
                label = "Highest education",
                value = education,
                options = EDUCATION_OPTIONS,
                onSelect = { education = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Currently employed?")
                Switch(checked = employed, onCheckedChange = { employed = it })
            }
            if (employed) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(occupation, { occupation = it }, label = { Text("Current occupation") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                phone, { phone = it }, label = { Text("Phone (optional — links an SMS/IVR status-check channel)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = "Save profile", loading = state.savingProfile,
                    onClick = { viewModel.saveProfile(name, region, education, employed, occupation, phone) }
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

            DropdownField(
                label = "Preferred language",
                value = language,
                options = LANGUAGE_OPTIONS,
                onSelect = { language = it },
                modifier = Modifier.fillMaxWidth()
            )
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
            Spacer(Modifier.height(20.dp))

            Text("Account type", style = MaterialTheme.typography.titleLarge)
            Text(
                if (user?.role == "official") "Switch back to a citizen account, dropping officer access."
                else "Register as a government official — needs a department verification code, or an ID document for an admin to review.",
                color = TextLow, style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(14.dp))

            if (user?.isAdmin != true) {
                if (switchTargetRole == "official") {
                    OutlinedTextField(switchEmployeeId, { switchEmployeeId = it }, label = { Text("Employee ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(switchDepartment, { switchDepartment = it }, label = { Text("Department") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        switchVerificationCode, { switchVerificationCode = it }, label = { Text("Department verification code (if you have one)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("— or —", style = MaterialTheme.typography.labelSmall, color = TextLow)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { switchDocumentPicker.launch("image/*") }) {
                        Text(
                            when {
                                resolvingSwitchDocument -> "Processing document…"
                                switchIdDocumentDataUrl == null -> "Attach an ID document photo for manual review"
                                else -> "ID document attached — tap to change"
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    switchPassword, { switchPassword = it }, label = { Text("Current password (to confirm this change)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
                if (state.accountTypeError != null) {
                    Text(state.accountTypeError, color = Red, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(14.dp))
                GhostButton(
                    text = if (switchTargetRole == "official") "Switch to an official account" else "Switch back to a citizen account",
                    onClick = {
                        viewModel.changeAccountType(
                            targetRole = switchTargetRole,
                            currentPassword = switchPassword,
                            employeeId = switchEmployeeId,
                            department = switchDepartment,
                            verificationCode = switchVerificationCode,
                            idDocumentDataUrl = switchIdDocumentDataUrl
                        )
                    }
                )
            } else {
                Text("Admin accounts can't change their own role here.", color = TextLow, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenServerSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Developer options — server settings")
            }
        }
    }
}
