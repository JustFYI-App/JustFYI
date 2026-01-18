package app.justfyi.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.justfyi.di.NavigationGraph
import app.justfyi.presentation.screens.ExposureReportFlowScreen
import app.justfyi.presentation.screens.HomeScreen
import app.justfyi.presentation.screens.InteractionHistoryScreen
import app.justfyi.presentation.screens.LicenseDetailScreen
import app.justfyi.presentation.screens.LicensesListScreen
import app.justfyi.presentation.screens.NotificationDetailScreen
import app.justfyi.presentation.screens.NotificationListScreen
import app.justfyi.presentation.screens.OnboardingScreen
import app.justfyi.presentation.screens.PlaceholderScreen
import app.justfyi.presentation.screens.ProfileScreen
import app.justfyi.presentation.screens.SettingsScreen
import app.justfyi.presentation.screens.SubmittedReportsScreen
import app.justfyi.util.Logger
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.interaction_detail_id_message
import justfyi.shared.generated.resources.interaction_detail_title
import org.jetbrains.compose.resources.stringResource

/**
 * Main navigation host for the Just FYI application.
 *
 * Uses JetBrains Compose Multiplatform navigation for cross-platform support.
 * The navigation graph includes all main screens and flows:
 * - Home: Nearby user discovery
 * - Profile: User ID and settings
 * - Onboarding: First-time user setup
 * - Notifications: Exposure alerts
 * - History: Recorded interactions
 * - Exposure Report: Report positive test flow
 * - Submitted Reports: View and manage submitted reports
 * - Licenses: Open source library licenses
 *
 * @param graph The NavigationGraph providing ViewModels via Metro DI
 * @param modifier Modifier for the NavHost
 * @param initialNotificationId Optional notification ID for deep linking from FCM notification tap
 * @param onNotificationConsumed Callback invoked after navigating to a notification, to clear the pending state
 */
@Composable
fun JustFyiNavHost(
    graph: NavigationGraph,
    modifier: Modifier = Modifier,
    initialNotificationId: String? = null,
    onNotificationConsumed: () -> Unit = {},
) {
    // State for onboarding check
    var isCheckingOnboarding by remember { mutableStateOf(true) }
    var startDestination by remember { mutableStateOf<NavRoute>(NavRoute.Home) }

    // Check onboarding status before showing any screen
    LaunchedEffect(Unit) {
        val isOnboardingComplete = graph.settingsRepository.isOnboardingComplete()
        Logger.d("JustFyiNavHost", "Onboarding complete: $isOnboardingComplete")

        startDestination =
            if (isOnboardingComplete) {
                NavRoute.Home
            } else {
                NavRoute.Onboarding.Start
            }
        isCheckingOnboarding = false
    }

    // Show loading while checking onboarding status
    if (isCheckingOnboarding) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()

    // Create navigation actions that wrap the NavController
    val navigationActions =
        remember(navController) {
            createNavigationActions(navController)
        }

    // Handle deep linking from notification tap
    LaunchedEffect(initialNotificationId) {
        if (initialNotificationId != null) {
            navController.navigate(NavRoute.NotificationDetail(initialNotificationId))
            onNotificationConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize(),
    ) {
        // =========================================================================
        // Home Screen
        // =========================================================================
        composable<NavRoute.Home> {
            HomeScreen(
                viewModel = viewModel { graph.homeViewModel },
                navigationActions = navigationActions,
            )
        }

        // =========================================================================
        // Profile Screen
        // =========================================================================
        composable<NavRoute.Profile> {
            ProfileScreen(
                viewModel = viewModel { graph.profileViewModel },
                navigationActions = navigationActions,
            )
        }

        // =========================================================================
        // Settings Screen
        // =========================================================================
        composable<NavRoute.Settings> {
            SettingsScreen(
                viewModel = viewModel { graph.settingsViewModel },
                navigationActions = navigationActions,
                onAccountDeleted = {
                    navController.navigate(NavRoute.Onboarding.Start) {
                        popUpTo(NavRoute.Home) { inclusive = true }
                    }
                },
            )
        }

        // =========================================================================
        // Onboarding Flow
        // =========================================================================
        composable<NavRoute.Onboarding.Start> {
            OnboardingScreen(
                viewModel = viewModel { graph.onboardingViewModel },
                navigationActions = navigationActions,
            )
        }

        // =========================================================================
        // Notification Screens
        // =========================================================================
        composable<NavRoute.NotificationList> {
            NotificationListScreen(
                viewModel = viewModel { graph.notificationListViewModel },
                navigationActions = navigationActions,
            )
        }

        composable<NavRoute.NotificationDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<NavRoute.NotificationDetail>()
            val detailViewModel =
                remember(route.notificationId) {
                    graph.notificationDetailViewModelFactory.create(route.notificationId)
                }
            NotificationDetailScreen(
                viewModel = detailViewModel,
                navigationActions = navigationActions,
            )
        }

        // =========================================================================
        // Interaction History
        // =========================================================================
        composable<NavRoute.InteractionHistory> {
            InteractionHistoryScreen(
                viewModel = viewModel { graph.interactionHistoryViewModel },
                navigationActions = navigationActions,
            )
        }

        composable<NavRoute.InteractionDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<NavRoute.InteractionDetail>()
            PlaceholderScreen(
                title = stringResource(Res.string.interaction_detail_title),
                onNavigate = navigationActions,
                message = stringResource(Res.string.interaction_detail_id_message, route.interactionId),
            )
        }

        // =========================================================================
        // Exposure Report Flow
        // =========================================================================
        composable<NavRoute.ExposureReport.StiSelection> {
            ExposureReportFlowScreen(
                viewModel = viewModel { graph.exposureReportFlowViewModel },
                navigationActions = navigationActions,
                onReportComplete = {
                    navController.navigate(NavRoute.Home) {
                        popUpTo(NavRoute.Home) { inclusive = true }
                    }
                },
            )
        }

        // =========================================================================
        // Submitted Reports Screen
        // =========================================================================
        composable<NavRoute.SubmittedReports> {
            SubmittedReportsScreen(
                viewModel = viewModel { graph.submittedReportsViewModel },
                navigationActions = navigationActions,
            )
        }

        // =========================================================================
        // Licenses Screens
        // =========================================================================
        composable<NavRoute.LicensesList> {
            LicensesListScreen(
                viewModel = viewModel { graph.licensesListViewModel },
                navigationActions = navigationActions,
            )
        }

        composable<NavRoute.LicenseDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<NavRoute.LicenseDetail>()
            val detailViewModel =
                remember(route.libraryId) {
                    graph.licenseDetailViewModelFactory.create(route.libraryId)
                }
            LicenseDetailScreen(
                viewModel = detailViewModel,
                navigationActions = navigationActions,
            )
        }
    }
}

/**
 * Creates NavigationActions implementation that wraps a NavController.
 *
 * This provides a clean abstraction for navigation that can be easily mocked
 * in tests and used consistently across the app.
 */
private fun createNavigationActions(navController: NavHostController): NavigationActions =
    object : NavigationActions {
        override fun navigateToHome() {
            navController.navigate(NavRoute.Home) {
                popUpTo(NavRoute.Home) { inclusive = true }
            }
        }

        override fun navigateToProfile() {
            navController.navigate(NavRoute.Profile)
        }

        override fun navigateToInteractionHistory() {
            navController.navigate(NavRoute.InteractionHistory)
        }

        override fun navigateToInteractionDetail(interactionId: String) {
            navController.navigate(NavRoute.InteractionDetail(interactionId))
        }

        override fun navigateToNotificationList() {
            navController.navigate(NavRoute.NotificationList)
        }

        override fun navigateToNotificationDetail(notificationId: String) {
            navController.navigate(NavRoute.NotificationDetail(notificationId))
        }

        override fun navigateToSettings() {
            navController.navigate(NavRoute.Settings)
        }

        override fun navigateToSubmittedReports() {
            navController.navigate(NavRoute.SubmittedReports)
        }

        override fun navigateToLicensesList() {
            navController.navigate(NavRoute.LicensesList)
        }

        override fun navigateToLicenseDetail(libraryId: String) {
            navController.navigate(NavRoute.LicenseDetail(libraryId))
        }

        override fun navigateToOnboarding() {
            navController.navigate(NavRoute.Onboarding.Start)
        }

        override fun completeOnboardingAndNavigateHome() {
            navController.navigate(NavRoute.Home) {
                popUpTo(NavRoute.Onboarding.Start) { inclusive = true }
            }
        }

        override fun navigateToExposureReport() {
            navController.navigate(NavRoute.ExposureReport.StiSelection)
        }

        override fun navigateToExposureReportStep(step: NavRoute.ExposureReport) {
            navController.navigate(step)
        }

        override fun navigateBack() {
            navController.popBackStack()
        }

        override fun popBackTo(
            route: NavRoute,
            inclusive: Boolean,
        ) {
            when (route) {
                is NavRoute.Home -> navController.popBackStack(NavRoute.Home, inclusive)
                is NavRoute.Profile -> navController.popBackStack(NavRoute.Profile, inclusive)
                is NavRoute.Settings -> navController.popBackStack(NavRoute.Settings, inclusive)
                is NavRoute.NotificationList -> navController.popBackStack(NavRoute.NotificationList, inclusive)
                is NavRoute.InteractionHistory -> navController.popBackStack(NavRoute.InteractionHistory, inclusive)
                is NavRoute.SubmittedReports -> navController.popBackStack(NavRoute.SubmittedReports, inclusive)
                is NavRoute.LicensesList -> navController.popBackStack(NavRoute.LicensesList, inclusive)
                is NavRoute.Onboarding.Start -> navController.popBackStack(NavRoute.Onboarding.Start, inclusive)
                else -> navController.popBackStack()
            }
        }
    }
