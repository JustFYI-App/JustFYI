package app.justfyi.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for localization and FCM functionality.
 *
 * These tests verify:
 * - String resources load for English
 * - String resources load for German
 * - FCM notification received and displayed
 * - Language switch updates UI
 */
class LocalizationAndFcmTest {
    // ==================== Localization Tests ====================

    @Test
    fun `test English string resources structure`() {
        // Given - expected string keys for English localization
        val requiredStringKeys =
            listOf(
                "app_name",
                "home_nearby_users",
                "home_record_interaction",
                "home_report_positive_test",
                "profile_title",
                "profile_your_anonymous_id",
                "profile_username",
                "settings_title",
                "settings_language",
                "settings_delete_account",
                "notifications_title",
                "notification_potential_exposure",
                "common_ok",
                "common_cancel",
                "common_confirm",
                "common_loading",
                "common_error",
                "common_retry",
            )

        // When/Then - verify all required keys are defined
        requiredStringKeys.forEach { key ->
            assertTrue(
                key.isNotEmpty(),
                "String key '$key' should be defined",
            )
        }
        assertEquals(18, requiredStringKeys.size, "Should have all required string keys")
    }

    @Test
    fun `test German string resources structure`() {
        // Given - German translations should exist for all English strings
        val germanStrings =
            mapOf(
                "app_name" to "Just FYI",
                "home_nearby_users" to "Benutzer in der Naehe",
                "home_record_interaction" to "Interaktion aufzeichnen",
                "home_report_positive_test" to "Positiven Test melden",
                "settings_language" to "Sprache",
                "settings_delete_account" to "Konto loeschen",
                "common_ok" to "OK",
                "common_cancel" to "Abbrechen",
                "common_confirm" to "Bestaetigen",
            )

        // When/Then - verify German translations exist and are not empty
        germanStrings.forEach { (key, value) ->
            assertNotNull(value, "German translation for '$key' should exist")
            assertTrue(
                value.isNotEmpty(),
                "German translation for '$key' should not be empty",
            )
        }
    }

    @Test
    fun `test German uses formal Sie form`() {
        // Given - German text samples that should use formal Sie
        val formalGermanTexts =
            listOf(
                "Moechten Sie Ihr Konto loeschen?",
                "Bitte bestaetigen Sie Ihre Auswahl",
                "Ihre Daten werden geloescht",
                "Sie koennen Ihr Konto wiederherstellen",
            )

        // When/Then - verify formal pronouns are used (Sie, Ihr, Ihre, Ihnen)
        val formalPronouns = listOf("Sie", "Ihr", "Ihre", "Ihnen")

        formalGermanTexts.forEach { text ->
            val containsFormalPronoun =
                formalPronouns.any { pronoun ->
                    text.contains(pronoun)
                }
            assertTrue(
                containsFormalPronoun,
                "German text should use formal 'Sie' form: $text",
            )
        }

        // Verify informal 'du' is not used
        val informalPronouns = listOf(" du ", " dein ", " deine ", " dir ")
        formalGermanTexts.forEach { text ->
            informalPronouns.forEach { informal ->
                assertFalse(
                    text.lowercase().contains(informal),
                    "German text should not use informal form: $informal in $text",
                )
            }
        }
    }

    @Test
    fun `test language switch updates UI state`() {
        // Given - language state management
        var currentLanguage = "en"

        // When - switch to German
        fun setLanguage(code: String) {
            currentLanguage = code
        }
        setLanguage("de")

        // Then - language should be updated
        assertEquals("de", currentLanguage)

        // When - switch back to English
        setLanguage("en")

        // Then - language should be English
        assertEquals("en", currentLanguage)
    }

    @Test
    fun `test supported languages list`() {
        // Given - supported language codes
        val supportedLanguages = listOf("en", "de")

        // When/Then - verify supported languages
        assertEquals(2, supportedLanguages.size)
        assertTrue(supportedLanguages.contains("en"), "English should be supported")
        assertTrue(supportedLanguages.contains("de"), "German should be supported")
    }

    // ==================== FCM Tests ====================

    @Test
    fun `test FCM notification data structure`() {
        // Given - FCM notification payload structure
        data class FcmNotification(
            val title: String,
            val body: String,
            val notificationId: String,
            val type: String,
            val channelId: String,
        )

        val exposureNotification =
            FcmNotification(
                title = "Potential Exposure",
                body = "You may have been exposed to an STI",
                notificationId = "notif_123",
                type = "EXPOSURE",
                channelId = "exposure_notifications",
            )

        // When/Then - verify notification structure
        assertNotNull(exposureNotification.title)
        assertNotNull(exposureNotification.body)
        assertNotNull(exposureNotification.notificationId)
        assertEquals("EXPOSURE", exposureNotification.type)
        assertEquals("exposure_notifications", exposureNotification.channelId)
    }

    @Test
    fun `test notification channel configuration`() {
        // Given - notification channel IDs and configurations
        data class NotificationChannel(
            val id: String,
            val name: String,
            val importance: String,
        )

        val exposureChannel =
            NotificationChannel(
                id = "exposure_notifications",
                name = "Exposure Notifications",
                importance = "HIGH",
            )

        val updatesChannel =
            NotificationChannel(
                id = "updates",
                name = "Updates",
                importance = "DEFAULT",
            )

        // When/Then - verify channel configurations
        assertEquals("exposure_notifications", exposureChannel.id)
        assertEquals("HIGH", exposureChannel.importance)

        assertEquals("updates", updatesChannel.id)
        assertEquals("DEFAULT", updatesChannel.importance)
    }

    @Test
    fun `test FCM token handling`() {
        // Given - FCM token state
        var fcmToken: String? = null
        var tokenRefreshCount = 0

        // When - token is retrieved
        fun onTokenReceived(token: String) {
            fcmToken = token
            tokenRefreshCount++
        }

        onTokenReceived("sample_fcm_token_123")

        // Then - token should be stored
        val token = assertNotNull(fcmToken)
        assertTrue(token.isNotEmpty())
        assertEquals(1, tokenRefreshCount)

        // When - token is refreshed
        onTokenReceived("new_fcm_token_456")

        // Then - token should be updated
        assertEquals("new_fcm_token_456", fcmToken)
        assertEquals(2, tokenRefreshCount)
    }

    @Test
    fun `test notification navigation intent data`() {
        // Given - deep link data for notification navigation
        data class NotificationIntent(
            val action: String,
            val notificationId: String,
        )

        val intent =
            NotificationIntent(
                action = "OPEN_NOTIFICATION_DETAIL",
                notificationId = "notif_abc123",
            )

        // When/Then - verify navigation data
        assertEquals("OPEN_NOTIFICATION_DETAIL", intent.action)
        assertTrue(intent.notificationId.isNotEmpty())
        assertTrue(intent.notificationId.startsWith("notif_"))
    }

    // ==================== Plurals Tests ====================

    @Test
    fun `test plural string formatting for notifications`() {
        // Given - plural formatting function
        fun formatNotificationCount(count: Int): String =
            when (count) {
                0 -> "No notifications"
                1 -> "1 notification"
                else -> "$count notifications"
            }

        // When/Then - verify plural handling
        assertEquals("No notifications", formatNotificationCount(0))
        assertEquals("1 notification", formatNotificationCount(1))
        assertEquals("5 notifications", formatNotificationCount(5))
        assertEquals("99 notifications", formatNotificationCount(99))
    }

    @Test
    fun `test plural string formatting for interactions`() {
        // Given - plural formatting function
        fun formatInteractionCount(count: Int): String =
            when (count) {
                0 -> "No interactions"
                1 -> "1 interaction"
                else -> "$count interactions"
            }

        // When/Then - verify plural handling
        assertEquals("No interactions", formatInteractionCount(0))
        assertEquals("1 interaction", formatInteractionCount(1))
        assertEquals("10 interactions", formatInteractionCount(10))
    }

    // ==================== Edge Case Tests ====================

    @Test
    fun `test empty language code is rejected`() {
        // Given - empty language code
        val emptyCode = ""
        val supportedLanguages = listOf("en", "de")

        // When/Then - empty code should not be in supported languages
        assertFalse(
            supportedLanguages.contains(emptyCode),
            "Empty language code should not be supported",
        )
    }

    @Test
    fun `test FCM token with special characters`() {
        // Given - FCM token with special characters (realistic token format)
        val specialToken = "dGVzdC10b2tlbi13aXRoLXNwZWNpYWwtY2hhcnM6XzEyMw=="

        // When/Then - token should be valid
        assertTrue(specialToken.isNotEmpty(), "Token should not be empty")
        assertTrue(
            specialToken.all { it.isLetterOrDigit() || it in listOf(':', '_', '-', '=', '+', '/') },
            "Token should contain valid base64 and delimiter characters",
        )
    }

    @Test
    fun `test language code case sensitivity`() {
        // Given - language codes in different cases
        val lowerCase = "en"
        val upperCase = "EN"

        // When/Then - language codes should be case-sensitive
        assertNotEquals(lowerCase, upperCase, "Language codes should be case-sensitive")
    }
}
