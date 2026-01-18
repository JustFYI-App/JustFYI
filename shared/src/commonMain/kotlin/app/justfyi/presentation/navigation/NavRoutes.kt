package app.justfyi.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the Just FYI application.
 * Each route is a serializable data class or object that represents a destination.
 */
sealed interface NavRoute {
    /**
     * Home screen - displays nearby users and allows interaction recording.
     */
    @Serializable
    data object Home : NavRoute

    /**
     * Profile screen - displays user ID and username management.
     */
    @Serializable
    data object Profile : NavRoute

    /**
     * Interaction history screen - displays recorded interactions.
     */
    @Serializable
    data object InteractionHistory : NavRoute

    /**
     * Interaction detail screen - displays details of a specific interaction.
     */
    @Serializable
    data class InteractionDetail(
        val interactionId: String,
    ) : NavRoute

    /**
     * Notification list screen - displays exposure notifications.
     */
    @Serializable
    data object NotificationList : NavRoute

    /**
     * Notification detail screen - displays details of a specific notification.
     */
    @Serializable
    data class NotificationDetail(
        val notificationId: String,
    ) : NavRoute

    /**
     * Settings screen - app settings and account management.
     */
    @Serializable
    data object Settings : NavRoute

    /**
     * Submitted reports screen - displays user's submitted exposure reports.
     */
    @Serializable
    data object SubmittedReports : NavRoute

    /**
     * Licenses list screen - displays open source libraries used in the app.
     */
    @Serializable
    data object LicensesList : NavRoute

    /**
     * License detail screen - displays details and full license text for a specific library.
     */
    @Serializable
    data class LicenseDetail(
        val libraryId: String,
    ) : NavRoute

    /**
     * Onboarding flow - multi-step flow for first-time user setup.
     * Guides users through ID generation, backup confirmation, permission requests,
     * and optional username setup.
     */
    sealed interface Onboarding : NavRoute {
        /**
         * Entry point for the onboarding flow.
         * Manages all steps internally using ViewModel state.
         */
        @Serializable
        data object Start : Onboarding
    }

    /**
     * Exposure report flow - multi-step flow for reporting positive test.
     */
    sealed interface ExposureReport : NavRoute {
        /**
         * Step 1: Select STI types.
         */
        @Serializable
        data object StiSelection : ExposureReport

        /**
         * Step 2: Select test date.
         */
        @Serializable
        data object DateSelection : ExposureReport

        /**
         * Step 3: View calculated exposure window.
         */
        @Serializable
        data object ExposureWindow : ExposureReport

        /**
         * Step 4: Select contacts to notify.
         */
        @Serializable
        data object ContactSelection : ExposureReport

        /**
         * Step 5: Configure privacy options.
         */
        @Serializable
        data object PrivacyOptions : ExposureReport

        /**
         * Step 6: Review and confirm report.
         */
        @Serializable
        data object Review : ExposureReport
    }
}
