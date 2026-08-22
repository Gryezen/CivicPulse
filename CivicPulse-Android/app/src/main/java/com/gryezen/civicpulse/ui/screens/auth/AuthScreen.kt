package com.gryezen.civicpulse.ui.screens.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gryezen.civicpulse.ui.components.DropdownField
import com.gryezen.civicpulse.ui.components.PrimaryButton
import com.gryezen.civicpulse.ui.theme.Navy
import com.gryezen.civicpulse.ui.theme.Red
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
    onSubmit: (
        name: String, email: String, password: String, region: String, education: String,
        employed: Boolean, occupation: String, language: String,
        role: String, employeeId: String, department: String, verificationCode: String, idDocumentDataUrl: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var education by remember { mutableStateOf(EDUCATION_OPTIONS.first()) }
    var employed by remember { mutableStateOf(true) }
    var occupation by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(LANGUAGE_OPTIONS.first()) }

    // "citizen" (default, plain signup) or "official" — mirrors auth.py's
    // register() two-path official verification (fast-track code, or a
    // document queued for an admin to review). Admin accounts have no
    // self-registration path at all — see admin.py's own docstring.
    var accountType by remember { mutableStateOf("citizen") }
    var employeeId by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var idDocumentDataUrl by remember { mutableStateOf<String?>(null) }
    var resolvingDocument by remember { mutableStateOf(false) }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        resolvingDocument = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { resolveUrisToCacheFiles(context, listOf(uri)) }.firstOrNull()
            idDocumentDataUrl = file?.let { withContext(Dispatchers.IO) { encodeFileAsImageDataUrl(it) } }
            resolvingDocument = false
        }
    }

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
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Account type", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = accountType == "citizen",
                onClick = { accountType = "citizen" },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("Citizen") }
            SegmentedButton(
                selected = accountType == "official",
                onClick = { accountType = "official" },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text("Government official") }
        }

        if (accountType == "official") {
            Spacer(Modifier.height(14.dp))
            Text(
                "Verified either instantly with a department code, or manually by an admin from an uploaded ID document. Neither is real government identity verification — see the website's registration page for the same disclosure.",
                style = MaterialTheme.typography.labelSmall, color = TextLow
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(employeeId, { employeeId = it }, label = { Text("Employee ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(department, { department = it }, label = { Text("Department") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                verificationCode, { verificationCode = it }, label = { Text("Department verification code (if you have one)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            Text("— or —", style = MaterialTheme.typography.labelSmall, color = TextLow)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { documentPicker.launch("image/*") }) {
                Text(
                    when {
                        resolvingDocument -> "Processing document…"
                        idDocumentDataUrl == null -> "Attach an ID document photo for manual review"
                        else -> "ID document attached — tap to change"
                    }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Create account",
            loading = loading,
            enabled = !resolvingDocument,
            onClick = {
                onSubmit(
                    name, email, password, region, education, employed, occupation, language,
                    accountType, employeeId, department, verificationCode, idDocumentDataUrl
                )
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        )
    }
}
