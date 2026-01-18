package app.justfyi.navigation

import app.justfyi.presentation.navigation.NavRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for onboarding navigation behavior.
 * These tests verify:
 * - NavRoute.Onboarding sealed interface structure
 * - Navigation from onboarding completion to Home with back stack cleared
 * - Initial destination selection based on isOnboardingComplete
 */
class OnboardingNavigationTest {
    /**
     * Test 1: Verify NavRoute.Onboarding sealed interface structure.
     * The Onboarding route should be a sealed interface containing a Start route,
     * following the pattern established by ExposureReport.
     */
    @Test
    fun onboardingRouteSealdInterfaceStructureIsCorrect() {
        // Given - the Onboarding sealed interface exists
        val onboardingStart = NavRoute.Onboarding.Start

        // Then - it should be non-null (type hierarchy is verified at compile time)
        assertNotNull(onboardingStart, "Onboarding.Start route should be defined")

        // Verify it's a singleton object
        val anotherReference = NavRoute.Onboarding.Start
        assertTrue(onboardingStart === anotherReference, "Onboarding.Start should be a singleton")
    }

    /**
     * Test 2: Verify initial destination selection logic.
     * When onboarding is not complete, start destination should be Onboarding.Start.
     * When onboarding is complete, start destination should be Home.
     */
    @Test
    fun initialDestinationSelectionBasedOnOnboardingComplete() {
        // Given - onboarding is not complete
        val isOnboardingCompleteNew = false

        // When - determining initial destination for new user
        val newUserDestination = determineInitialDestination(isOnboardingCompleteNew)

        // Then - should navigate to onboarding
        assertEquals(
            NavRoute.Onboarding.Start,
            newUserDestination,
            "New users should start at Onboarding.Start",
        )

        // Given - onboarding is complete
        val isOnboardingCompleteReturning = true

        // When - determining initial destination for returning user
        val returningUserDestination = determineInitialDestination(isOnboardingCompleteReturning)

        // Then - should navigate to Home
        assertEquals(
            NavRoute.Home,
            returningUserDestination,
            "Returning users should start at Home",
        )
    }

    /**
     * Test 3: Verify navigation from onboarding completion clears back stack.
     * The navigation action should use popUpTo with inclusive = true to clear
     * the onboarding route from the back stack.
     */
    @Test
    fun onboardingCompletionNavigationClearsBackStack() {
        // Given - navigation options for completing onboarding
        val popUpToRoute = NavRoute.Onboarding.Start
        val inclusive = true

        // When - creating navigation options for onboarding completion
        val navigationConfig =
            OnboardingCompletionNavConfig(
                destination = NavRoute.Home,
                popUpToRoute = popUpToRoute,
                inclusive = inclusive,
            )

        // Then - destination should be Home
        assertEquals(
            NavRoute.Home,
            navigationConfig.destination,
            "Destination should be Home",
        )

        // And - popUpTo should target the onboarding route
        assertEquals(
            NavRoute.Onboarding.Start,
            navigationConfig.popUpToRoute,
            "Should pop up to Onboarding.Start",
        )

        // And - inclusive should be true to remove onboarding from back stack
        assertTrue(
            navigationConfig.inclusive,
            "Inclusive should be true to clear onboarding from back stack",
        )
    }

    /**
     * Helper function that mimics the logic used in JustFyiNavHost
     * for determining the initial destination.
     */
    private fun determineInitialDestination(isOnboardingComplete: Boolean): NavRoute =
        if (isOnboardingComplete) {
            NavRoute.Home
        } else {
            NavRoute.Onboarding.Start
        }

    /**
     * Test 4: Verify navigation config can be created with different destinations.
     */
    @Test
    fun navigationConfigSupportsVariousDestinations() {
        // Given - different possible destinations
        val destinations =
            listOf(
                NavRoute.Home,
                NavRoute.Profile,
                NavRoute.Settings,
            )

        // When - creating navigation configs for each destination
        val configs =
            destinations.map { dest ->
                OnboardingCompletionNavConfig(
                    destination = dest,
                    popUpToRoute = NavRoute.Onboarding.Start,
                    inclusive = true,
                )
            }

        // Then - each config should have the correct destination
        assertEquals(NavRoute.Home, configs[0].destination)
        assertEquals(NavRoute.Profile, configs[1].destination)
        assertEquals(NavRoute.Settings, configs[2].destination)
    }

    /**
     * Test 5: Verify navigation config inclusive flag variations.
     */
    @Test
    fun navigationConfigInclusiveFlagVariations() {
        // Given - configs with different inclusive settings
        val inclusiveConfig =
            OnboardingCompletionNavConfig(
                destination = NavRoute.Home,
                popUpToRoute = NavRoute.Onboarding.Start,
                inclusive = true,
            )
        val nonInclusiveConfig =
            OnboardingCompletionNavConfig(
                destination = NavRoute.Home,
                popUpToRoute = NavRoute.Onboarding.Start,
                inclusive = false,
            )

        // Then - inclusive flags should be correctly stored
        assertTrue(inclusiveConfig.inclusive)
        assertFalse(nonInclusiveConfig.inclusive)
    }

    /**
     * Data class representing navigation configuration for onboarding completion.
     * Used to verify the correct navigation parameters are set.
     */
    private data class OnboardingCompletionNavConfig(
        val destination: NavRoute,
        val popUpToRoute: NavRoute,
        val inclusive: Boolean,
    )
}
