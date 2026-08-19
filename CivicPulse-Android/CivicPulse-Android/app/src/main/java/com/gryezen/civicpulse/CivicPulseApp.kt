package com.gryezen.civicpulse

import android.app.Application
import com.gryezen.civicpulse.data.local.PreferencesManager
import com.gryezen.civicpulse.data.remote.ApiClient
import com.gryezen.civicpulse.data.repository.AuthRepository
import com.gryezen.civicpulse.data.repository.ComplaintRepository
import com.gryezen.civicpulse.data.repository.PolicyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * No Hilt/Dagger — the dependency graph here is small (one API client, three
 * repositories, one preferences store) so a hand-rolled container keeps the
 * build simple while the backend contract is still moving.
 */
class CivicPulseApp : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var complaintRepository: ComplaintRepository
        private set
    lateinit var policyRepository: PolicyRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        preferencesManager = PreferencesManager(this)
        apiClient = ApiClient(this)
        authRepository = AuthRepository(apiClient, preferencesManager)
        complaintRepository = ComplaintRepository(apiClient)
        policyRepository = PolicyRepository(apiClient)

        // Keep the OkHttp interceptor's target host in sync with whatever the
        // user has configured (Account > Server settings), including the very
        // first read on process start.
        preferencesManager.baseUrl
            .onEach { apiClient.setBaseUrl(it) }
            .launchIn(appScope)
    }
}
