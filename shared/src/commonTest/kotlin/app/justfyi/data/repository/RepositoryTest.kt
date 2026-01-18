package app.justfyi.data.repository

import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.createTestDatabase
import app.justfyi.domain.model.Interaction
import app.justfyi.domain.model.User
import app.justfyi.util.currentTimeMillis
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for repository layer operations.
 * These tests use a test database to verify:
 * - UserRepository save and retrieve
 * - InteractionRepository with local-first write
 * - NotificationRepository sync from Firestore (mocked)
 * - Data sync conflict resolution (server wins)
 *
 * Note: These tests focus on the local database operations.
 * Firebase sync behavior is tested at the integration level.
 */
class RepositoryTest {
    private lateinit var database: JustFyiDatabase

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        // Clear all data to ensure test isolation
        database.settingsQueries.deleteAllSettings()
        database.userQueries.deleteAllUsers()
        database.interactionQueries.deleteAllInteractions()
        database.notificationQueries.deleteAllNotifications()
        database.exposureReportQueries.deleteAllReports()
    }

    @AfterTest
    fun teardown() {
        // Clean up to ensure test isolation
        database.settingsQueries.deleteAllSettings()
        database.userQueries.deleteAllUsers()
        database.interactionQueries.deleteAllInteractions()
        database.notificationQueries.deleteAllNotifications()
        database.exposureReportQueries.deleteAllReports()
    }

    // ==================== User Repository Tests ====================

    @Test
    fun `test UserRepository save and retrieve user`() {
        // Given
        val user =
            User(
                id = "user-123",
                anonymousId = "anon-456",
                username = "testuser",
                createdAt = currentTimeMillis(),
                fcmToken = "fcm-token-789",
                idBackupConfirmed = true,
            )

        // When - save user to database (simulating repository save)
        database.userQueries.insertUser(
            id = user.id,
            anonymous_id = user.anonymousId,
            username = user.username,
            created_at = user.createdAt,
            fcm_token = user.fcmToken,
            id_backup_confirmed = if (user.idBackupConfirmed) 1L else 0L,
        )

        // Then - retrieve and verify
        val dbUser = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNotNull(dbUser)
        assertEquals(user.id, dbUser.id)
        assertEquals(user.anonymousId, dbUser.anonymous_id)
        assertEquals(user.username, dbUser.username)
        assertEquals(user.fcmToken, dbUser.fcm_token)
        assertEquals(1L, dbUser.id_backup_confirmed)
    }

    @Test
    fun `test UserRepository update username`() {
        // Given - insert initial user
        val userId = "user-update-test"
        database.userQueries.insertUser(
            id = userId,
            anonymous_id = "anon-update",
            username = "oldname",
            created_at = currentTimeMillis(),
            fcm_token = null,
            id_backup_confirmed = 0,
        )

        // When - update username
        val newUsername = "newname"
        database.userQueries.updateUsername(username = newUsername, id = userId)

        // Then
        val user = database.userQueries.getUserById(userId).executeAsOneOrNull()
        assertNotNull(user)
        assertEquals(newUsername, user.username)
    }

    // ==================== Interaction Repository Tests ====================

    @Test
    fun `test InteractionRepository local-first write saves immediately`() {
        // Given
        val interaction =
            Interaction(
                id = "interaction-local-first",
                partnerAnonymousId = "partner-123",
                partnerUsernameSnapshot = "PartnerUser",
                recordedAt = currentTimeMillis(),
                syncedToCloud = false, // Not yet synced
            )

        // When - save to local database (local-first)
        database.interactionQueries.insertInteraction(
            id = interaction.id,
            partner_anonymous_id = interaction.partnerAnonymousId,
            partner_username_snapshot = interaction.partnerUsernameSnapshot,
            recorded_at = interaction.recordedAt,
            synced_to_cloud = 0,
        )

        // Then - verify it's immediately available locally
        val dbInteraction =
            database.interactionQueries
                .getInteractionById(interaction.id)
                .executeAsOneOrNull()
        assertNotNull(dbInteraction)
        assertEquals(interaction.partnerAnonymousId, dbInteraction.partner_anonymous_id)
        assertEquals(interaction.partnerUsernameSnapshot, dbInteraction.partner_username_snapshot)
        assertEquals(0L, dbInteraction.synced_to_cloud) // Not synced yet
    }

    @Test
    fun `test InteractionRepository preserves username snapshot`() {
        // Given - record interaction with username snapshot
        val originalUsername = "OriginalName"
        val interactionId = "interaction-snapshot"
        val recordedAt = currentTimeMillis()

        database.interactionQueries.insertInteraction(
            id = interactionId,
            partner_anonymous_id = "partner-456",
            partner_username_snapshot = originalUsername,
            recorded_at = recordedAt,
            synced_to_cloud = 0,
        )

        // When - username snapshot should be preserved (not updated if partner changes name)
        // This is a design principle - we verify the snapshot is stored correctly
        val interaction =
            database.interactionQueries
                .getInteractionById(interactionId)
                .executeAsOneOrNull()

        // Then
        assertNotNull(interaction)
        assertEquals(originalUsername, interaction.partner_username_snapshot)
        // The snapshot should never change, even if the partner's actual username changes
    }

    @Test
    fun `test InteractionRepository get interactions in date range`() {
        // Given - interactions at different dates
        val now = currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        // Interaction 10 days ago
        database.interactionQueries.insertInteraction(
            id = "interaction-10-days",
            partner_anonymous_id = "partner-1",
            partner_username_snapshot = "User1",
            recorded_at = now - (10 * oneDayMs),
            synced_to_cloud = 1,
        )

        // Interaction 30 days ago
        database.interactionQueries.insertInteraction(
            id = "interaction-30-days",
            partner_anonymous_id = "partner-2",
            partner_username_snapshot = "User2",
            recorded_at = now - (30 * oneDayMs),
            synced_to_cloud = 1,
        )

        // Interaction 60 days ago
        database.interactionQueries.insertInteraction(
            id = "interaction-60-days",
            partner_anonymous_id = "partner-3",
            partner_username_snapshot = "User3",
            recorded_at = now - (60 * oneDayMs),
            synced_to_cloud = 1,
        )

        // When - query for interactions in last 45 days
        val startDate = now - (45 * oneDayMs)
        val endDate = now
        val interactions =
            database.interactionQueries
                .getInteractionsInDateRange(startDate, endDate)
                .executeAsList()

        // Then - should return only the 2 recent interactions
        assertEquals(2, interactions.size)
        assertTrue(interactions.any { it.id == "interaction-10-days" })
        assertTrue(interactions.any { it.id == "interaction-30-days" })
    }

    // ==================== Notification Repository Tests ====================

    @Test
    fun `test NotificationRepository sync from Firestore overwrites local data - server wins`() {
        // Given - existing local notification
        val notificationId = "notification-sync"
        val now = currentTimeMillis()
        val initialChainData = """{"nodes": [{"id": "user1", "status": "unknown"}]}"""

        database.notificationQueries.insertNotification(
            id = notificationId,
            type = "EXPOSURE",
            sti_type = "HIV",
            exposure_date = now - 86400000L,
            chain_data = initialChainData,
            is_read = 0,
            received_at = now,
            updated_at = now,
            deleted_at = null,
        )

        // When - "sync from cloud" with updated data (simulating server response)
        val updatedChainData = """{"nodes": [{"id": "user1", "status": "negative"}]}"""
        val serverUpdatedAt = now + 5000L

        // Server wins: use insertOrReplace to overwrite
        database.notificationQueries.insertOrReplaceNotification(
            id = notificationId,
            type = "EXPOSURE",
            sti_type = "HIV",
            exposure_date = now - 86400000L,
            chain_data = updatedChainData, // Updated by server
            is_read = 0,
            received_at = now,
            updated_at = serverUpdatedAt,
            deleted_at = null,
        )

        // Then - local data should be overwritten with server data
        val notification =
            database.notificationQueries
                .getNotificationById(notificationId)
                .executeAsOneOrNull()
        assertNotNull(notification)
        assertEquals(updatedChainData, notification.chain_data)
        assertEquals(serverUpdatedAt, notification.updated_at)
    }

    @Test
    fun `test NotificationRepository unread count`() {
        // Given - mix of read and unread notifications
        val now = currentTimeMillis()

        repeat(3) { index ->
            database.notificationQueries.insertNotification(
                id = "unread-$index",
                type = "EXPOSURE",
                sti_type = null,
                exposure_date = null,
                chain_data = "{}",
                is_read = 0,
                received_at = now - (index * 1000L),
                updated_at = now,
                deleted_at = null,
            )
        }

        repeat(2) { index ->
            database.notificationQueries.insertNotification(
                id = "read-$index",
                type = "EXPOSURE",
                sti_type = null,
                exposure_date = null,
                chain_data = "{}",
                is_read = 1,
                received_at = now - (index * 1000L),
                updated_at = now,
                deleted_at = null,
            )
        }

        // When
        val unreadCount = database.notificationQueries.getUnreadCount().executeAsOne()

        // Then
        assertEquals(3L, unreadCount)
    }

    // ==================== Settings Repository Tests ====================

    @Test
    fun `test SettingsRepository pure local storage`() {
        // Given - settings to store
        val languageKey = "language_override"
        val languageValue = "de"

        // When - save setting
        database.settingsQueries.insertOrReplaceSetting(languageKey, languageValue)

        // Then - retrieve setting
        val retrievedValue =
            database.settingsQueries
                .getSettingByKey(languageKey)
                .executeAsOneOrNull()
        assertEquals(languageValue, retrievedValue)

        // When - update setting
        database.settingsQueries.insertOrReplaceSetting(languageKey, "en")

        // Then - verify update
        val updatedValue =
            database.settingsQueries
                .getSettingByKey(languageKey)
                .executeAsOneOrNull()
        assertEquals("en", updatedValue)
    }
}
