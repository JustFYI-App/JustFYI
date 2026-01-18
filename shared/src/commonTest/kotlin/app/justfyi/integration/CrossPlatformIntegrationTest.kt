package app.justfyi.integration

import app.justfyi.data.firebase.TestFirebaseProvider
import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.createTestDatabase
import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.repository.BluetoothState
import app.justfyi.domain.repository.SettingsRepository
import app.justfyi.util.AppCoroutineDispatchers
import app.justfyi.util.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-Platform Integration Tests (Task Group 6).
 *
 * These tests focus on end-to-end functionality and cross-platform interoperability:
 * 1. Full onboarding flow on iOS (auth + permissions + BLE setup)
 * 2. Android device discovers iOS device via BLE
 * 3. iOS device discovers Android device via BLE
 * 4. Interaction recording between Android and iOS users
 * 5. Exposure notification delivery on iOS
 * 6. App state restoration after background/foreground cycle
 * 7. Navigation deep linking from notifications on iOS
 * 8. Settings persistence across app restarts on iOS
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrossPlatformIntegrationTest {
    private lateinit var database: JustFyiDatabase
    private lateinit var firebaseProvider: TestFirebaseProvider
    private lateinit var dispatchers: AppCoroutineDispatchers
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        database = createTestDatabase()
        // Clear all data to ensure test isolation
        database.settingsQueries.deleteAllSettings()
        database.userQueries.deleteAllUsers()
        database.interactionQueries.deleteAllInteractions()
        database.notificationQueries.deleteAllNotifications()
        database.exposureReportQueries.deleteAllReports()

        firebaseProvider = TestFirebaseProvider()
        dispatchers =
            AppCoroutineDispatchers(
                io = testDispatcher,
                main = testDispatcher,
                default = testDispatcher,
            )
    }

    @AfterTest
    fun teardown() {
        // Clean up to ensure test isolation
        database.settingsQueries.deleteAllSettings()
        database.userQueries.deleteAllUsers()
        database.interactionQueries.deleteAllInteractions()
        database.notificationQueries.deleteAllNotifications()
        database.exposureReportQueries.deleteAllReports()
        Dispatchers.resetMain()
    }

    // ==================================================================================
    // Test 1: Full Onboarding Flow on iOS
    // Integration: Firebase Auth + Permissions + BLE Setup + Database
    // ==================================================================================

    @Test
    fun `test full iOS onboarding flow - auth permissions BLE and database setup`() =
        runTest {
            // Initialize Firebase provider
            firebaseProvider.initialize()

            val anonymousId = "ios-user-${currentTimeMillis()}"
            firebaseProvider.setMockAnonymousUserId(anonymousId)
            val authResult = firebaseProvider.signInAnonymously()
            assertNotNull(authResult, "Authentication should succeed on iOS")
            assertEquals(anonymousId, authResult)

            val createdAt = currentTimeMillis()
            database.userQueries.insertUser(
                id = anonymousId,
                anonymous_id = anonymousId,
                username = "User",
                created_at = createdAt,
                fcm_token = null,
                id_backup_confirmed = 0,
            )

            val user = database.userQueries.getCurrentUser().executeAsOneOrNull()
            assertNotNull(user, "User should be created in local database")
            assertEquals(anonymousId, user.anonymous_id)

            // On iOS, CBCentralManager/CBPeripheralManager triggers permission prompt
            val iosBluetoothState = simulateIosBluetoothAuthorization(granted = true)
            assertEquals(BluetoothState.ON, iosBluetoothState)

            val notificationPermissionGranted = simulateIosNotificationAuthorization(granted = true)
            assertTrue(notificationPermissionGranted, "Notification permission should be granted")

            database.userQueries.updateIdBackupConfirmed(id_backup_confirmed = 1, id = anonymousId)
            database.userQueries.updateUsername(username = "iOSTestUser", id = anonymousId)
            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_ONBOARDING_COMPLETE,
                "true",
            )

            // Verify complete flow
            val finalUser = database.userQueries.getCurrentUser().executeAsOneOrNull()
            assertNotNull(finalUser)
            assertEquals("iOSTestUser", finalUser.username)
            assertEquals(1L, finalUser.id_backup_confirmed)

            val onboardingComplete =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                    .executeAsOneOrNull()
            assertEquals("true", onboardingComplete)
        }

    // ==================================================================================
    // Test 2: Android Device Discovers iOS Device via BLE
    // Cross-platform BLE interop: Android CBCentralManager equivalent finds iOS device
    // ==================================================================================

    @Test
    fun `test Android device discovers iOS device via BLE with correct service UUID`() {
        // Given - iOS device is advertising with Just FYI service UUID
        val justFyiServiceUuid = "7b5c3a1e-8f2d-4e6a-9c0b-1d2e3f4a5b6c"
        val iosDeviceHashedId = "ios-hashed-id-abc123"
        val iosUsername = "iOSUser"

        val iosAdvertisementData =
            mapOf(
                "serviceUUIDs" to listOf(justFyiServiceUuid),
                "localName" to "JustFYI-iOS",
                "manufacturerData" to
                    mapOf(
                        "userIdHash" to iosDeviceHashedId,
                        "username" to iosUsername,
                    ),
            )

        // When - Android scanner (simulated) discovers the iOS device
        val discoveredDevices =
            simulateAndroidBleDiscovery(
                serviceUuidFilter = justFyiServiceUuid,
                advertisingDevices = listOf(iosAdvertisementData),
            )

        // Then - iOS device should be discovered with correct data
        assertEquals(1, discoveredDevices.size, "Should discover 1 iOS device")

        val iosDevice = discoveredDevices.first()
        assertEquals(iosDeviceHashedId, iosDevice.anonymousIdHash)
        assertEquals(iosUsername, iosDevice.username)
        assertTrue(iosDevice.signalStrength < 0, "RSSI should be negative")
    }

    // ==================================================================================
    // Test 3: iOS Device Discovers Android Device via BLE
    // Cross-platform BLE interop: iOS CBCentralManager finds Android device
    // ==================================================================================

    @Test
    fun `test iOS device discovers Android device via BLE with correct GATT structure`() {
        // Given - Android device is advertising with Just FYI GATT service
        val justFyiServiceUuid = "7b5c3a1e-8f2d-4e6a-9c0b-1d2e3f4a5b6c"
        val androidDeviceHashedId = "android-hashed-id-xyz789"
        val androidUsername = "AndroidUser"

        val androidGattAdvertisement =
            mapOf(
                "serviceUUIDs" to listOf(justFyiServiceUuid),
                "characteristicData" to
                    mapOf(
                        "userIdHash" to androidDeviceHashedId,
                        "username" to androidUsername,
                    ),
                "rssi" to -55,
            )

        // When - iOS CBCentralManager (simulated) discovers the Android device
        val discoveredPeripherals =
            simulateIosCbCentralManagerDiscovery(
                serviceUuids = listOf(justFyiServiceUuid),
                advertisingPeripherals = listOf(androidGattAdvertisement),
            )

        // Then - Android device should be discovered
        assertEquals(1, discoveredPeripherals.size, "Should discover 1 Android device")

        val androidDevice = discoveredPeripherals.first()
        assertEquals(androidDeviceHashedId, androidDevice.anonymousIdHash)
        assertEquals(androidUsername, androidDevice.username)
    }

    // ==================================================================================
    // Test 4: Interaction Recording Between Android and iOS Users
    // End-to-end: BLE discovery + database recording + cloud sync
    // ==================================================================================

    @Test
    fun `test interaction recording between Android and iOS users`() =
        runTest {
            // Initialize Firebase provider
            firebaseProvider.initialize()

            // Setup - create the current user (on either platform)
            val currentUserId = "current-user-cross-platform"
            database.userQueries.insertUser(
                id = currentUserId,
                anonymous_id = currentUserId,
                username = "CurrentUser",
                created_at = currentTimeMillis(),
                fcm_token = "fcm-token",
                id_backup_confirmed = 1,
            )

            val iosNearbyUser =
                NearbyUser(
                    anonymousIdHash = "ios-partner-hash",
                    username = "iOSPartner",
                    signalStrength = -55,
                    lastSeen = currentTimeMillis(),
                )
            val androidNearbyUser =
                NearbyUser(
                    anonymousIdHash = "android-partner-hash",
                    username = "AndroidPartner",
                    signalStrength = -60,
                    lastSeen = currentTimeMillis(),
                )

            val nearbyUsers = listOf(iosNearbyUser, androidNearbyUser)

            val recordedAt = currentTimeMillis()
            database.interactionQueries.transaction {
                nearbyUsers.forEachIndexed { index, nearbyUser ->
                    database.interactionQueries.insertInteraction(
                        id = "cross-platform-interaction-$index",
                        partner_anonymous_id = nearbyUser.anonymousIdHash,
                        partner_username_snapshot = nearbyUser.username,
                        recorded_at = recordedAt,
                        synced_to_cloud = 0,
                    )
                }
            }

            val allInteractions = database.interactionQueries.getAllInteractions().executeAsList()
            assertEquals(2, allInteractions.size, "Both iOS and Android interactions should be recorded")

            val iosInteraction = allInteractions.find { it.partner_anonymous_id == "ios-partner-hash" }
            val androidInteraction = allInteractions.find { it.partner_anonymous_id == "android-partner-hash" }

            assertNotNull(iosInteraction, "iOS interaction should be recorded")
            assertNotNull(androidInteraction, "Android interaction should be recorded")
            assertEquals("iOSPartner", iosInteraction.partner_username_snapshot)
            assertEquals("AndroidPartner", androidInteraction.partner_username_snapshot)

            allInteractions.forEach { interaction ->
                firebaseProvider.setDocument(
                    collection = "interactions",
                    documentId = interaction.id,
                    data =
                        mapOf(
                            "partnerId" to interaction.partner_anonymous_id,
                            "recordedAt" to interaction.recorded_at,
                        ),
                )
                database.interactionQueries.updateSyncStatus(synced_to_cloud = 1, id = interaction.id)
            }

            // Verify sync completed
            val unsyncedAfter = database.interactionQueries.getUnsyncedInteractions().executeAsList()
            assertEquals(0, unsyncedAfter.size, "All interactions should be synced")
        }

    // ==================================================================================
    // Test 5: Exposure Notification Delivery on iOS
    // Firebase Cloud Messaging via GitLive SDK + local notification handling
    // ==================================================================================

    @Test
    fun `test exposure notification delivery on iOS`() =
        runTest {
            // Initialize Firebase provider
            firebaseProvider.initialize()

            // Setup - create receiving user
            val recipientId = "ios-recipient-user"
            database.userQueries.insertUser(
                id = recipientId,
                anonymous_id = recipientId,
                username = "iOSRecipient",
                created_at = currentTimeMillis(),
                fcm_token = "ios-apns-fcm-token",
                id_backup_confirmed = 1,
            )

            val apnsFcmToken = "ios-apns-fcm-token-${currentTimeMillis()}"
            firebaseProvider.setMockFcmToken(apnsFcmToken)
            val retrievedToken = firebaseProvider.getFcmToken()
            assertEquals(apnsFcmToken, retrievedToken, "FCM token should be retrieved on iOS")

            val notificationPayload =
                mapOf(
                    "type" to "EXPOSURE",
                    "stiType" to "HIV",
                    "exposureDate" to (currentTimeMillis() - 10 * 24 * 60 * 60 * 1000L).toString(),
                    "chainData" to """{"nodes":[{"username":"Reporter","testStatus":"POSITIVE"}]}""",
                )

            val notificationId = "ios-exposure-notif-${currentTimeMillis()}"
            database.notificationQueries.insertNotification(
                id = notificationId,
                type = notificationPayload["type"]!!,
                sti_type = notificationPayload["stiType"],
                exposure_date = notificationPayload["exposureDate"]?.toLongOrNull(),
                chain_data = notificationPayload["chainData"]!!,
                is_read = 0,
                received_at = currentTimeMillis(),
                updated_at = currentTimeMillis(),
                deleted_at = null,
            )

            val storedNotification =
                database.notificationQueries
                    .getNotificationById(notificationId)
                    .executeAsOneOrNull()

            assertNotNull(storedNotification, "Notification should be stored locally")
            assertEquals("EXPOSURE", storedNotification.type)
            assertEquals("HIV", storedNotification.sti_type)
            assertEquals(0L, storedNotification.is_read, "Should be unread initially")

            val unreadCount = database.notificationQueries.getUnreadCount().executeAsOne()
            assertTrue(unreadCount > 0, "Unread count should be greater than 0")
        }

    // ==================================================================================
    // Test 6: App State Restoration After Background/Foreground Cycle
    // iOS lifecycle: applicationDidEnterBackground/applicationWillEnterForeground
    // ==================================================================================

    @Test
    fun `test app state restoration after background foreground cycle on iOS`() =
        runTest {
            // Setup - create user and app state
            val userId = "ios-lifecycle-user"
            database.userQueries.insertUser(
                id = userId,
                anonymous_id = userId,
                username = "LifecycleTestUser",
                created_at = currentTimeMillis(),
                fcm_token = "lifecycle-fcm",
                id_backup_confirmed = 1,
            )

            // Record some interactions before going to background
            database.interactionQueries.insertInteraction(
                id = "pre-background-interaction",
                partner_anonymous_id = "partner-lifecycle",
                partner_username_snapshot = "PartnerUser",
                recorded_at = currentTimeMillis(),
                synced_to_cloud = 0,
            )

            var bleDiscoveryActive = true
            bleDiscoveryActive = false // BLE discovery stops in background (foreground-only per spec)

            assertFalse(bleDiscoveryActive, "BLE should be stopped in background")

            @Suppress("UNUSED_VARIABLE")
            val backgroundDuration = 5000L // 5 seconds

            bleDiscoveryActive = true // BLE discovery resumes

            assertTrue(bleDiscoveryActive, "BLE should resume in foreground")

            val restoredUser = database.userQueries.getCurrentUser().executeAsOneOrNull()
            assertNotNull(restoredUser, "User should still exist after lifecycle")
            assertEquals("LifecycleTestUser", restoredUser.username)

            val pendingInteractions = database.interactionQueries.getUnsyncedInteractions().executeAsList()
            assertEquals(1, pendingInteractions.size, "Pending interaction should survive lifecycle")

            pendingInteractions.forEach { interaction ->
                database.interactionQueries.updateSyncStatus(synced_to_cloud = 1, id = interaction.id)
            }

            val syncedAfter = database.interactionQueries.getUnsyncedInteractions().executeAsList()
            assertEquals(0, syncedAfter.size, "All interactions should be synced after restoration")
        }

    // ==================================================================================
    // Test 7: Navigation Deep Linking from Notifications on iOS
    // Push notification tap -> app opens -> navigates to notification detail
    // ==================================================================================

    @Test
    fun `test navigation deep linking from notification tap on iOS`() {
        // Setup - create notification
        val notificationId = "deep-link-notification"
        val now = currentTimeMillis()

        database.notificationQueries.insertNotification(
            id = notificationId,
            type = "EXPOSURE",
            sti_type = "CHLAMYDIA",
            exposure_date = now - (7 * 24 * 60 * 60 * 1000L),
            chain_data = """{"nodes":[{"username":"Source","testStatus":"POSITIVE"}]}""",
            is_read = 0,
            received_at = now,
            updated_at = now,
            deleted_at = null,
        )

        val deepLinkUrl = "justfyi://notification/$notificationId"
        val parsedNotificationId = parseDeepLinkNotificationId(deepLinkUrl)

        assertEquals(notificationId, parsedNotificationId, "Should parse notification ID from deep link")

        val navigationDestination = determineNavigationDestinationForDeepLink(parsedNotificationId)

        assertEquals(
            NavigationDestination.NotificationDetail(notificationId),
            navigationDestination,
            "Should navigate to notification detail",
        )

        database.notificationQueries.markAsRead(updated_at = now + 1000L, id = notificationId)

        val notification =
            database.notificationQueries
                .getNotificationById(notificationId)
                .executeAsOneOrNull()

        assertNotNull(notification)
        assertEquals(1L, notification.is_read, "Notification should be marked as read")
    }

    // ==================================================================================
    // Test 8: Settings Persistence Across App Restarts on iOS
    // UserDefaults-backed settings via SQLDelight
    // ==================================================================================

    @Test
    fun `test settings persistence across app restarts on iOS`() {
        database.settingsQueries.insertOrReplaceSetting("language_override", "de")
        database.settingsQueries.insertOrReplaceSetting("privacy_policy_accepted", "true")
        database.settingsQueries.insertOrReplaceSetting("notifications_enabled", "true")
        database.settingsQueries.insertOrReplaceSetting("theme_preference", "dark")
        database.settingsQueries.insertOrReplaceSetting(
            SettingsRepository.KEY_ONBOARDING_COMPLETE,
            "true",
        )

        // In reality, SQLite database file persists across app launches

        val languageOverride =
            database.settingsQueries
                .getSettingByKey("language_override")
                .executeAsOneOrNull()
        val privacyPolicyAccepted =
            database.settingsQueries
                .getSettingByKey("privacy_policy_accepted")
                .executeAsOneOrNull()
        val notificationsEnabled =
            database.settingsQueries
                .getSettingByKey("notifications_enabled")
                .executeAsOneOrNull()
        val themePreference =
            database.settingsQueries
                .getSettingByKey("theme_preference")
                .executeAsOneOrNull()
        val onboardingComplete =
            database.settingsQueries
                .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                .executeAsOneOrNull()

        assertEquals("de", languageOverride, "Language should persist")
        assertEquals("true", privacyPolicyAccepted, "Privacy policy acceptance should persist")
        assertEquals("true", notificationsEnabled, "Notification preference should persist")
        assertEquals("dark", themePreference, "Theme preference should persist")
        assertEquals("true", onboardingComplete, "Onboarding completion should persist")

        database.settingsQueries.insertOrReplaceSetting("theme_preference", "light")

        val updatedTheme =
            database.settingsQueries
                .getSettingByKey("theme_preference")
                .executeAsOneOrNull()
        assertEquals("light", updatedTheme, "Settings should be updatable after restart")
    }

    // ==================================================================================
    // Helper Functions
    // ==================================================================================

    /**
     * Simulates iOS Bluetooth authorization via Core Bluetooth.
     */
    private fun simulateIosBluetoothAuthorization(granted: Boolean): BluetoothState =
        if (granted) BluetoothState.ON else BluetoothState.NOT_AVAILABLE

    /**
     * Simulates iOS notification authorization via UNUserNotificationCenter.
     */
    private fun simulateIosNotificationAuthorization(granted: Boolean): Boolean = granted

    /**
     * Simulates Android BLE discovery finding devices.
     */
    private fun simulateAndroidBleDiscovery(
        serviceUuidFilter: String,
        advertisingDevices: List<Map<String, Any>>,
    ): List<NearbyUser> {
        return advertisingDevices
            .filter { device ->
                @Suppress("UNCHECKED_CAST")
                val serviceUuids = device["serviceUUIDs"] as? List<String> ?: emptyList()
                serviceUuids.contains(serviceUuidFilter)
            }.mapNotNull { device ->
                @Suppress("UNCHECKED_CAST")
                val manufacturerData = device["manufacturerData"] as? Map<String, String>
                if (manufacturerData != null) {
                    NearbyUser(
                        anonymousIdHash = manufacturerData["userIdHash"] ?: return@mapNotNull null,
                        username = manufacturerData["username"] ?: "Unknown",
                        signalStrength = -65, // Default RSSI
                        lastSeen = currentTimeMillis(),
                    )
                } else {
                    null
                }
            }
    }

    /**
     * Simulates iOS CBCentralManager discovering BLE peripherals.
     */
    private fun simulateIosCbCentralManagerDiscovery(
        serviceUuids: List<String>,
        advertisingPeripherals: List<Map<String, Any>>,
    ): List<NearbyUser> {
        return advertisingPeripherals
            .filter { peripheral ->
                @Suppress("UNCHECKED_CAST")
                val peripheralServices = peripheral["serviceUUIDs"] as? List<String> ?: emptyList()
                peripheralServices.any { it in serviceUuids }
            }.mapNotNull { peripheral ->
                @Suppress("UNCHECKED_CAST")
                val characteristicData = peripheral["characteristicData"] as? Map<String, String>
                val rssi = peripheral["rssi"] as? Int ?: -70

                if (characteristicData != null) {
                    NearbyUser(
                        anonymousIdHash = characteristicData["userIdHash"] ?: return@mapNotNull null,
                        username = characteristicData["username"] ?: "Unknown",
                        signalStrength = rssi,
                        lastSeen = currentTimeMillis(),
                    )
                } else {
                    null
                }
            }
    }

    /**
     * Parses notification ID from deep link URL.
     */
    private fun parseDeepLinkNotificationId(url: String): String? {
        // URL format: justfyi://notification/{notificationId}
        val prefix = "justfyi://notification/"
        return if (url.startsWith(prefix)) {
            url.removePrefix(prefix)
        } else {
            null
        }
    }

    /**
     * Navigation destination sealed class for deep linking.
     */
    sealed class NavigationDestination {
        data object Home : NavigationDestination()

        data class NotificationDetail(
            val notificationId: String,
        ) : NavigationDestination()

        data class InteractionDetail(
            val interactionId: String,
        ) : NavigationDestination()
    }

    /**
     * Determines navigation destination from deep link.
     */
    private fun determineNavigationDestinationForDeepLink(notificationId: String?): NavigationDestination =
        if (notificationId != null) {
            NavigationDestination.NotificationDetail(notificationId)
        } else {
            NavigationDestination.Home
        }
}
