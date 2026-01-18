package app.justfyi.domain.usecase

import app.justfyi.data.firebase.DocumentSnapshot
import app.justfyi.data.firebase.FirebaseException
import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.data.firebase.TestFirebaseProvider
import app.justfyi.util.AppCoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for DataExportUseCase.
 *
 * These tests verify:
 * - Successful data export returns ExportData with all 4 data types
 * - Unauthenticated error is mapped correctly
 * - Network/Firebase errors are handled gracefully
 * - Response parsing handles all field types correctly
 * - Missing or null fields are handled gracefully
 */
class DataExportUseCaseTest {
    private val testDispatchers =
        AppCoroutineDispatchers(
            io = Dispatchers.Default,
            main = Dispatchers.Default,
            default = Dispatchers.Default,
        )

    // ==================== Success Case Tests ====================

    @Test
    fun `exportUserData returns ExportData on successful Cloud Function call`() =
        runTest {
            // Given - a mock Firebase provider with successful function result
            val firebaseProvider = TestFirebaseProvider()
            firebaseProvider.setMockAnonymousUserId("test-user-123")
            firebaseProvider.signInAnonymously()

            val mockResponse = createMockExportResponse()
            firebaseProvider.setMockFunctionResult("exportUserData", mockResponse)

            val useCase = DataExportUseCaseImpl(firebaseProvider, testDispatchers)

            // When
            val result = useCase.exportUserData()

            // Then
            assertTrue(result.isSuccess, "Result should be successful")
            val exportData = result.getOrNull()
            assertNotNull(exportData, "ExportData should not be null")

            // Verify user data
            assertEquals("anon-abc", exportData.user.anonymousId)
            assertEquals("TestUser", exportData.user.username)
            assertEquals(1704067200000L, exportData.user.createdAt)

            // Verify interactions
            assertEquals(1, exportData.interactions.size)
            assertEquals("interaction-1", exportData.interactions[0].id)
            assertEquals("partner-anon-xyz", exportData.interactions[0].partnerAnonymousId)

            // Verify notifications
            assertEquals(1, exportData.notifications.size)
            assertEquals("notif-1", exportData.notifications[0].id)
            assertEquals("exposure", exportData.notifications[0].type)
            assertEquals("{\"hops\":2}", exportData.notifications[0].chainData)

            // Verify reports
            assertEquals(1, exportData.reports.size)
            assertEquals("report-1", exportData.reports[0].id)
            assertEquals("[\"HIV\"]", exportData.reports[0].stiTypes)
        }

    @Test
    fun `exportUserData parses all 4 data types correctly`() =
        runTest {
            // Given - mock response with multiple items in each collection
            val firebaseProvider = TestFirebaseProvider()
            firebaseProvider.setMockAnonymousUserId("test-user")
            firebaseProvider.signInAnonymously()

            val mockResponse =
                mapOf(
                    "user" to
                        mapOf(
                            "id" to "user-1",
                            "anonymousId" to "anon-1",
                            "username" to "User1",
                            "createdAt" to 1700000000000L,
                            "idBackupConfirmed" to false,
                        ),
                    "interactions" to
                        listOf(
                            mapOf(
                                "id" to "int-1",
                                "partnerAnonymousId" to "partner-1",
                                "partnerUsernameSnapshot" to "Partner1",
                                "recordedAt" to 1700000001000L,
                                "syncedToCloud" to true,
                            ),
                            mapOf(
                                "id" to "int-2",
                                "partnerAnonymousId" to "partner-2",
                                "partnerUsernameSnapshot" to "Partner2",
                                "recordedAt" to 1700000002000L,
                                "syncedToCloud" to false,
                            ),
                        ),
                    "notifications" to
                        listOf(
                            mapOf(
                                "id" to "notif-1",
                                "type" to "exposure",
                                "stiType" to "HIV",
                                "exposureDate" to 1699999999000L,
                                "chainData" to "{\"hops\":1}",
                                "isRead" to true,
                                "receivedAt" to 1700000000000L,
                                "updatedAt" to 1700000000000L,
                            ),
                        ),
                    "reports" to
                        listOf(
                            mapOf(
                                "id" to "rep-1",
                                "stiTypes" to "[\"CHLAMYDIA\"]",
                                "testDate" to 1699900000000L,
                                "privacyLevel" to "anonymous",
                                "reportedAt" to 1700000000000L,
                                "syncedToCloud" to true,
                            ),
                            mapOf(
                                "id" to "rep-2",
                                "stiTypes" to "[\"SYPHILIS\",\"GONORRHEA\"]",
                                "testDate" to 1699800000000L,
                                "privacyLevel" to "identified",
                                "reportedAt" to 1699900000000L,
                                "syncedToCloud" to true,
                            ),
                        ),
                )
            firebaseProvider.setMockFunctionResult("exportUserData", mockResponse)

            val useCase = DataExportUseCaseImpl(firebaseProvider, testDispatchers)

            // When
            val result = useCase.exportUserData()

            // Then
            assertTrue(result.isSuccess)
            val data = result.getOrThrow()

            assertEquals("anon-1", data.user.anonymousId)
            assertEquals(2, data.interactions.size)
            assertEquals(1, data.notifications.size)
            assertEquals(2, data.reports.size)

            // Verify second interaction
            assertEquals("int-2", data.interactions[1].id)
            assertEquals("partner-2", data.interactions[1].partnerAnonymousId)

            // Verify second report
            assertEquals("rep-2", data.reports[1].id)
            assertEquals("identified", data.reports[1].privacyLevel)
        }

    // ==================== Error Handling Tests ====================

    @Test
    fun `exportUserData returns failure when user is not authenticated`() =
        runTest {
            // Given - user is not signed in
            val firebaseProvider = TestFirebaseProvider()
            // Note: Not calling signInAnonymously()

            val useCase = DataExportUseCaseImpl(firebaseProvider, testDispatchers)

            // When
            val result = useCase.exportUserData()

            // Then
            assertTrue(result.isFailure, "Result should be failure when not authenticated")
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertTrue(
                exception.message?.contains("authenticated") == true ||
                    exception.message?.contains("sign") == true,
                "Error message should indicate authentication issue: ${exception.message}",
            )
        }

    @Test
    fun `exportUserData returns failure on Firebase error`() =
        runTest {
            // Given - Firebase provider that throws on function call
            val firebaseProvider =
                ThrowingFirebaseProvider(
                    errorToThrow = FirebaseException("Network error: connection timeout"),
                )

            val useCase = DataExportUseCaseImpl(firebaseProvider, testDispatchers)

            // When
            val result = useCase.exportUserData()

            // Then
            assertTrue(result.isFailure, "Result should be failure on network error")
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertIs<FirebaseException>(exception)
        }

    @Test
    fun `exportUserData handles missing optional fields gracefully`() =
        runTest {
            // Given - response with missing optional fields
            val firebaseProvider = TestFirebaseProvider()
            firebaseProvider.setMockAnonymousUserId("test-user")
            firebaseProvider.signInAnonymously()

            val mockResponse =
                mapOf(
                    "user" to
                        mapOf(
                            "id" to "user-1",
                            "anonymousId" to "anon-1",
                            "username" to "User",
                            "createdAt" to 1700000000000L,
                            "idBackupConfirmed" to true,
                        ),
                    "interactions" to emptyList<Map<String, Any?>>(),
                    "notifications" to
                        listOf(
                            mapOf(
                                "id" to "notif-1",
                                "type" to "exposure",
                                // stiType is null/missing
                                // exposureDate is null/missing
                                "chainData" to "{}",
                                "isRead" to false,
                                "receivedAt" to 1700000000000L,
                                "updatedAt" to 1700000000000L,
                            ),
                        ),
                    "reports" to emptyList<Map<String, Any?>>(),
                )
            firebaseProvider.setMockFunctionResult("exportUserData", mockResponse)

            val useCase = DataExportUseCaseImpl(firebaseProvider, testDispatchers)

            // When
            val result = useCase.exportUserData()

            // Then
            assertTrue(result.isSuccess, "Should succeed with missing optional fields")
            val data = result.getOrThrow()

            assertEquals(0, data.interactions.size)
            assertEquals(1, data.notifications.size)
            assertEquals(0, data.reports.size)

            // Optional fields should be null
            val notification = data.notifications[0]
            assertEquals(null, notification.stiType)
            assertEquals(null, notification.exposureDate)
        }

    @Test
    fun `exportUserData returns failure when response is null`() =
        runTest {
            // Given - function returns null
            val firebaseProvider = TestFirebaseProvider()
            firebaseProvider.setMockAnonymousUserId("test-user")
            firebaseProvider.signInAnonymously()
            firebaseProvider.setMockFunctionResult("exportUserData", null)

            val useCase = DataExportUseCaseImpl(firebaseProvider, testDispatchers)

            // When
            val result = useCase.exportUserData()

            // Then
            assertTrue(result.isFailure, "Result should be failure when response is null")
        }

    // ==================== Helper Methods ====================

    private fun createMockExportResponse(): Map<String, Any?> =
        mapOf(
            "user" to
                mapOf(
                    "id" to "user-123",
                    "anonymousId" to "anon-abc",
                    "username" to "TestUser",
                    "createdAt" to 1704067200000L,
                    "idBackupConfirmed" to true,
                ),
            "interactions" to
                listOf(
                    mapOf(
                        "id" to "interaction-1",
                        "partnerAnonymousId" to "partner-anon-xyz",
                        "partnerUsernameSnapshot" to "PartnerName",
                        "recordedAt" to 1704153600000L,
                        "syncedToCloud" to true,
                    ),
                ),
            "notifications" to
                listOf(
                    mapOf(
                        "id" to "notif-1",
                        "type" to "exposure",
                        "stiType" to "HIV",
                        "exposureDate" to 1703980800000L,
                        "chainData" to "{\"hops\":2}",
                        "isRead" to false,
                        "receivedAt" to 1704067200000L,
                        "updatedAt" to 1704067200000L,
                    ),
                ),
            "reports" to
                listOf(
                    mapOf(
                        "id" to "report-1",
                        "stiTypes" to "[\"HIV\"]",
                        "testDate" to 1703894400000L,
                        "privacyLevel" to "anonymous",
                        "reportedAt" to 1704067200000L,
                        "syncedToCloud" to true,
                    ),
                ),
        )
}

/**
 * Test FirebaseProvider that throws exceptions on function calls.
 * Implements FirebaseProvider directly to avoid extending final class.
 * Used for testing error handling scenarios.
 */
private class ThrowingFirebaseProvider(
    private val errorToThrow: Exception,
) : FirebaseProvider {
    private var authenticated = true

    override suspend fun initialize() {}

    override fun isInitialized(): Boolean = true

    override suspend fun signInAnonymously(): String = "test-user"

    override fun isAuthenticated(): Boolean = authenticated

    override fun getCurrentUserId(): String? = if (authenticated) "test-user" else null

    override suspend fun signOut() {
        authenticated = false
    }

    override suspend fun setDocument(
        collection: String,
        documentId: String,
        data: Map<String, Any?>,
        merge: Boolean,
    ) {}

    override suspend fun getDocument(
        collection: String,
        documentId: String,
    ): Map<String, Any?>? = null

    override suspend fun updateDocument(
        collection: String,
        documentId: String,
        updates: Map<String, Any?>,
    ) {}

    override suspend fun deleteDocument(
        collection: String,
        documentId: String,
    ) {}

    override suspend fun queryCollection(
        collection: String,
        whereField: String,
        whereValue: Any,
        orderByField: String?,
        descending: Boolean,
    ): List<Map<String, Any?>> = emptyList()

    override fun observeCollection(
        collection: String,
        whereField: String,
        whereValue: Any,
    ): Flow<List<DocumentSnapshot>> = emptyFlow()

    override fun removeCollectionListener(
        collection: String,
        whereField: String,
        whereValue: Any,
    ) {}

    override suspend fun getFcmToken(): String? = null

    override suspend fun signInWithCustomToken(customToken: String): String? = "test-user"

    override suspend fun callFunction(
        functionName: String,
        data: Map<String, Any?>,
    ): Map<String, Any?>? = throw errorToThrow
}
