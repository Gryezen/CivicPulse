package com.gryezen.civicpulse.ui.navigation

/** Mirrors the web app's clean URLs (index/login/complaint/dashboard/track/account). */
sealed class Routes(val route: String) {
    data object Splash : Routes("splash")
    data object Login : Routes("login")
    data object Register : Routes("register")
    data object Dashboard : Routes("dashboard")
    data object FileComplaint : Routes("complaint")
    data object Track : Routes("track")
    data object TrackDetail : Routes("track/{docketId}") {
        fun of(docketId: String) = "track/$docketId"
        const val ARG_DOCKET_ID = "docketId"
    }
    data object Account : Routes("account")
    data object Policy : Routes("policy")
    data object PolicyDetail : Routes("policy/{policySlug}") {
        fun of(slug: String) = "policy/$slug"
        const val ARG_SLUG = "policySlug"
    }
    data object ServerSettings : Routes("server_settings")
    data object OfficerDashboard : Routes("officer")
    data object AdminDashboard : Routes("admin")
}
