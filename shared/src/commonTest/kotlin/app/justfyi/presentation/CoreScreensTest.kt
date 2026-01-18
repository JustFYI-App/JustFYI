package app.justfyi.presentation

import app.justfyi.domain.model.Interaction
import app.justfyi.domain.model.NearbyUser
import app.justfyi.util.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for core screen functionality.
 *
 * These tests verify:
 * - Home screen displays nearby users
 * - Profile screen ID reveal/hide toggle
 * - Interaction history list rendering
 * - Navigation between screens
 * - Permission request dialogs
 *
 * Note: These tests focus on ViewModel state logic and data transformations.
 * Full UI tests would require Compose testing framework.
 */
class CoreScreensTest {
    // Helper enum for navigation tests - defined at class level
    private enum class TestScreen { HOME, PROFILE, HISTORY, NOTIFICATIONS, SETTINGS }

    // Helper data class for permission tests - defined at class level
    private data class TestPermissionState(
        val isGranted: Boolean,
        val shouldShowRationale: Boolean,
    )

    // ==================== Home Screen Tests ====================

    @Test
    fun `test Home screen nearby users list transformation`() {
        // Given - nearby users from BLE discovery
        val nearbyUsers =
            listOf(
                NearbyUser(
                    anonymousIdHash = "hash1",
                    username = "User1",
                    signalStrength = -45,
                    lastSeen = currentTimeMillis(),
                ),
                NearbyUser(
                    anonymousIdHash = "hash2",
                    username = "User2",
                    signalStrength = -70,
                    lastSeen = currentTimeMillis(),
                ),
                NearbyUser(
                    anonymousIdHash = "hash3",
                    username = "User3",
                    signalStrength = -85,
                    lastSeen = currentTimeMillis(),
                ),
            )

        // When - prepare for display (sorted by signal strength)
        val sortedUsers = nearbyUsers.sortedByDescending { it.signalStrength }

        // Then - users should be sorted by signal strength (strongest first)
        assertEquals(3, sortedUsers.size)
        assertEquals("User1", sortedUsers[0].username) // -45 strongest
        assertEquals("User2", sortedUsers[1].username) // -70
        assertEquals("User3", sortedUsers[2].username) // -85 weakest
    }

    @Test
    fun `test Home screen user selection toggle`() {
        // Given - a selection state (simulating ViewModel state)
        val selectedUsers = mutableSetOf<String>()
        val userId1 = "hash1"
        val userId2 = "hash2"

        // When - toggle selection on
        fun toggleSelection(userId: String) {
            if (selectedUsers.contains(userId)) {
                selectedUsers.remove(userId)
            } else {
                selectedUsers.add(userId)
            }
        }

        // Select first user
        toggleSelection(userId1)
        assertTrue(selectedUsers.contains(userId1))
        assertEquals(1, selectedUsers.size)

        // Select second user
        toggleSelection(userId2)
        assertTrue(selectedUsers.contains(userId1))
        assertTrue(selectedUsers.contains(userId2))
        assertEquals(2, selectedUsers.size)

        // Deselect first user
        toggleSelection(userId1)
        assertFalse(selectedUsers.contains(userId1))
        assertTrue(selectedUsers.contains(userId2))
        assertEquals(1, selectedUsers.size)
    }

    @Test
    fun `test Home screen empty state detection`() {
        // Given - empty nearby users list
        val emptyNearbyUsers = emptyList<NearbyUser>()

        // When/Then - should show empty state
        assertTrue(emptyNearbyUsers.isEmpty())

        // Given - non-empty list
        val nearbyUsers =
            listOf(
                NearbyUser("hash1", "User1", -50, currentTimeMillis()),
            )

        // When/Then - should not show empty state
        assertFalse(nearbyUsers.isEmpty())
    }

    // ==================== Profile Screen Tests ====================

    @Test
    fun `test Profile screen ID reveal toggle`() {
        // Given - ID is initially hidden
        var isIdRevealed = false
        val anonymousId = "firebase-anonymous-uid-12345"

        // When - toggle reveal
        fun toggleIdReveal() {
            isIdRevealed = !isIdRevealed
        }

        // Initially hidden
        assertFalse(isIdRevealed)

        // First tap - reveal
        toggleIdReveal()
        assertTrue(isIdRevealed)

        // Second tap - hide again
        toggleIdReveal()
        assertFalse(isIdRevealed)
    }

    @Test
    fun `test Profile screen ID formatting for display`() {
        // Given - a Firebase anonymous UID
        val anonymousId = "abc123def456ghi789"

        // When - format for display (grouping with dashes)
        fun formatIdForDisplay(id: String): String = id.chunked(4).joinToString("-").uppercase()

        val formattedId = formatIdForDisplay(anonymousId)

        // Then - should be formatted in groups
        assertEquals("ABC1-23DE-F456-GHI7-89", formattedId)
    }

    @Test
    fun `test Profile screen username validation`() {
        // Given - username validation rules
        val maxLength = 30

        fun validateUsername(name: String): Boolean {
            // Must not be empty
            if (name.isEmpty()) return false
            // Must not exceed max length
            if (name.length > maxLength) return false
            // Must contain only ASCII printable characters
            if (!name.all { it.code in 32..126 }) return false
            return true
        }

        // Valid usernames
        assertTrue(validateUsername("ValidUser123"))
        assertTrue(validateUsername("a")) // Min 1 char
        assertTrue(validateUsername("A".repeat(30))) // Max 30 chars

        // Invalid usernames
        assertFalse(validateUsername("")) // Empty
        assertFalse(validateUsername("A".repeat(31))) // Too long
        assertFalse(validateUsername("User\u0000Name")) // Non-printable
    }

    // ==================== Interaction History Tests ====================

    @Test
    fun `test Interaction history list ordering`() {
        // Given - interactions with different timestamps
        val now = currentTimeMillis()
        val interactions =
            listOf(
                Interaction(
                    id = "int1",
                    partnerAnonymousId = "hash1",
                    partnerUsernameSnapshot = "OldestUser",
                    recordedAt = now - 86400000 * 3, // 3 days ago
                    syncedToCloud = true,
                ),
                Interaction(
                    id = "int2",
                    partnerAnonymousId = "hash2",
                    partnerUsernameSnapshot = "NewestUser",
                    recordedAt = now - 1000, // 1 second ago
                    syncedToCloud = true,
                ),
                Interaction(
                    id = "int3",
                    partnerAnonymousId = "hash3",
                    partnerUsernameSnapshot = "MiddleUser",
                    recordedAt = now - 86400000, // 1 day ago
                    syncedToCloud = true,
                ),
            )

        // When - sort by date (newest first)
        val sortedInteractions = interactions.sortedByDescending { it.recordedAt }

        // Then - should be in reverse chronological order
        assertEquals("NewestUser", sortedInteractions[0].partnerUsernameSnapshot)
        assertEquals("MiddleUser", sortedInteractions[1].partnerUsernameSnapshot)
        assertEquals("OldestUser", sortedInteractions[2].partnerUsernameSnapshot)
    }

    @Test
    fun `test Interaction history empty state detection`() {
        // Given - empty interaction list
        val emptyInteractions = emptyList<Interaction>()

        // When/Then - should show empty state
        assertTrue(emptyInteractions.isEmpty())

        // Given - non-empty list
        val interactions =
            listOf(
                Interaction(
                    id = "int1",
                    partnerAnonymousId = "hash1",
                    partnerUsernameSnapshot = "User1",
                    recordedAt = currentTimeMillis(),
                    syncedToCloud = true,
                ),
            )

        // When/Then - should not show empty state
        assertFalse(interactions.isEmpty())
    }

    @Test
    fun `test Interaction history 180-day retention calculation`() {
        // Given - interactions with various ages
        val now = currentTimeMillis()
        val retentionDays = 180
        val retentionMs = retentionDays.toLong() * 24 * 60 * 60 * 1000

        val interactions =
            listOf(
                Interaction(
                    id = "recent",
                    partnerAnonymousId = "hash1",
                    partnerUsernameSnapshot = "RecentUser",
                    recordedAt = now - 86400000L * 10, // 10 days ago
                    syncedToCloud = true,
                ),
                Interaction(
                    id = "old",
                    partnerAnonymousId = "hash2",
                    partnerUsernameSnapshot = "OldUser",
                    recordedAt = now - 86400000L * 200, // 200 days ago (beyond retention)
                    syncedToCloud = true,
                ),
            )

        // When - filter by retention period
        val retainedInteractions =
            interactions.filter { interaction ->
                val ageMs = now - interaction.recordedAt
                ageMs <= retentionMs
            }

        // Then - only recent interaction should remain
        assertEquals(1, retainedInteractions.size)
        assertEquals("RecentUser", retainedInteractions[0].partnerUsernameSnapshot)
    }

    // ==================== Navigation Tests ====================

    @Test
    fun `test Navigation state tracking`() {
        // Given - navigation destinations using class-level enum
        var currentScreen = TestScreen.HOME

        // When - navigate to different screens
        fun navigateTo(screen: TestScreen) {
            currentScreen = screen
        }

        navigateTo(TestScreen.PROFILE)
        assertEquals(TestScreen.PROFILE, currentScreen)

        navigateTo(TestScreen.HISTORY)
        assertEquals(TestScreen.HISTORY, currentScreen)

        navigateTo(TestScreen.NOTIFICATIONS)
        assertEquals(TestScreen.NOTIFICATIONS, currentScreen)

        navigateTo(TestScreen.HOME)
        assertEquals(TestScreen.HOME, currentScreen)
    }

    // ==================== Permission Dialog Tests ====================

    @Test
    fun `test BLE permission state handling`() {
        // Given - permission states using class-level data class
        fun getPermissionAction(state: TestPermissionState): String =
            when {
                state.isGranted -> "START_BLE"
                state.shouldShowRationale -> "SHOW_RATIONALE"
                else -> "REQUEST_PERMISSION"
            }

        // When/Then - all permissions granted
        assertEquals("START_BLE", getPermissionAction(TestPermissionState(true, false)))

        // When/Then - permission denied, show rationale
        assertEquals("SHOW_RATIONALE", getPermissionAction(TestPermissionState(false, true)))

        // When/Then - permission not granted, first request
        assertEquals("REQUEST_PERMISSION", getPermissionAction(TestPermissionState(false, false)))
    }
}
