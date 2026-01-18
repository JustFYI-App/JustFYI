package app.justfyi.presentation.navigation

/**
 * Interface for navigation actions that abstracts the NavController.
 * This allows for easier testing by providing a mockable navigation layer.
 */
interface NavigationActions {
    /**
     * Navigate to the home screen.
     */
    fun navigateToHome()

    /**
     * Navigate to the profile screen.
     */
    fun navigateToProfile()

    /**
     * Navigate to the interaction history screen.
     */
    fun navigateToInteractionHistory()

    /**
     * Navigate to the interaction detail screen.
     */
    fun navigateToInteractionDetail(interactionId: String)

    /**
     * Navigate to the notification list screen.
     */
    fun navigateToNotificationList()

    /**
     * Navigate to the notification detail screen.
     */
    fun navigateToNotificationDetail(notificationId: String)

    /**
     * Navigate to the settings screen.
     */
    fun navigateToSettings()

    /**
     * Navigate to the submitted reports screen.
     */
    fun navigateToSubmittedReports()

    /**
     * Navigate to the licenses list screen.
     */
    fun navigateToLicensesList()

    /**
     * Navigate to the license detail screen.
     */
    fun navigateToLicenseDetail(libraryId: String)

    /**
     * Start the onboarding flow.
     */
    fun navigateToOnboarding()

    /**
     * Complete onboarding and navigate to Home.
     * Clears the onboarding flow from the back stack so the user
     * cannot navigate back to onboarding after completion.
     */
    fun completeOnboardingAndNavigateHome()

    /**
     * Start the exposure report flow.
     */
    fun navigateToExposureReport()

    /**
     * Navigate to the next step in the exposure report flow.
     */
    fun navigateToExposureReportStep(step: NavRoute.ExposureReport)

    /**
     * Navigate back to the previous screen.
     */
    fun navigateBack()

    /**
     * Pop the back stack to a specific destination.
     */
    fun popBackTo(
        route: NavRoute,
        inclusive: Boolean = false,
    )
}
