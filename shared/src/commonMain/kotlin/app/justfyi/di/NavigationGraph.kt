package app.justfyi.di

import app.justfyi.domain.repository.SettingsRepository
import app.justfyi.presentation.feature.exposure.ExposureReportFlowViewModel
import app.justfyi.presentation.feature.history.InteractionHistoryViewModel
import app.justfyi.presentation.feature.home.HomeViewModel
import app.justfyi.presentation.feature.licenses.LicenseDetailViewModel
import app.justfyi.presentation.feature.licenses.LicensesListViewModel
import app.justfyi.presentation.feature.notifications.NotificationDetailViewModel
import app.justfyi.presentation.feature.notifications.NotificationListViewModel
import app.justfyi.presentation.feature.onboarding.OnboardingViewModel
import app.justfyi.presentation.feature.profile.ProfileViewModel
import app.justfyi.presentation.feature.settings.SettingsViewModel
import app.justfyi.presentation.feature.submittedreports.SubmittedReportsViewModel

/**
 * Interface for navigation-layer dependency injection.
 *
 * This interface defines the contract for providing ViewModels to JustFyiNavHost.
 * It is implemented by the Metro-generated AppGraph, which provides all ViewModel
 * instances via @Inject constructor injection.
 *
 * The pattern follows the RevenueCat cat-paywalls-kmp approach where navigation
 * receives a graph interface and uses viewModel { graph.someViewModel } pattern.
 *
 * ViewModels are created fresh on each property access - Compose's viewModel()
 * function handles the lifecycle caching to ensure proper ViewModel retention
 * across configuration changes.
 */
interface NavigationGraph {
    /**
     * SettingsRepository - App settings and onboarding status.
     * Used by JustFyiNavHost to check if onboarding is complete.
     */
    val settingsRepository: SettingsRepository

    // =========================================================================
    // ViewModel Properties
    // These properties expose ViewModels for Compose integration.
    // Each ViewModel is retrieved via viewModel { graph.someViewModel } pattern.
    // Metro generates implementations for these properties using @Inject constructors.
    // =========================================================================

    /**
     * HomeViewModel - Home screen for nearby user discovery.
     */
    val homeViewModel: HomeViewModel

    /**
     * ProfileViewModel - User profile and ID management.
     */
    val profileViewModel: ProfileViewModel

    /**
     * OnboardingViewModel - 4-step onboarding wizard.
     */
    val onboardingViewModel: OnboardingViewModel

    /**
     * InteractionHistoryViewModel - Recorded interaction history.
     */
    val interactionHistoryViewModel: InteractionHistoryViewModel

    /**
     * NotificationListViewModel - Exposure notification list.
     */
    val notificationListViewModel: NotificationListViewModel

    /**
     * SettingsViewModel - App settings and account management.
     */
    val settingsViewModel: SettingsViewModel

    /**
     * ExposureReportFlowViewModel - 6-step exposure report wizard.
     */
    val exposureReportFlowViewModel: ExposureReportFlowViewModel

    /**
     * SubmittedReportsViewModel - View and manage submitted exposure reports.
     */
    val submittedReportsViewModel: SubmittedReportsViewModel

    /**
     * LicensesListViewModel - Open source licenses list.
     */
    val licensesListViewModel: LicensesListViewModel

    // =========================================================================
    // ViewModel Factory Properties
    // For ViewModels requiring runtime parameters.
    // =========================================================================

    /**
     * NotificationDetailViewModel.Factory - Creates NotificationDetailViewModel
     * with runtime notificationId parameter.
     */
    val notificationDetailViewModelFactory: NotificationDetailViewModel.Factory

    /**
     * LicenseDetailViewModel.Factory - Creates LicenseDetailViewModel
     * with runtime libraryId parameter.
     */
    val licenseDetailViewModelFactory: LicenseDetailViewModel.Factory
}
