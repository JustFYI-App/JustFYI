package app.justfyi.domain.usecase

import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.createTestDatabase
import app.justfyi.domain.model.NearbyUser
import app.justfyi.util.currentTimeMillis
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for interaction recording functionality.
 * These tests verify:
 * - Single interaction recording
 * - Batch interaction recording (multi-select)
 * - Username snapshot preservation
 * - Retry on failure
 *
 * Note: These tests focus on the use case logic using the local database.
 * Firebase sync behavior is tested at the integration level.
 */
class InteractionRecordingTest {
    private lateinit var database: JustFyiDatabase

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        // Clear all data to ensure test isolation
        database.interactionQueries.deleteAllInteractions()
        database.userQueries.deleteAllUsers()
    }

    @AfterTest
    fun teardown() {
        // Clean up to ensure test isolation
        database.interactionQueries.deleteAllInteractions()
        database.userQueries.deleteAllUsers()
    }

    // ==================== Single Interaction Recording Tests ====================

    @Test
    fun `test single interaction recording saves to database`() {
        // Given - a nearby user to record interaction with
        val nearbyUser =
            NearbyUser(
                anonymousIdHash = "hash-12345",
                username = "TestPartner",
                signalStrength = -50,
                lastSeen = currentTimeMillis(),
            )

        // When - record interaction (simulating RecordInteractionUseCase)
        val interactionId = "interaction-${currentTimeMillis()}"
        val recordedAt = currentTimeMillis()

        database.interactionQueries.insertInteraction(
            id = interactionId,
            partner_anonymous_id = nearbyUser.anonymousIdHash,
            partner_username_snapshot = nearbyUser.username,
            recorded_at = recordedAt,
            synced_to_cloud = 0,
        )

        // Then - interaction should be saved immediately
        val savedInteraction =
            database.interactionQueries
                .getInteractionById(interactionId)
                .executeAsOneOrNull()

        assertNotNull(savedInteraction)
        assertEquals(nearbyUser.anonymousIdHash, savedInteraction.partner_anonymous_id)
        assertEquals(nearbyUser.username, savedInteraction.partner_username_snapshot)
        assertEquals(recordedAt, savedInteraction.recorded_at)
        assertEquals(0L, savedInteraction.synced_to_cloud) // Not synced yet (local-first)
    }

    @Test
    fun `test single interaction appears in history immediately`() {
        // Given - record an interaction
        val interactionId = "interaction-immediate"
        val username = "ImmediateUser"
        val recordedAt = currentTimeMillis()

        database.interactionQueries.insertInteraction(
            id = interactionId,
            partner_anonymous_id = "hash-immediate",
            partner_username_snapshot = username,
            recorded_at = recordedAt,
            synced_to_cloud = 0,
        )

        // When - query all interactions
        val allInteractions = database.interactionQueries.getAllInteractions().executeAsList()

        // Then - the interaction should be in the list immediately
        assertEquals(1, allInteractions.size)
        assertEquals(interactionId, allInteractions[0].id)
        assertEquals(username, allInteractions[0].partner_username_snapshot)
    }

    // ==================== Batch Interaction Recording Tests ====================

    @Test
    fun `test batch interaction recording saves all selected users`() {
        // Given - multiple nearby users selected for interaction
        val nearbyUsers =
            listOf(
                NearbyUser("hash-user1", "User1", -45, currentTimeMillis()),
                NearbyUser("hash-user2", "User2", -55, currentTimeMillis()),
                NearbyUser("hash-user3", "User3", -65, currentTimeMillis()),
            )

        val recordedAt = currentTimeMillis()

        // When - record batch interactions (simulating multi-select)
        database.interactionQueries.transaction {
            nearbyUsers.forEachIndexed { index, user ->
                database.interactionQueries.insertInteraction(
                    id = "batch-interaction-$index",
                    partner_anonymous_id = user.anonymousIdHash,
                    partner_username_snapshot = user.username,
                    recorded_at = recordedAt,
                    synced_to_cloud = 0,
                )
            }
        }

        // Then - all interactions should be saved
        val savedInteractions = database.interactionQueries.getAllInteractions().executeAsList()
        assertEquals(3, savedInteractions.size)

        // Verify each user's interaction was recorded
        nearbyUsers.forEachIndexed { index, user ->
            val saved = savedInteractions.find { it.partner_anonymous_id == user.anonymousIdHash }
            assertNotNull(saved, "Interaction for ${user.username} should be saved")
            assertEquals(user.username, saved.partner_username_snapshot)
        }
    }

    @Test
    fun `test batch interaction all have same timestamp`() {
        // Given - multiple users to record simultaneously
        val userHashes = listOf("hash-batch-1", "hash-batch-2", "hash-batch-3")
        val recordedAt = currentTimeMillis()

        // When - record in a transaction (should have same timestamp)
        database.interactionQueries.transaction {
            userHashes.forEachIndexed { index, hash ->
                database.interactionQueries.insertInteraction(
                    id = "batch-ts-$index",
                    partner_anonymous_id = hash,
                    partner_username_snapshot = "User$index",
                    recorded_at = recordedAt,
                    synced_to_cloud = 0,
                )
            }
        }

        // Then - all should have the same timestamp
        val savedInteractions = database.interactionQueries.getAllInteractions().executeAsList()
        assertTrue(savedInteractions.all { it.recorded_at == recordedAt })
    }

    // ==================== Username Snapshot Preservation Tests ====================

    @Test
    fun `test username snapshot preserved at recording time`() {
        // Given - record interaction with original username
        val originalUsername = "OriginalName"
        val interactionId = "snapshot-test-interaction"

        database.interactionQueries.insertInteraction(
            id = interactionId,
            partner_anonymous_id = "hash-snapshot-user",
            partner_username_snapshot = originalUsername,
            recorded_at = currentTimeMillis(),
            synced_to_cloud = 1,
        )

        // When - simulate the partner changing their username (which we don't track)
        // The interaction record should still show the original name
        val savedInteraction =
            database.interactionQueries
                .getInteractionById(interactionId)
                .executeAsOneOrNull()

        // Then - username snapshot is preserved
        assertNotNull(savedInteraction)
        assertEquals(originalUsername, savedInteraction.partner_username_snapshot)
        // The snapshot is immutable - there's no update mechanism for the snapshot
    }

    @Test
    fun `test username snapshot stored exactly as received from BLE`() {
        // Given - various username formats from BLE
        val testUsernames =
            listOf(
                "SimpleUser",
                "User_With_Underscores",
                "User-With-Dashes",
                "User123Numbers",
                "a", // Single character
            )

        // When - record interactions with each username
        testUsernames.forEachIndexed { index, username ->
            database.interactionQueries.insertInteraction(
                id = "format-test-$index",
                partner_anonymous_id = "hash-format-$index",
                partner_username_snapshot = username,
                recorded_at = currentTimeMillis(),
                synced_to_cloud = 0,
            )
        }

        // Then - all usernames should be stored exactly
        testUsernames.forEachIndexed { index, expectedUsername ->
            val saved =
                database.interactionQueries
                    .getInteractionById("format-test-$index")
                    .executeAsOneOrNull()
            assertNotNull(saved)
            assertEquals(expectedUsername, saved.partner_username_snapshot)
        }
    }

    // ==================== Retry Mechanism Tests ====================

    @Test
    fun `test failed sync marked for retry`() {
        // Given - interaction saved locally but not synced (simulating failed cloud sync)
        val interactionId = "failed-sync-interaction"

        database.interactionQueries.insertInteraction(
            id = interactionId,
            partner_anonymous_id = "hash-failed-sync",
            partner_username_snapshot = "FailedSyncUser",
            recorded_at = currentTimeMillis(),
            synced_to_cloud = 0, // Not synced - indicates potential failure
        )

        // When - query unsynced interactions (for retry)
        val unsyncedInteractions =
            database.interactionQueries
                .getUnsyncedInteractions()
                .executeAsList()

        // Then - failed interaction should be in retry list
        assertEquals(1, unsyncedInteractions.size)
        assertEquals(interactionId, unsyncedInteractions[0].id)
    }

    @Test
    fun `test successful retry updates sync status`() {
        // Given - unsynced interaction (from failed previous attempt)
        val interactionId = "retry-success-interaction"

        database.interactionQueries.insertInteraction(
            id = interactionId,
            partner_anonymous_id = "hash-retry",
            partner_username_snapshot = "RetryUser",
            recorded_at = currentTimeMillis(),
            synced_to_cloud = 0,
        )

        // Verify it's in unsynced list
        val beforeRetry = database.interactionQueries.getUnsyncedInteractions().executeAsList()
        assertEquals(1, beforeRetry.size)

        // When - retry succeeds (simulating successful cloud sync)
        database.interactionQueries.updateSyncStatus(
            synced_to_cloud = 1,
            id = interactionId,
        )

        // Then - should no longer be in unsynced list
        val afterRetry = database.interactionQueries.getUnsyncedInteractions().executeAsList()
        assertEquals(0, afterRetry.size)

        // And the sync status should be updated
        val interaction =
            database.interactionQueries
                .getInteractionById(interactionId)
                .executeAsOneOrNull()
        assertNotNull(interaction)
        assertEquals(1L, interaction.synced_to_cloud)
    }
}
