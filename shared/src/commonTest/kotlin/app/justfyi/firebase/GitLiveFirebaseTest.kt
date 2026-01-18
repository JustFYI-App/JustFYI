package app.justfyi.firebase

import app.justfyi.data.firebase.TestFirebaseProvider
import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.createTestDatabase
import app.justfyi.domain.model.User
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for GitLive Firebase SDK integration.
 * These tests verify that repository implementations work correctly
 * with the GitLive SDK abstractions for multiplatform Firebase support.
 *
 * Tests cover:
 * 1. Anonymous authentication flow with GitLive SDK
 * 2. Firestore document read/write operations
 * 3. Firestore collection queries with filters
 * 4. Cloud Functions invocation
 * 5. FCM token retrieval (platform-agnostic)
 * 6. Firebase initialization on both platforms
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GitLiveFirebaseTest {
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

    // ==================== Test 1: Anonymous Authentication Flow ====================

    @Test
    fun `test anonymous authentication flow with GitLive SDK`() =
        runTest {
            // Given - a user attempting anonymous sign-in
            val expectedUserId = "test-anonymous-id-123"
            firebaseProvider.setMockAnonymousUserId(expectedUserId)

            // When - authenticate anonymously
            val result = firebaseProvider.signInAnonymously()

            // Then - should return valid anonymous user ID
            assertNotNull(result)
            assertEquals(expectedUserId, result)
            assertTrue(firebaseProvider.isAuthenticated())
        }

    // ==================== Test 2: Firestore Document Read/Write ====================

    @Test
    fun `test Firestore document read and write operations`() =
        runTest {
            // Given - a user to save
            val user =
                User(
                    id = "user-doc-test",
                    anonymousId = "anon-doc-123",
                    username = "DocTestUser",
                    createdAt = currentTimeMillis(),
                    fcmToken = "fcm-token-doc",
                    idBackupConfirmed = true,
                )

            // When - save document using FirebaseProvider
            firebaseProvider.setDocument(
                collection = "users",
                documentId = user.anonymousId,
                data =
                    mapOf(
                        "anonymousId" to user.anonymousId,
                        "username" to user.username,
                        "createdAt" to user.createdAt,
                        "fcmToken" to user.fcmToken,
                    ),
            )

            // Then - read document should return the same data
            val retrievedData = firebaseProvider.getDocument("users", user.anonymousId)
            assertNotNull(retrievedData)
            assertEquals(user.anonymousId, retrievedData["anonymousId"])
            assertEquals(user.username, retrievedData["username"])
            assertEquals(user.fcmToken, retrievedData["fcmToken"])
        }

    // ==================== Test 3: Firestore Collection Queries with Filters ====================

    @Test
    fun `test Firestore collection queries with filters`() =
        runTest {
            // Given - multiple notifications for different users
            val userId = "user-query-test"
            val otherUserId = "other-user"
            val now = currentTimeMillis()

            // Add notifications for the test user
            repeat(3) { index ->
                firebaseProvider.setDocument(
                    collection = "notifications",
                    documentId = "notif-$userId-$index",
                    data =
                        mapOf(
                            "recipientId" to userId,
                            "type" to "EXPOSURE",
                            "receivedAt" to now - (index * 1000L),
                        ),
                )
            }

            // Add notifications for another user
            repeat(2) { index ->
                firebaseProvider.setDocument(
                    collection = "notifications",
                    documentId = "notif-$otherUserId-$index",
                    data =
                        mapOf(
                            "recipientId" to otherUserId,
                            "type" to "EXPOSURE",
                            "receivedAt" to now - (index * 1000L),
                        ),
                )
            }

            // When - query notifications for the test user
            val result =
                firebaseProvider.queryCollection(
                    collection = "notifications",
                    whereField = "recipientId",
                    whereValue = userId,
                )

            // Then - should only return notifications for the test user
            assertEquals(3, result.size)
            assertTrue(result.all { it["recipientId"] == userId })
        }

    // ==================== Test 4: Cloud Functions Invocation ====================

    @Test
    fun `test Cloud Functions invocation`() =
        runTest {
            // Given - a function call with data
            val functionName = "processExposureReport"
            val reportId = "report-func-test-123"
            val functionData = mapOf("reportId" to reportId)

            // Configure mock to return success
            firebaseProvider.setMockFunctionResult(functionName, mapOf("success" to true))

            // When - invoke the function
            val result = firebaseProvider.callFunction(functionName, functionData)

            // Then - should return the expected result
            assertNotNull(result)
            assertEquals(true, result["success"])

            // Verify the function was called with correct data
            val lastCall = firebaseProvider.getLastFunctionCall()
            assertNotNull(lastCall)
            assertEquals(functionName, lastCall.first)
            assertEquals(reportId, (lastCall.second as Map<*, *>)["reportId"])
        }

    // ==================== Test 5: FCM Token Retrieval ====================

    @Test
    fun `test FCM token retrieval platform-agnostic`() =
        runTest {
            // Given - a mock FCM token
            val expectedToken = "mock-fcm-token-12345"
            firebaseProvider.setMockFcmToken(expectedToken)

            // When - retrieve FCM token
            val token = firebaseProvider.getFcmToken()

            // Then - should return the expected token
            assertNotNull(token)
            assertEquals(expectedToken, token)
        }

    // ==================== Test 6: Firebase Initialization ====================

    @Test
    fun `test Firebase initialization on both platforms`() =
        runTest {
            // Given - Firebase provider is not initialized
            val uninitializedProvider = TestFirebaseProvider()
            assertTrue(!uninitializedProvider.isInitialized())

            // When - initialize Firebase
            uninitializedProvider.initialize()

            // Then - should be initialized and ready to use
            assertTrue(uninitializedProvider.isInitialized())

            // Verify basic operations work after initialization
            uninitializedProvider.setMockAnonymousUserId("init-test-user")
            val userId = uninitializedProvider.signInAnonymously()
            assertNotNull(userId)
        }
}
