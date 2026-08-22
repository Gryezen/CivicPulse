package com.gryezen.civicpulse.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.gryezen.civicpulse.CivicPulseApp
import com.gryezen.civicpulse.ui.screens.account.AccountViewModel
import com.gryezen.civicpulse.ui.screens.admin.AdminViewModel
import com.gryezen.civicpulse.ui.screens.auth.AuthViewModel
import com.gryezen.civicpulse.ui.screens.complaint.FileComplaintViewModel
import com.gryezen.civicpulse.ui.screens.dashboard.DashboardViewModel
import com.gryezen.civicpulse.ui.screens.officer.OfficerViewModel
import com.gryezen.civicpulse.ui.screens.policy.PolicyViewModel
import com.gryezen.civicpulse.ui.screens.smsdemo.SmsDemoViewModel
import com.gryezen.civicpulse.ui.screens.track.TrackViewModel

/** Hand-rolled factory — mirrors the manual DI container in [CivicPulseApp]. */
class CivicPulseViewModelFactory(private val app: CivicPulseApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return when (modelClass) {
            AuthViewModel::class.java -> AuthViewModel(app.authRepository) as T
            DashboardViewModel::class.java -> DashboardViewModel(app.complaintRepository, app.policyRepository, app.preferencesManager) as T
            FileComplaintViewModel::class.java -> FileComplaintViewModel(app.complaintRepository, app.preferencesManager) as T
            TrackViewModel::class.java -> TrackViewModel(app.complaintRepository) as T
            AccountViewModel::class.java -> AccountViewModel(app.authRepository, app.preferencesManager) as T
            PolicyViewModel::class.java -> PolicyViewModel(app.policyRepository) as T
            OfficerViewModel::class.java -> OfficerViewModel(app.officerRepository) as T
            AdminViewModel::class.java -> AdminViewModel(app.adminRepository) as T
            SmsDemoViewModel::class.java -> SmsDemoViewModel(app.ivrRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
