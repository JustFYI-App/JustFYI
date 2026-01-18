package app.justfyi.data.local

import app.justfyi.util.currentTimeMillis
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for JustFyiDatabase operations.
 * Uses an in-memory database for fast, isolated tests.
 *
 * These tests cover:
 * - User insert and query
 * - Interaction insert with username snapshot
 * - Notification insert and read status update
 * - Cascading deletes for GDPR compliance
 * - 180-day data query filtering
 */
class JustFyiDatabaseTest {
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

    // ==================== User Tests ====================

    @Test
    fun `test user insert and query`() {
        // Given
        val userId = "user-123"
        val anonymousId = "anon-456"
        val username = "testuser"
        val createdAt = currentTimeMillis()
        val fcmToken = "fcm-token-789"
        val idBackupConfirmed = 1L

        // When
        database.userQueries.insertUser(
            id = userId,
            anonymous_id = anonymousId,
            username = username,
            created_at = createdAt,
            fcm_token = fcmToken,
            id_backup_confirmed = idBackupConfirmed,
        )

        // Then
        val user = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNotNull(user)
        assertEquals(userId, user.id)
        assertEquals(anonymousId, user.anonymous_id)
        assertEquals(username, user.username)
        assertEquals(createdAt, user.created_at)
        assertEquals(fcmToken, user.fcm_token)
        assertEquals(idBackupConfirmed, user.id_backup_confirmed)
    }

    @Test
    fun `test user query by anonymous_id`() {
        // Given
        val anonymousId = "unique-anon-id"
        database.userQueries.insertUser(
            id = "user-1",
            anonymous_id = anonymousId,
            username = "recoveryuser",
            created_at = currentTimeMillis(),
            fcm_token = null,
            id_backup_confirmed = 0,
        )

        // When
        val user = database.userQueries.getUserByAnonymousId(anonymousId).executeAsOneOrNull()

        // Then
        assertNotNull(user)
        assertEquals(anonymousId, user.anonymous_id)
        assertEquals("recoveryuser", user.username)
    }

    // ==================== Interaction Tests ====================

    @Test
    fun `test interaction insert with username snapshot preserved`() {
        // Given
        val interactionId = "interaction-123"
        val partnerAnonymousId = "partner-456"
        val usernameSnapshot = "PartnerUser_AtRecordTime"
        val recordedAt = currentTimeMillis()

        // When
        database.interactionQueries.insertInteraction(
            id = interactionId,
            partner_anonymous_id = partnerAnonymousId,
            partner_username_snapshot = usernameSnapshot,
            recorded_at = recordedAt,
            synced_to_cloud = 0,
        )

        // Then
        val interaction =
            database.interactionQueries
                .getInteractionById(interactionId)
                .executeAsOneOrNull()
        assertNotNull(interaction)
        assertEquals(usernameSnapshot, interaction.partner_username_snapshot)
        assertEquals(partnerAnonymousId, interaction.partner_anonymous_id)
    }

    @Test
    fun `test interaction batch insert and query`() {
        // Given - insert multiple interactions
        val now = currentTimeMillis()
        repeat(5) { index ->
            database.interactionQueries.insertInteraction(
                id = "interaction-$index",
                partner_anonymous_id = "partner-$index",
                partner_username_snapshot = "User$index",
                recorded_at = now - (index * 1000L),
                synced_to_cloud = 0,
            )
        }

        // When
        val interactions = database.interactionQueries.getAllInteractions().executeAsList()

        // Then
        assertEquals(5, interactions.size)
        // Should be ordered by date descending (newest first)
        assertEquals("interaction-0", interactions.first().id)
    }

    // ==================== Notification Tests ====================

    @Test
    fun `test notification insert and read status update`() {
        // Given
        val notificationId = "notification-123"
        val now = currentTimeMillis()
        database.notificationQueries.insertNotification(
            id = notificationId,
            type = "EXPOSURE",
            sti_type = "HIV",
            exposure_date = now - 86400000L, // 1 day ago
            chain_data = """{"chain": ["user1", "user2"]}""",
            is_read = 0,
            received_at = now,
            updated_at = now,
            deleted_at = null,
        )

        // Verify notification is unread
        var notification =
            database.notificationQueries
                .getNotificationById(notificationId)
                .executeAsOneOrNull()
        assertNotNull(notification)
        assertEquals(0L, notification.is_read)

        // When - mark as read
        database.notificationQueries.markAsRead(
            updated_at = now + 1000L,
            id = notificationId,
        )

        // Then
        notification =
            database.notificationQueries
                .getNotificationById(notificationId)
                .executeAsOneOrNull()
        assertNotNull(notification)
        assertEquals(1L, notification.is_read)
    }

    @Test
    fun `test notification unread count query`() {
        // Given
        val now = currentTimeMillis()
        // Insert 3 unread and 2 read notifications
        repeat(3) { index ->
            database.notificationQueries.insertNotification(
                id = "unread-$index",
                type = "EXPOSURE",
                sti_type = null,
                exposure_date = null,
                chain_data = "{}",
                is_read = 0,
                received_at = now,
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
                received_at = now,
                updated_at = now,
                deleted_at = null,
            )
        }

        // When
        val unreadCount = database.notificationQueries.getUnreadCount().executeAsOne()

        // Then
        assertEquals(3L, unreadCount)
    }

    @Test
    fun `test notification chain data update for negative test`() {
        // Given
        val notificationId = "notification-chain"
        val now = currentTimeMillis()
        val initialChainData = """{"nodes": [{"id": "user1", "status": "unknown"}]}"""
        val updatedChainData = """{"nodes": [{"id": "user1", "status": "negative"}]}"""

        database.notificationQueries.insertNotification(
            id = notificationId,
            type = "EXPOSURE",
            sti_type = "Chlamydia",
            exposure_date = now,
            chain_data = initialChainData,
            is_read = 0,
            received_at = now,
            updated_at = now,
            deleted_at = null,
        )

        // When - update chain data with negative test result
        database.notificationQueries.updateChainData(
            chain_data = updatedChainData,
            updated_at = now + 5000L,
            id = notificationId,
        )

        // Then
        val notification =
            database.notificationQueries
                .getNotificationById(notificationId)
                .executeAsOneOrNull()
        assertNotNull(notification)
        assertEquals(updatedChainData, notification.chain_data)
        assertTrue(notification.updated_at > now)
    }

    // ==================== GDPR Compliance Tests ====================

    @Test
    fun `test cascading deletes for GDPR compliance`() {
        // Given - insert data across all tables
        val now = currentTimeMillis()

        database.userQueries.insertUser(
            id = "user-gdpr",
            anonymous_id = "anon-gdpr",
            username = "gdpruser",
            created_at = now,
            fcm_token = "token",
            id_backup_confirmed = 1,
        )

        database.interactionQueries.insertInteraction(
            id = "interaction-gdpr",
            partner_anonymous_id = "partner-1",
            partner_username_snapshot = "Partner1",
            recorded_at = now,
            synced_to_cloud = 1,
        )

        database.notificationQueries.insertNotification(
            id = "notification-gdpr",
            type = "EXPOSURE",
            sti_type = "HIV",
            exposure_date = now,
            chain_data = "{}",
            is_read = 0,
            received_at = now,
            updated_at = now,
            deleted_at = null,
        )

        database.exposureReportQueries.insertReport(
            id = "report-gdpr",
            sti_types = """["HIV"]""",
            test_date = now,
            privacy_level = "FULL",
            contacted_ids = """["partner-1"]""",
            reported_at = now,
            synced_to_cloud = 1,
            test_result = "POSITIVE",
        )

        database.settingsQueries.insertOrReplaceSetting("language_override", "de")

        // When - delete all data for GDPR
        database.userQueries.deleteAllUsers()
        database.interactionQueries.deleteAllInteractions()
        database.notificationQueries.deleteAllNotifications()
        database.exposureReportQueries.deleteAllReports()
        database.settingsQueries.deleteAllSettings()

        // Then - verify all data is deleted
        assertEquals(0L, database.userQueries.userExists().executeAsOne())
        assertEquals(0L, database.interactionQueries.getInteractionCount().executeAsOne())
        assertEquals(0L, database.notificationQueries.getNotificationCount().executeAsOne())
        assertEquals(0L, database.exposureReportQueries.getReportCount().executeAsOne())
        assertNull(database.settingsQueries.getSettingByKey("language_override").executeAsOneOrNull())
    }

    // ==================== 180-Day Retention Tests ====================

    @Test
    fun `test 180-day data query filtering for interactions`() {
        // Given
        val now = currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val cutoffDate = now - (180 * oneDayMs) // 180 days ago

        // Insert interactions: 2 within 180 days, 2 older than 180 days
        database.interactionQueries.insertInteraction(
            id = "recent-1",
            partner_anonymous_id = "partner-1",
            partner_username_snapshot = "Recent1",
            recorded_at = now - (10 * oneDayMs), // 10 days ago
            synced_to_cloud = 1,
        )
        database.interactionQueries.insertInteraction(
            id = "recent-2",
            partner_anonymous_id = "partner-2",
            partner_username_snapshot = "Recent2",
            recorded_at = now - (60 * oneDayMs), // 60 days ago
            synced_to_cloud = 1,
        )
        database.interactionQueries.insertInteraction(
            id = "old-1",
            partner_anonymous_id = "partner-3",
            partner_username_snapshot = "Old1",
            recorded_at = now - (190 * oneDayMs), // 190 days ago
            synced_to_cloud = 1,
        )
        database.interactionQueries.insertInteraction(
            id = "old-2",
            partner_anonymous_id = "partner-4",
            partner_username_snapshot = "Old2",
            recorded_at = now - (250 * oneDayMs), // 250 days ago
            synced_to_cloud = 1,
        )

        // When - query interactions within last 180 days
        val recentInteractions =
            database.interactionQueries
                .getInteractionsWithinDays(cutoffDate)
                .executeAsList()

        // Then
        assertEquals(2, recentInteractions.size)
        assertTrue(recentInteractions.all { it.id.startsWith("recent-") })
    }

    @Test
    fun `test 180-day old data deletion`() {
        // Given
        val now = currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val cutoffDate = now - (180 * oneDayMs)

        // Insert mix of old and new interactions
        database.interactionQueries.insertInteraction(
            id = "keep-1",
            partner_anonymous_id = "p1",
            partner_username_snapshot = "Keep1",
            recorded_at = now - (50 * oneDayMs),
            synced_to_cloud = 1,
        )
        database.interactionQueries.insertInteraction(
            id = "delete-1",
            partner_anonymous_id = "p2",
            partner_username_snapshot = "Delete1",
            recorded_at = now - (200 * oneDayMs), // 200 days ago (beyond 180-day retention)
            synced_to_cloud = 1,
        )

        // When - delete old interactions
        database.interactionQueries.deleteInteractionsOlderThan(cutoffDate)

        // Then
        val remaining = database.interactionQueries.getAllInteractions().executeAsList()
        assertEquals(1, remaining.size)
        assertEquals("keep-1", remaining.first().id)
    }

    // ==================== Settings Tests ====================

    @Test
    fun `test settings insert update and query`() {
        // Given
        val key = "language_override"
        val value = "en"

        // When - insert
        database.settingsQueries.insertOrReplaceSetting(key, value)

        // Then
        var setting = database.settingsQueries.getSettingByKey(key).executeAsOneOrNull()
        assertEquals("en", setting)

        // When - update
        database.settingsQueries.insertOrReplaceSetting(key, "de")

        // Then
        setting = database.settingsQueries.getSettingByKey(key).executeAsOneOrNull()
        assertEquals("de", setting)
    }

    // ==================== ExposureReport Tests ====================

    @Test
    fun `test exposure report insert and pending sync query`() {
        // Given
        val now = currentTimeMillis()

        database.exposureReportQueries.insertReport(
            id = "report-1",
            sti_types = """["HIV", "Syphilis"]""",
            test_date = now - 86400000L,
            privacy_level = "FULL",
            contacted_ids = """["partner-1", "partner-2"]""",
            reported_at = now,
            synced_to_cloud = 0,
            test_result = "POSITIVE",
        )

        database.exposureReportQueries.insertReport(
            id = "report-2",
            sti_types = """["Chlamydia"]""",
            test_date = now - 172800000L,
            privacy_level = "ANONYMOUS",
            contacted_ids = """["partner-3"]""",
            reported_at = now + 1000L,
            synced_to_cloud = 1, // Already synced
            test_result = "POSITIVE",
        )

        // When
        val pendingReports = database.exposureReportQueries.getPendingSyncReports().executeAsList()

        // Then
        assertEquals(1, pendingReports.size)
        assertEquals("report-1", pendingReports.first().id)
    }
}
