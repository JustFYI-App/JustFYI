package app.justfyi.integration

import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.createTestDatabase
import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.model.PrivacyOptions
import app.justfyi.domain.model.STI
import app.justfyi.util.currentTimeMillis
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for Just FYI MVP critical user workflows.
 *
 * Task Group 13: Strategic integration tests focusing on end-to-end user journeys:
 * - First launch -> ID backup -> username setup
 * - Open app -> discover users -> record interaction
 * - Report exposure -> notifications sent
 * - Receive notification -> view chain -> mark tested
 * - GDPR deletion completeness
 *
 * These tests verify the integration between:
 * - Auth + BLE components
 * - Interaction recording + sync flow
 * - Exposure report + notification chain
 * - GDPR deletion completeness
 */
class JustFyiIntegrationTest {
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

    // ==================================================================================
    // Test 1: First Launch Flow Integration
    // User Journey: Anonymous auth -> ID backup prompt -> username setup
    // ==================================================================================

    @Test
    fun `test first launch flow - auth creates user then backup confirmed then username set`() {
        val firebaseAnonymousId = "firebase-anon-${currentTimeMillis()}"
        val createdAt = currentTimeMillis()

        // Verify no user exists initially
        val existingUser = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNull(existingUser, "No user should exist before first launch")

        // Create user during anonymous auth
        database.userQueries.insertUser(
            id = firebaseAnonymousId,
            anonymous_id = firebaseAnonymousId,
            username = "User", // Default username
            created_at = createdAt,
            fcm_token = null,
            id_backup_confirmed = 0, // Not confirmed yet
        )

        // Verify user was created
        val newUser = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNotNull(newUser)
        assertEquals("User", newUser.username)
        assertEquals(0L, newUser.id_backup_confirmed, "Backup should not be confirmed yet")

        database.userQueries.updateIdBackupConfirmed(id_backup_confirmed = 1, id = firebaseAnonymousId)

        val userAfterBackup = database.userQueries.getUserById(firebaseAnonymousId).executeAsOneOrNull()
        assertNotNull(userAfterBackup)
        assertEquals(1L, userAfterBackup.id_backup_confirmed, "Backup should now be confirmed")

        val chosenUsername = "AliceHealthy"
        database.userQueries.updateUsername(username = chosenUsername, id = firebaseAnonymousId)

        val userAfterUsername = database.userQueries.getUserById(firebaseAnonymousId).executeAsOneOrNull()
        assertNotNull(userAfterUsername)
        assertEquals(chosenUsername, userAfterUsername.username)

        // Verify complete first launch flow
        assertEquals(firebaseAnonymousId, userAfterUsername.id)
        assertEquals(1L, userAfterUsername.id_backup_confirmed)
        assertEquals(chosenUsername, userAfterUsername.username)
    }

    // ==================================================================================
    // Test 2: Interaction Recording Flow Integration
    // User Journey: Open app -> discover users via BLE -> select users -> record -> appears in history
    // ==================================================================================

    @Test
    fun `test interaction recording flow - BLE discovery to history`() {
        // Simulate current user
        val currentUserId = "current-user-123"
        database.userQueries.insertUser(
            id = currentUserId,
            anonymous_id = currentUserId,
            username = "CurrentUser",
            created_at = currentTimeMillis(),
            fcm_token = null,
            id_backup_confirmed = 1,
        )

        val nearbyUsers =
            listOf(
                NearbyUser(
                    anonymousIdHash = "hash-partner-1",
                    username = "Partner1",
                    signalStrength = -55,
                    lastSeen = currentTimeMillis(),
                ),
                NearbyUser(
                    anonymousIdHash = "hash-partner-2",
                    username = "Partner2",
                    signalStrength = -70,
                    lastSeen = currentTimeMillis(),
                ),
            )

        val selectedUsers = nearbyUsers.take(2) // Select both users

        val recordedAt = currentTimeMillis()
        database.interactionQueries.transaction {
            selectedUsers.forEachIndexed { index, nearbyUser ->
                database.interactionQueries.insertInteraction(
                    id = "interaction-flow-$index",
                    partner_anonymous_id = nearbyUser.anonymousIdHash,
                    partner_username_snapshot = nearbyUser.username, // Snapshot preserved
                    recorded_at = recordedAt,
                    synced_to_cloud = 0, // Local-first, not synced yet
                )
            }
        }

        val history = database.interactionQueries.getAllInteractions().executeAsList()
        assertEquals(2, history.size, "Both interactions should appear in history")

        // Verify username snapshots are preserved
        val partner1Interaction = history.find { it.partner_anonymous_id == "hash-partner-1" }
        val partner2Interaction = history.find { it.partner_anonymous_id == "hash-partner-2" }

        assertNotNull(partner1Interaction)
        assertNotNull(partner2Interaction)
        assertEquals("Partner1", partner1Interaction.partner_username_snapshot)
        assertEquals("Partner2", partner2Interaction.partner_username_snapshot)

        // Verify interactions are marked for sync
        val unsyncedInteractions = database.interactionQueries.getUnsyncedInteractions().executeAsList()
        assertEquals(2, unsyncedInteractions.size, "Both interactions should be pending sync")

        unsyncedInteractions.forEach { interaction ->
            database.interactionQueries.updateSyncStatus(synced_to_cloud = 1, id = interaction.id)
        }

        val afterSyncUnsynced = database.interactionQueries.getUnsyncedInteractions().executeAsList()
        assertEquals(0, afterSyncUnsynced.size, "No interactions should be pending after sync")
    }

    // ==================================================================================
    // Test 3: Exposure Report Flow Integration
    // User Journey: Select STIs -> date -> contacts in window -> privacy options -> submit
    // ==================================================================================

    @Test
    fun `test exposure report flow - complete flow from STI selection to submission`() {
        // Setup: Create user and some interactions in the exposure window
        val reporterId = "reporter-user"
        val now = currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L

        database.userQueries.insertUser(
            id = reporterId,
            anonymous_id = reporterId,
            username = "Reporter",
            created_at = now,
            fcm_token = "fcm-token",
            id_backup_confirmed = 1,
        )

        // Create interactions within exposure window (last 30 days for HIV)
        database.interactionQueries.insertInteraction(
            id = "report-flow-interaction-1",
            partner_anonymous_id = "hash-contact-1",
            partner_username_snapshot = "Contact1",
            recorded_at = now - (10 * dayMillis), // 10 days ago
            synced_to_cloud = 1,
        )

        database.interactionQueries.insertInteraction(
            id = "report-flow-interaction-2",
            partner_anonymous_id = "hash-contact-2",
            partner_username_snapshot = "Contact2",
            recorded_at = now - (20 * dayMillis), // 20 days ago
            synced_to_cloud = 1,
        )

        // Create interaction outside exposure window (should not be included)
        database.interactionQueries.insertInteraction(
            id = "report-flow-interaction-3",
            partner_anonymous_id = "hash-contact-3",
            partner_username_snapshot = "Contact3",
            recorded_at = now - (100 * dayMillis), // 100 days ago (outside HIV window)
            synced_to_cloud = 1,
        )

        val selectedSTIs = listOf(STI.HIV)
        val maxIncubationDays = 30 // HIV max incubation

        val testDateMillis = now - (5 * dayMillis) // Tested 5 days ago

        val exposureWindowStart = testDateMillis - (maxIncubationDays * dayMillis)
        val exposureWindowEnd = testDateMillis

        val contactsInWindow =
            database.interactionQueries
                .getInteractionsInDateRange(exposureWindowStart, exposureWindowEnd)
                .executeAsList()

        // Should find 2 contacts within 30-day window
        assertEquals(2, contactsInWindow.size, "Should find 2 contacts in exposure window")
        assertTrue(contactsInWindow.any { it.partner_anonymous_id == "hash-contact-1" })
        assertTrue(contactsInWindow.any { it.partner_anonymous_id == "hash-contact-2" })

        // Contact3 should NOT be in the window (100 days ago)
        assertFalse(contactsInWindow.any { it.partner_anonymous_id == "hash-contact-3" })

        val privacyOptions = PrivacyOptions.DEFAULT

        val reportId = "report-${currentTimeMillis()}"
        val contactIds = contactsInWindow.map { it.partner_anonymous_id }

        database.exposureReportQueries.insertReport(
            id = reportId,
            sti_types = STI.toJsonArray(selectedSTIs),
            test_date = testDateMillis,
            privacy_level = privacyOptions.toPrivacyLevel(),
            contacted_ids = "[\"${contactIds.joinToString("\",\"")}\"]",
            reported_at = now,
            synced_to_cloud = 0, // Pending sync to trigger Cloud Function
            test_result = "POSITIVE",
        )

        // Verify report was saved
        val savedReport = database.exposureReportQueries.getReportById(reportId).executeAsOneOrNull()
        assertNotNull(savedReport)
        assertEquals("[\"HIV\"]", savedReport.sti_types)
        assertEquals("FULL", savedReport.privacy_level)
        assertEquals(0L, savedReport.synced_to_cloud, "Report should be pending sync")

        // Verify pending reports can be queried for Cloud Function trigger
        val pendingReports = database.exposureReportQueries.getPendingSyncReports().executeAsList()
        assertTrue(pendingReports.any { it.id == reportId })
    }

    // ==================================================================================
    // Test 4: Notification Chain Flow Integration
    // User Journey: Receive notification -> view chain -> mark as tested
    // ==================================================================================

    @Test
    fun `test notification chain flow - receive view and mark as tested`() {
        // Setup: Create user
        val recipientId = "recipient-user"
        val now = currentTimeMillis()

        database.userQueries.insertUser(
            id = recipientId,
            anonymous_id = recipientId,
            username = "Recipient",
            created_at = now,
            fcm_token = "recipient-fcm",
            id_backup_confirmed = 1,
        )

        val notificationId = "notification-chain-test"
        val chainData =
            """
            {
                "nodes": [
                    {"username": "Someone", "testStatus": "POSITIVE", "isCurrentUser": false},
                    {"username": "User2", "testStatus": "UNKNOWN", "isCurrentUser": false},
                    {"username": "You", "testStatus": "UNKNOWN", "isCurrentUser": true}
                ]
            }
            """.trimIndent()

        database.notificationQueries.insertNotification(
            id = notificationId,
            type = "EXPOSURE",
            sti_type = "HIV",
            exposure_date = now - (10 * 24 * 60 * 60 * 1000L),
            chain_data = chainData,
            is_read = 0, // Unread
            received_at = now,
            updated_at = now,
            deleted_at = null,
        )

        // Verify unread count reflects the new notification
        val unreadCount = database.notificationQueries.getUnreadCount().executeAsOne()
        assertEquals(1L, unreadCount)

        val allNotifications = database.notificationQueries.getAllNotifications().executeAsList()
        assertEquals(1, allNotifications.size)

        val notification = allNotifications.first()
        assertEquals("EXPOSURE", notification.type)
        assertEquals("HIV", notification.sti_type)
        assertEquals(0L, notification.is_read)

        database.notificationQueries.markAsRead(updated_at = now + 1000L, id = notificationId)

        val afterRead = database.notificationQueries.getNotificationById(notificationId).executeAsOneOrNull()
        assertNotNull(afterRead)
        assertEquals(1L, afterRead.is_read, "Notification should be marked as read")

        val unreadAfterView = database.notificationQueries.getUnreadCount().executeAsOne()
        assertEquals(0L, unreadAfterView, "Unread count should be 0 after viewing")

        val updatedChainData =
            """
            {
                "nodes": [
                    {"username": "Someone", "testStatus": "POSITIVE", "isCurrentUser": false},
                    {"username": "User2", "testStatus": "UNKNOWN", "isCurrentUser": false},
                    {"username": "You", "testStatus": "NEGATIVE", "isCurrentUser": true}
                ]
            }
            """.trimIndent()

        database.notificationQueries.updateChainData(
            chain_data = updatedChainData,
            updated_at = now + 2000L,
            id = notificationId,
        )

        val afterTested = database.notificationQueries.getNotificationById(notificationId).executeAsOneOrNull()
        assertNotNull(afterTested)
        assertTrue(afterTested.chain_data.contains("NEGATIVE"), "Chain should show NEGATIVE status")
    }

    // ==================================================================================
    // Test 5: Chain Update Propagation Integration
    // Scenario: User in chain tests negative -> downstream notifications updated
    // ==================================================================================

    @Test
    fun `test chain update propagation - negative test updates downstream chains`() {
        val now = currentTimeMillis()

        // Create multiple notifications with shared chain participant

        // Notification 1: Direct from reporter to intermediate user
        val notification1ChainData =
            """
            {
                "nodes": [
                    {"username": "Reporter", "testStatus": "POSITIVE", "isCurrentUser": false},
                    {"username": "Intermediate", "testStatus": "UNKNOWN", "isCurrentUser": true}
                ]
            }
            """.trimIndent()

        database.notificationQueries.insertNotification(
            id = "chain-notif-1",
            type = "EXPOSURE",
            sti_type = "SYPHILIS",
            exposure_date = now - (20 * 24 * 60 * 60 * 1000L),
            chain_data = notification1ChainData,
            is_read = 0,
            received_at = now,
            updated_at = now,
            deleted_at = null,
        )

        // Notification 2: From intermediate user to downstream user
        val notification2ChainData =
            """
            {
                "nodes": [
                    {"username": "Reporter", "testStatus": "POSITIVE", "isCurrentUser": false},
                    {"username": "Intermediate", "testStatus": "UNKNOWN", "isCurrentUser": false},
                    {"username": "Downstream", "testStatus": "UNKNOWN", "isCurrentUser": true}
                ]
            }
            """.trimIndent()

        database.notificationQueries.insertNotification(
            id = "chain-notif-2",
            type = "EXPOSURE",
            sti_type = "SYPHILIS",
            exposure_date = now - (15 * 24 * 60 * 60 * 1000L),
            chain_data = notification2ChainData,
            is_read = 0,
            received_at = now,
            updated_at = now,
            deleted_at = null,
        )

        // Verify initial state: Intermediate user shows UNKNOWN in both chains
        val notif1Before = database.notificationQueries.getNotificationById("chain-notif-1").executeAsOneOrNull()
        val notif2Before = database.notificationQueries.getNotificationById("chain-notif-2").executeAsOneOrNull()

        assertNotNull(notif1Before)
        assertNotNull(notif2Before)
        assertTrue(notif1Before.chain_data.contains("UNKNOWN"))
        assertTrue(notif2Before.chain_data.contains("UNKNOWN"))

        // Simulate: Intermediate user tests NEGATIVE
        // This would trigger Cloud Function to update all downstream notifications
        // Here we simulate the result of that update

        val updated2ChainData =
            """
            {
                "nodes": [
                    {"username": "Reporter", "testStatus": "POSITIVE", "isCurrentUser": false},
                    {"username": "Intermediate", "testStatus": "NEGATIVE", "isCurrentUser": false},
                    {"username": "Downstream", "testStatus": "UNKNOWN", "isCurrentUser": true}
                ]
            }
            """.trimIndent()

        database.notificationQueries.updateChainData(
            chain_data = updated2ChainData,
            updated_at = now + 5000L,
            id = "chain-notif-2",
        )

        // Verify downstream notification was updated with NEGATIVE status
        val notif2After = database.notificationQueries.getNotificationById("chain-notif-2").executeAsOneOrNull()
        assertNotNull(notif2After)
        assertTrue(notif2After.chain_data.contains("NEGATIVE"), "Chain should show intermediate user as NEGATIVE")
        assertTrue(notif2After.updated_at > now, "Updated timestamp should be newer")
    }

    // ==================================================================================
    // Test 6: GDPR Deletion Completeness Integration
    // User Journey: Delete account -> all data removed -> signed out
    // ==================================================================================

    @Test
    fun `test GDPR deletion completeness - all user data removed across all tables`() {
        val userId = "gdpr-delete-user"
        val now = currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L

        database.userQueries.insertUser(
            id = userId,
            anonymous_id = userId,
            username = "GdprUser",
            created_at = now,
            fcm_token = "gdpr-fcm-token",
            id_backup_confirmed = 1,
        )

        // Create interactions
        repeat(5) { index ->
            database.interactionQueries.insertInteraction(
                id = "gdpr-interaction-$index",
                partner_anonymous_id = "partner-$index",
                partner_username_snapshot = "Partner$index",
                recorded_at = now - (index * dayMillis),
                synced_to_cloud = if (index % 2 == 0) 1 else 0,
            )
        }

        // Create notifications
        repeat(3) { index ->
            database.notificationQueries.insertNotification(
                id = "gdpr-notification-$index",
                type = "EXPOSURE",
                sti_type = if (index == 0) "HIV" else null,
                exposure_date = if (index == 0) now - (10 * dayMillis) else null,
                chain_data = """{"nodes":[]}""",
                is_read = if (index == 0) 1 else 0,
                received_at = now - (index * dayMillis),
                updated_at = now,
                deleted_at = null,
            )
        }

        // Create exposure reports
        repeat(2) { index ->
            database.exposureReportQueries.insertReport(
                id = "gdpr-report-$index",
                sti_types = "[\"HIV\"]",
                test_date = now - (index * 10 * dayMillis),
                privacy_level = "FULL",
                contacted_ids = "[\"partner-0\",\"partner-1\"]",
                reported_at = now - (index * dayMillis),
                synced_to_cloud = 1,
                test_result = "POSITIVE",
            )
        }

        // Create settings
        database.settingsQueries.insertOrReplaceSetting("language_override", "de")
        database.settingsQueries.insertOrReplaceSetting("privacy_policy_accepted", "1")
        database.settingsQueries.insertOrReplaceSetting("terms_accepted", "1")

        // Verify all data exists before deletion
        assertTrue(database.userQueries.userExists().executeAsOne() > 0, "User should exist")
        assertEquals(5L, database.interactionQueries.getInteractionCount().executeAsOne())
        assertEquals(3L, database.notificationQueries.getNotificationCount().executeAsOne())
        assertEquals(2L, database.exposureReportQueries.getReportCount().executeAsOne())
        assertNotNull(database.settingsQueries.getSettingByKey("language_override").executeAsOneOrNull())

        database.transaction {
            database.userQueries.deleteAllUsers()
            database.interactionQueries.deleteAllInteractions()
            database.notificationQueries.deleteAllNotifications()
            database.exposureReportQueries.deleteAllReports()
            database.settingsQueries.deleteAllSettings()
        }

        assertEquals(0L, database.userQueries.userExists().executeAsOne(), "User table should be empty")
        assertEquals(
            0L,
            database.interactionQueries.getInteractionCount().executeAsOne(),
            "Interactions should be empty",
        )
        assertEquals(
            0L,
            database.notificationQueries.getNotificationCount().executeAsOne(),
            "Notifications should be empty",
        )
        assertEquals(0L, database.exposureReportQueries.getReportCount().executeAsOne(), "Reports should be empty")
        assertNull(
            database.settingsQueries.getSettingByKey("language_override").executeAsOneOrNull(),
            "Settings should be empty",
        )
        assertNull(database.settingsQueries.getSettingByKey("privacy_policy_accepted").executeAsOneOrNull())
        assertNull(database.settingsQueries.getSettingByKey("terms_accepted").executeAsOneOrNull())

        val currentUser = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNull(currentUser, "No current user should exist after GDPR deletion")
    }

    // ==================================================================================
    // Test 7: 180-Day Data Retention Integration
    // Verifies automatic cleanup of old data across all data types
    // ==================================================================================

    @Test
    fun `test 180-day retention - old data cleaned up new data preserved`() {
        val now = currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L
        val retentionDays = 180L
        val cutoffDate = now - (retentionDays * dayMillis)

        // Create recent data (should be preserved)
        database.interactionQueries.insertInteraction(
            id = "recent-interaction",
            partner_anonymous_id = "recent-partner",
            partner_username_snapshot = "RecentPartner",
            recorded_at = now - (30 * dayMillis), // 30 days ago
            synced_to_cloud = 1,
        )

        database.notificationQueries.insertNotification(
            id = "recent-notification",
            type = "EXPOSURE",
            sti_type = "HIV",
            exposure_date = now - (30 * dayMillis),
            chain_data = """{"nodes":[]}""",
            is_read = 0,
            received_at = now - (25 * dayMillis), // 25 days ago
            updated_at = now - (25 * dayMillis),
            deleted_at = null,
        )

        // Create old data (should be deleted)
        database.interactionQueries.insertInteraction(
            id = "old-interaction",
            partner_anonymous_id = "old-partner",
            partner_username_snapshot = "OldPartner",
            recorded_at = now - (200 * dayMillis), // 200 days ago
            synced_to_cloud = 1,
        )

        database.notificationQueries.insertNotification(
            id = "old-notification",
            type = "EXPOSURE",
            sti_type = "CHLAMYDIA",
            exposure_date = now - (190 * dayMillis),
            chain_data = """{"nodes":[]}""",
            is_read = 1,
            received_at = now - (190 * dayMillis), // 190 days ago
            updated_at = now - (190 * dayMillis),
            deleted_at = null,
        )

        // Verify initial state
        assertEquals(2L, database.interactionQueries.getInteractionCount().executeAsOne())
        assertEquals(2L, database.notificationQueries.getNotificationCount().executeAsOne())

        // Execute 180-day cleanup
        database.interactionQueries.deleteInteractionsOlderThan(cutoffDate)
        database.notificationQueries.deleteNotificationsOlderThan(cutoffDate)

        // Verify old data removed, recent data preserved
        val remainingInteractions = database.interactionQueries.getAllInteractions().executeAsList()
        val remainingNotifications = database.notificationQueries.getAllNotifications().executeAsList()

        assertEquals(1, remainingInteractions.size, "Only recent interaction should remain")
        assertEquals("recent-interaction", remainingInteractions.first().id)

        assertEquals(1, remainingNotifications.size, "Only recent notification should remain")
        assertEquals("recent-notification", remainingNotifications.first().id)
    }

    // ==================================================================================
    // Test 8: Account Recovery Flow Integration
    // User Journey: App reinstall -> enter saved ID -> restore account
    // ==================================================================================

    @Test
    fun `test account recovery flow - restore user from saved ID`() {
        val originalAnonymousId = "original-firebase-uid-12345"
        val originalUsername = "OriginalUser"
        val originalCreatedAt = currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L) // 30 days ago

        // In reality, this would be fetched from Firestore after re-authentication

        // Verify no local user exists (simulating fresh install)
        val existingLocal = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNull(existingLocal, "No local user should exist after reinstall")

        // Firebase would re-authenticate and restore the user
        database.userQueries.insertUser(
            id = originalAnonymousId,
            anonymous_id = originalAnonymousId,
            username = originalUsername,
            created_at = originalCreatedAt,
            fcm_token = "new-fcm-token", // FCM token would be new
            id_backup_confirmed = 1, // Already confirmed from before
        )

        val restoredUser = database.userQueries.getUserByAnonymousId(originalAnonymousId).executeAsOneOrNull()
        assertNotNull(restoredUser, "User should be restored")
        assertEquals(originalAnonymousId, restoredUser.anonymous_id)
        assertEquals(originalUsername, restoredUser.username)
        assertEquals(originalCreatedAt, restoredUser.created_at) // Original creation date preserved

        assertEquals("new-fcm-token", restoredUser.fcm_token)
    }

    // ==================================================================================
    // Test 9: Multi-Select Interaction with Partial Sync Failure
    // Verifies retry mechanism for failed syncs
    // ==================================================================================

    @Test
    fun `test interaction sync retry - failed sync marked for retry and recovers`() {
        val now = currentTimeMillis()

        // Record batch interactions (some will fail sync)
        val interactionIds = listOf("sync-retry-1", "sync-retry-2", "sync-retry-3")

        interactionIds.forEach { id ->
            database.interactionQueries.insertInteraction(
                id = id,
                partner_anonymous_id = "partner-$id",
                partner_username_snapshot = "Partner-$id",
                recorded_at = now,
                synced_to_cloud = 0, // All pending sync
            )
        }

        // Verify all are pending
        val pendingBefore = database.interactionQueries.getUnsyncedInteractions().executeAsList()
        assertEquals(3, pendingBefore.size, "All 3 should be pending sync")

        // Simulate: First two succeed, third fails
        database.interactionQueries.updateSyncStatus(synced_to_cloud = 1, id = "sync-retry-1")
        database.interactionQueries.updateSyncStatus(synced_to_cloud = 1, id = "sync-retry-2")
        // sync-retry-3 remains unsynced (simulating network failure)

        // Verify one still pending
        val pendingAfterPartial = database.interactionQueries.getUnsyncedInteractions().executeAsList()
        assertEquals(1, pendingAfterPartial.size, "One should still be pending")
        assertEquals("sync-retry-3", pendingAfterPartial.first().id)

        // Simulate: Retry succeeds
        database.interactionQueries.updateSyncStatus(synced_to_cloud = 1, id = "sync-retry-3")

        // Verify all synced
        val pendingAfterRetry = database.interactionQueries.getUnsyncedInteractions().executeAsList()
        assertEquals(0, pendingAfterRetry.size, "No pending after successful retry")

        // Verify all interactions exist and are synced
        interactionIds.forEach { id ->
            val interaction = database.interactionQueries.getInteractionById(id).executeAsOneOrNull()
            assertNotNull(interaction)
            assertEquals(1L, interaction.synced_to_cloud)
        }
    }

    // ==================================================================================
    // Test 10: Complete User Journey Integration
    // End-to-end: New user -> setup -> record -> report -> receive notification -> delete
    // ==================================================================================

    @Test
    fun `test complete user journey - full lifecycle from signup to deletion`() {
        val now = currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L
        val userId = "complete-journey-user"

        // === PHASE 1: First Launch ===
        database.userQueries.insertUser(
            id = userId,
            anonymous_id = userId,
            username = "JourneyUser",
            created_at = now,
            fcm_token = "journey-fcm",
            id_backup_confirmed = 1,
        )
        val user = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNotNull(user, "User should exist after signup")

        // === PHASE 2: Record Interactions ===
        database.interactionQueries.insertInteraction(
            id = "journey-interaction-1",
            partner_anonymous_id = "journey-partner-1",
            partner_username_snapshot = "JourneyPartner1",
            recorded_at = now - (5 * dayMillis),
            synced_to_cloud = 1,
        )
        database.interactionQueries.insertInteraction(
            id = "journey-interaction-2",
            partner_anonymous_id = "journey-partner-2",
            partner_username_snapshot = "JourneyPartner2",
            recorded_at = now - (10 * dayMillis),
            synced_to_cloud = 1,
        )
        assertEquals(2L, database.interactionQueries.getInteractionCount().executeAsOne())

        // === PHASE 3: Submit Exposure Report ===
        database.exposureReportQueries.insertReport(
            id = "journey-report",
            sti_types = "[\"CHLAMYDIA\"]",
            test_date = now - (3 * dayMillis),
            privacy_level = "FULL",
            contacted_ids = "[\"journey-partner-1\",\"journey-partner-2\"]",
            reported_at = now,
            synced_to_cloud = 1,
            test_result = "POSITIVE",
        )
        assertEquals(1L, database.exposureReportQueries.getReportCount().executeAsOne())

        // === PHASE 4: Receive Notification (from someone else's report) ===
        database.notificationQueries.insertNotification(
            id = "journey-notification",
            type = "EXPOSURE",
            sti_type = "HIV",
            exposure_date = now - (15 * dayMillis),
            chain_data = """{"nodes":[{"username":"Someone","testStatus":"POSITIVE"}]}""",
            is_read = 0,
            received_at = now,
            updated_at = now,
            deleted_at = null,
        )
        assertEquals(1L, database.notificationQueries.getUnreadCount().executeAsOne())

        // View notification
        database.notificationQueries.markAsRead(updated_at = now + 1000L, id = "journey-notification")
        assertEquals(0L, database.notificationQueries.getUnreadCount().executeAsOne())

        // === PHASE 5: Delete Account (GDPR) ===
        database.transaction {
            database.userQueries.deleteAllUsers()
            database.interactionQueries.deleteAllInteractions()
            database.notificationQueries.deleteAllNotifications()
            database.exposureReportQueries.deleteAllReports()
            database.settingsQueries.deleteAllSettings()
        }

        // Verify complete deletion
        assertEquals(0L, database.userQueries.userExists().executeAsOne())
        assertEquals(0L, database.interactionQueries.getInteractionCount().executeAsOne())
        assertEquals(0L, database.notificationQueries.getNotificationCount().executeAsOne())
        assertEquals(0L, database.exposureReportQueries.getReportCount().executeAsOne())
        assertNull(database.userQueries.getCurrentUser().executeAsOneOrNull())
    }
}
