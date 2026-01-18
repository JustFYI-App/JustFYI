package app.justfyi.navigation

import app.justfyi.presentation.navigation.NavRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for navigation route definitions.
 * These tests verify that all navigation destinations are correctly defined.
 */
class NavRoutesTest {
    /**
     * Test 1: Verify all main navigation routes are defined.
     */
    @Test
    fun mainRoutesAreDefined() {
        // Object routes should be accessible
        val home = NavRoute.Home
        val profile = NavRoute.Profile
        val interactionHistory = NavRoute.InteractionHistory
        val notificationList = NavRoute.NotificationList
        val settings = NavRoute.Settings

        assertNotNull(home, "Home route should be defined")
        assertNotNull(profile, "Profile route should be defined")
        assertNotNull(interactionHistory, "InteractionHistory route should be defined")
        assertNotNull(notificationList, "NotificationList route should be defined")
        assertNotNull(settings, "Settings route should be defined")
    }

    /**
     * Test 2: Verify routes with parameters are correctly structured.
     */
    @Test
    fun parameterizedRoutesWork() {
        val testInteractionId = "test-interaction-123"
        val testNotificationId = "test-notification-456"

        val interactionDetail = NavRoute.InteractionDetail(testInteractionId)
        val notificationDetail = NavRoute.NotificationDetail(testNotificationId)

        assertEquals(testInteractionId, interactionDetail.interactionId)
        assertEquals(testNotificationId, notificationDetail.notificationId)
    }

    /**
     * Test 3: Verify exposure report flow routes are defined.
     */
    @Test
    fun exposureReportFlowRoutesAreDefined() {
        val stiSelection = NavRoute.ExposureReport.StiSelection
        val dateSelection = NavRoute.ExposureReport.DateSelection
        val exposureWindow = NavRoute.ExposureReport.ExposureWindow
        val contactSelection = NavRoute.ExposureReport.ContactSelection
        val privacyOptions = NavRoute.ExposureReport.PrivacyOptions
        val review = NavRoute.ExposureReport.Review

        assertNotNull(stiSelection, "StiSelection route should be defined")
        assertNotNull(dateSelection, "DateSelection route should be defined")
        assertNotNull(exposureWindow, "ExposureWindow route should be defined")
        assertNotNull(contactSelection, "ContactSelection route should be defined")
        assertNotNull(privacyOptions, "PrivacyOptions route should be defined")
        assertNotNull(review, "Review route should be defined")
    }

    /**
     * Test 4: Verify parameterized routes with same parameters are equal.
     */
    @Test
    fun parameterizedRoutesWithSameParametersAreEqual() {
        // Given - routes with the same parameters
        val detail1 = NavRoute.InteractionDetail("interaction-123")
        val detail2 = NavRoute.InteractionDetail("interaction-123")

        val notif1 = NavRoute.NotificationDetail("notification-456")
        val notif2 = NavRoute.NotificationDetail("notification-456")

        // Then - routes with same parameters should be equal
        assertEquals(detail1, detail2, "InteractionDetail with same ID should be equal")
        assertEquals(notif1, notif2, "NotificationDetail with same ID should be equal")
    }

    /**
     * Test 5: Verify parameterized routes with different parameters are not equal.
     */
    @Test
    fun parameterizedRoutesWithDifferentParametersAreNotEqual() {
        // Given - routes with different parameters
        val detail1 = NavRoute.InteractionDetail("interaction-123")
        val detail2 = NavRoute.InteractionDetail("interaction-456")

        val notif1 = NavRoute.NotificationDetail("notification-123")
        val notif2 = NavRoute.NotificationDetail("notification-456")

        // Then - routes with different parameters should not be equal
        assertNotNull(detail1)
        assertNotNull(detail2)
        assertTrue(detail1 != detail2, "InteractionDetail with different IDs should not be equal")
        assertTrue(notif1 != notif2, "NotificationDetail with different IDs should not be equal")
    }

    /**
     * Test 6: Verify parameterized routes handle edge case inputs.
     */
    @Test
    fun parameterizedRoutesHandleEdgeCases() {
        // Given - routes with edge case parameters
        val emptyId = NavRoute.InteractionDetail("")
        val specialCharsId = NavRoute.InteractionDetail("id-with-special_chars.123")
        val unicodeId = NavRoute.NotificationDetail("notification-\u00E9\u00E8\u00EA")

        // Then - routes should store the parameters correctly
        assertEquals("", emptyId.interactionId, "Empty ID should be stored")
        assertEquals("id-with-special_chars.123", specialCharsId.interactionId)
        assertEquals("notification-\u00E9\u00E8\u00EA", unicodeId.notificationId)
    }

    /**
     * Test 7: Verify object routes are singletons.
     */
    @Test
    fun objectRoutesAreSingletons() {
        // Given - multiple references to object routes
        val home1 = NavRoute.Home
        val home2 = NavRoute.Home
        val settings1 = NavRoute.Settings
        val settings2 = NavRoute.Settings

        // Then - they should be the same instance
        assertTrue(home1 === home2, "Home route should be a singleton")
        assertTrue(settings1 === settings2, "Settings route should be a singleton")
    }
}
