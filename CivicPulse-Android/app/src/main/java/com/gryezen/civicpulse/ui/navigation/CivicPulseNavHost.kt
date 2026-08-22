package com.gryezen.civicpulse.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.getValue
import com.gryezen.civicpulse.CivicPulseApp
import com.gryezen.civicpulse.ui.screens.account.AccountScreen
import com.gryezen.civicpulse.ui.screens.account.AccountViewModel
import com.gryezen.civicpulse.ui.screens.admin.AdminScreen
import com.gryezen.civicpulse.ui.screens.admin.AdminViewModel
import com.gryezen.civicpulse.ui.screens.auth.AuthScreen
import com.gryezen.civicpulse.ui.screens.auth.AuthViewModel
import com.gryezen.civicpulse.ui.screens.complaint.FileComplaintScreen
import com.gryezen.civicpulse.ui.screens.complaint.FileComplaintViewModel
import com.gryezen.civicpulse.ui.screens.dashboard.DashboardScreen
import com.gryezen.civicpulse.ui.screens.dashboard.DashboardViewModel
import com.gryezen.civicpulse.ui.screens.officer.OfficerDashboardScreen
import com.gryezen.civicpulse.ui.screens.officer.OfficerViewModel
import com.gryezen.civicpulse.ui.screens.policy.PolicyDetailScreen
import com.gryezen.civicpulse.ui.screens.policy.PolicyListScreen
import com.gryezen.civicpulse.ui.screens.policy.PolicyViewModel
import com.gryezen.civicpulse.ui.screens.settings.ServerSettingsScreen
import com.gryezen.civicpulse.ui.screens.smsdemo.SmsDemoScreen
import com.gryezen.civicpulse.ui.screens.smsdemo.SmsDemoViewModel
import com.gryezen.civicpulse.ui.screens.splash.SplashScreen
import com.gryezen.civicpulse.ui.screens.track.TrackScreen
import com.gryezen.civicpulse.ui.screens.track.TrackViewModel

private val TAB_ROUTES = setOf(Routes.Dashboard.route, Routes.FileComplaint.route, Routes.Track.route, Routes.Account.route)

@Composable
fun CivicPulseNavHost(app: CivicPulseApp) {
    val navController = rememberNavController()
    val factory = CivicPulseViewModelFactory(app)

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            if (backStackEntry?.destination?.route in TAB_ROUTES) {
                AppBottomBar(navController)
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = Routes.Splash.route) {
                composable(Routes.Splash.route) {
                    SplashScreen(app) { loggedIn ->
                        val target = if (loggedIn) Routes.Dashboard.route else Routes.Login.route
                        navController.navigate(target) {
                            popUpTo(Routes.Splash.route) { inclusive = true }
                        }
                    }
                }

                composable(Routes.Login.route) {
                    val vm: AuthViewModel = viewModel(factory = factory)
                    AuthScreen(
                        viewModel = vm,
                        onAuthenticated = {
                            navController.navigate(Routes.Dashboard.route) {
                                popUpTo(Routes.Login.route) { inclusive = true }
                            }
                        },
                        onTrackWithoutAccount = { navController.navigate(Routes.Track.route) }
                    )
                }

                composable(Routes.Dashboard.route) {
                    val vm: DashboardViewModel = viewModel(factory = factory)
                    DashboardScreen(
                        viewModel = vm,
                        onFileComplaint = { navController.navigate(Routes.FileComplaint.route) },
                        onOpenComplaint = { id -> navController.navigate(Routes.TrackDetail.of(id)) },
                        onOpenPolicy = { slug -> navController.navigate(Routes.PolicyDetail.of(slug)) },
                        onBrowsePolicies = { navController.navigate(Routes.Policy.route) }
                    )
                }

                composable(Routes.FileComplaint.route) {
                    val vm: FileComplaintViewModel = viewModel(factory = factory)
                    FileComplaintScreen(
                        viewModel = vm,
                        onTrackDocket = { id -> navController.navigate(Routes.TrackDetail.of(id)) },
                        onFileAnother = { }
                    )
                }

                composable(Routes.Track.route) {
                    val vm: TrackViewModel = viewModel(factory = factory)
                    TrackScreen(viewModel = vm, onBrowsePolicies = { navController.navigate(Routes.Policy.route) })
                }

                composable(
                    route = Routes.TrackDetail.route,
                    arguments = listOf(navArgument(Routes.TrackDetail.ARG_DOCKET_ID) { })
                ) { backStackEntry ->
                    val vm: TrackViewModel = viewModel(factory = factory)
                    val docketId = backStackEntry.arguments?.getString(Routes.TrackDetail.ARG_DOCKET_ID)
                    TrackScreen(viewModel = vm, initialDocketId = docketId, onBrowsePolicies = { navController.navigate(Routes.Policy.route) })
                }

                composable(Routes.Account.route) {
                    val vm: AccountViewModel = viewModel(factory = factory)
                    AccountScreen(
                        viewModel = vm,
                        onLoggedOut = {
                            navController.navigate(Routes.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onOpenServerSettings = { navController.navigate(Routes.ServerSettings.route) },
                        onOpenOfficerDashboard = { navController.navigate(Routes.OfficerDashboard.route) },
                        onOpenAdminDashboard = { navController.navigate(Routes.AdminDashboard.route) },
                        onOpenSmsDemo = { navController.navigate(Routes.SmsDemo.route) }
                    )
                }

                composable(Routes.OfficerDashboard.route) {
                    val vm: OfficerViewModel = viewModel(factory = factory)
                    OfficerDashboardScreen(viewModel = vm)
                }

                composable(Routes.AdminDashboard.route) {
                    val vm: AdminViewModel = viewModel(factory = factory)
                    AdminScreen(viewModel = vm)
                }

                composable(Routes.SmsDemo.route) {
                    val vm: SmsDemoViewModel = viewModel(factory = factory)
                    val accountVm: AccountViewModel = viewModel(factory = factory)
                    SmsDemoScreen(viewModel = vm, prefillPhone = accountVm.state.user?.phone.orEmpty())
                }

                composable(Routes.ServerSettings.route) {
                    ServerSettingsScreen(app)
                }

                composable(Routes.Policy.route) {
                    val vm: PolicyViewModel = viewModel(factory = factory)
                    PolicyListScreen(viewModel = vm, onOpenPolicy = { slug -> navController.navigate(Routes.PolicyDetail.of(slug)) })
                }

                composable(
                    route = Routes.PolicyDetail.route,
                    arguments = listOf(navArgument(Routes.PolicyDetail.ARG_SLUG) { })
                ) { backStackEntry ->
                    val vm: PolicyViewModel = viewModel(factory = factory)
                    val slug = backStackEntry.arguments?.getString(Routes.PolicyDetail.ARG_SLUG) ?: ""
                    PolicyDetailScreen(
                        viewModel = vm,
                        slug = slug,
                        onFileComplaint = { navController.navigate(Routes.FileComplaint.route) }
                    )
                }
            }
        }
    }
}
