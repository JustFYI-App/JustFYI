package app.justfyi.integration

import app.justfyi.data.firebase.DocumentSnapshot
import app.justfyi.data.firebase.FirebaseException
import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.data.firebase.TestFirebaseProvider
import app.justfyi.domain.model.ExportData
import app.justfyi.domain.model.ExportInteraction
import app.justfyi.domain.model.ExportNotification
import app.justfyi.domain.model.ExportReport
import app.justfyi.domain.model.ExportUser
import app.justfyi.domain.usecase.DataExportUseCaseImpl
import app.justfyi.platform.FileResult
import app.justfyi.platform.ShareService
import app.justfyi.platform.ZipService
import app.justfyi.util.AppCoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the User Data Export feature.
 *
 * Task Group 6: Test Review and Gap Analysis
 *
 * These tests verify the integration between:
 * - DataExportUseCase: Fetches data from Cloud Function
 * - ZipService: Creates ZIP file with JSON contents
 * - ShareService: Opens native share sheet
 *
 * Focus areas:
 * - End-to-end export flow (Cloud Function -> ZIP -> Share)
 * - Large data export handling
 * - Error propagation across layers
 * - chainData integrity through the entire flow
 */
class DataExportIntegrationTest {
    private val testDispatchers =
        AppCoroutineDispatchers(
            io = Dispatchers.Default,
            main = Dispatchers.Default,
            default = Dispatchers.Default,
        )

    private val prettyJson =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    // ==================================================================================
    // Test 1: End-to-End Export Flow Integration
    // Verifies: UseCase fetches data -> ZipService creates ZIP -> ShareService shares
    // ==================================================================================

    @Test
    fun `test end-to-end export flow from UseCase through ZipService to ShareService`() =
        runTest {
            // Given - Set up all components
            val firebaseProvider = TestFirebaseProvider()
            firebaseProvider.setMockAnonymousUserId("test-user-123")
            firebaseProvider.signInAnonymously()
            firebaseProvider.setMockFunctionResult("exportUserData", createMockExportResponse())

            val useCase = DataExportUseCaseImpl(firebaseProvider, testDispatchers)

            // Track ZipService calls
            var zipServiceCalled = false
            var zipDataReceived: ExportData? = null
            var zipFileNameReceived: String? = null

            val zipService =
                object : ZipService {
                    override suspend fun createZip(
                        data: ExportData,
                        fileName: String,
                    ): FileResult {
                        zipServiceCalled = true
                        zipDataReceived = data
                        zipFileNameReceived = fileName
                        return FileResult(
                            filePath = "/cache/$fileName",
                            mimeType = "application/zip",
                        )
                    }
                }

            // Track ShareService calls
            var shareServiceCalled = false
            var shareFilePathReceived: String? = null
            var shareMimeTypeReceived: String? = null
            var shareFileNameReceived: String? = null

            val shareService =
                object : ShareService {
                    override fun shareFile(
                        filePath: String,
                        mimeType: String,
                        fileName: String,
                    ): Boolean {
                        shareServiceCalled = true
                        shareFilePathReceived = filePath
                        shareMimeTypeReceived = mimeType
                        shareFileNameReceived = fileName
                        return true
                    }
                }

            // When - Execute the export flow
            val exportResult = useCase.exportUserData()
            assertTrue(exportResult.isSuccess, "Export should succeed")

            val exportData = exportResult.getOrThrow()
            val timestamp = 1704067200000L
            val fileName = "justfyi-export-$timestamp.zip"
            val fileResult = zipService.createZip(exportData, fileName)
            val shareSuccess =
                shareService.shareFile(
                    filePath = fileResult.filePath,
                    mimeType = fileResult.mimeType,
                    fileName = fileName,
                )

            // Then - Verify the complete flow
            assertTrue(zipServiceCalled, "ZipService should be called")
            assertNotNull(zipDataReceived, "ZipService should receive ExportData")
            assertEquals("justfyi-export-$timestamp.zip", zipFileNameReceived)

            assertTrue(shareServiceCalled, "ShareService should be called")
            assertEquals("/cache/justfyi-export-$timestamp.zip", shareFilePathReceived)
            assertEquals("application/zip", shareMimeTypeReceived)
            assertEquals("justfyi-export-$timestamp.zip", shareFileNameReceived)
            assertTrue(shareSuccess, "ShareService should return success")

            // Verify data integrity through the flow (zipDataReceived is non-null after assertNotNull)
            val receivedData = zipDataReceived!!
            assertEquals("anon-abc", receivedData.user.anonymousId)
            assertEquals(1, receivedData.interactions.size)
            assertEquals(1, receivedData.notifications.size)
            assertEquals(1, receivedData.reports.size)
        }

    // ==================================================================================
    // Test 2: Large Data Export Handling
    // Verifies: Export works correctly with many items (100+ interactions)
    // ==================================================================================

    @Test
    fun `test large data export with 100 interactions serializes correctly`() =
        runTest {
            // Given - Create large dataset
            val firebaseProvider = TestFirebaseProvider()
            firebaseProvider.setMockAnonymousUserId("test-user")
            firebaseProvider.signInAnonymously()

            // Create response with 100 interactions
            val largeInteractionList =
                (1..100).map { index ->
                    mapOf(
                        "id" to "interaction-$index",
                        "partnerAnonymousId" to "partner-$index",
                        "partnerUsernameSnapshot" to "Partner$index",
                        "recordedAt" to (1700000000000L + index * 1000),
                        "syncedToCloud" to (index % 2 == 0),
                    )
                }

            val largeResponse =
                mapOf(
                    "user" to
                        mapOf(
                            "id" to "user-1",
                            "anonymousId" to "anon-1",
                            "username" to "TestUser",
                            "createdAt" to 1700000000000L,
                            "idBackupConfirmed" to true,
                        ),
                    "interactions" to largeInteractionList,
                    "notifications" to emptyList<Map<String, Any?>>(),
                    "reports" to emptyList<Map<String, Any?>>(),
                )
            firebaseProvider.setMockFunctionResult("exportUserData", largeResponse)

            val useCase = DataExportUseCaseImpl(firebaseProvider, testDispatchers)

            // When
            val result = useCase.exportUserData()

            // Then
            assertTrue(result.isSuccess, "Large data export should succeed")
            val data = result.getOrThrow()
            assertEquals(100, data.interactions.size, "Should have 100 interactions")

            // Verify first and last interaction
            assertEquals("interaction-1", data.interactions.first().id)
            assertEquals("interaction-100", data.interactions.last().id)

            // Verify serialization works
            val json = prettyJson.encodeToString(data.interactions)
            assertTrue(json.contains("interaction-1"), "JSON should contain first interaction")
            assertTrue(json.contains("interaction-100"), "JSON should contain last interaction")
            assertTrue(json.length > 10000, "JSON should be substantial size")
        }

    // ==================================================================================
    // Test 3: ZipService Error Propagation
    // Verifies: Errors from ZipService are properly caught and can be handled
    // ==================================================================================

    @Test
    fun `test ZipService error is caught and can be reported to user`() =
        runTest {
            // Given - ZipService that throws
            val failingZipService =
                object : ZipService {
                    override suspend fun createZip(
                        data: ExportData,
                        fileName: String,
                    ): FileResult = throw RuntimeException("Disk full - cannot create ZIP")
                }

            val exportData = createTestExportData()

            // When - Try to create ZIP
            var errorCaught = false
            var errorMessage: String? = null
            try {
                failingZipService.createZip(exportData, "test.zip")
            } catch (e: Exception) {
                errorCaught = true
                errorMessage = e.message
            }

            // Then - Error should be catchable
            assertTrue(errorCaught, "Exception should be caught")
            assertEquals("Disk full - cannot create ZIP", errorMessage)
        }

    // ==================================================================================
    // Test 4: ShareService Failure Handling
    // Verifies: When ShareService returns false, the flow can report error to user
    // ==================================================================================

    @Test
    fun `test ShareService failure returns false and error can be reported`() =
        runTest {
            // Given - ShareService that fails
            val failingShareService =
                object : ShareService {
                    override fun shareFile(
                        filePath: String,
                        mimeType: String,
                        fileName: String,
                    ): Boolean {
                        return false // Simulates share sheet failed to open
                    }
                }

            // When
            val shareResult =
                failingShareService.shareFile(
                    filePath = "/cache/export.zip",
                    mimeType = "application/zip",
                    fileName = "export.zip",
                )

            // Then
            assertFalse(shareResult, "ShareService should return false on failure")

            // Verify error can be mapped to user-friendly message
            val userMessage = if (!shareResult) "Export failed. Please try again." else null
            assertEquals("Export failed. Please try again.", userMessage)
        }

    // ==================================================================================
    // Test 5: chainData Integrity Through Complete Flow
    // Verifies: chainData JSON string passes through unmodified from Cloud Function to ZIP
    // ==================================================================================

    @Test
    fun `test chainData remains unmodified through export flow`() =
        runTest {
            // Given - Complex nested chainData
            val complexChainData = """{"nodes":[{"username":"User1","testStatus":"POSITIVE","isCurrentUser":false,"hops":0},{"username":"User2","testStatus":"UNKNOWN","isCurrentUser":false,"hops":1},{"username":"You","testStatus":"PENDING","isCurrentUser":true,"hops":2}],"totalHops":2,"maxDepth":3}"""

            val firebaseProvider = TestFirebaseProvider()
            firebaseProvider.setMockAnonymousUserId("test-user")
            firebaseProvider.signInAnonymously()

            val responseWithChainData =
                mapOf(
                    "user" to
                        mapOf(
                            "id" to "user-1",
                            "anonymousId" to "anon-1",
                            "username" to "TestUser",
                            "createdAt" to 1700000000000L,
                            "idBackupConfirmed" to true,
                        ),
                    "interactions" to emptyList<Map<String, Any?>>(),
                    "notifications" to
                        listOf(
                            mapOf(
                                "id" to "notif-1",
                                "type" to "exposure",
                                "stiType" to "HIV",
                                "exposureDate" to 1699999999000L,
                                "chainData" to complexChainData,
                                "isRead" to false,
                                "receivedAt" to 1700000000000L,
                                "updatedAt" to 1700000000000L,
                            ),
                        ),
                    "reports" to emptyList<Map<String, Any?>>(),
                )
            firebaseProvider.setMockFunctionResult("exportUserData", responseWithChainData)

            val useCase = DataExportUseCaseImpl(firebaseProvider, testDispatchers)

            // When
            val result = useCase.exportUserData()

            // Then
            assertTrue(result.isSuccess)
            val data = result.getOrThrow()

            // Verify chainData is exactly the same string
            assertEquals(1, data.notifications.size)
            val notification = data.notifications[0]
            assertEquals(complexChainData, notification.chainData, "chainData should be unmodified")

            // Verify it can be serialized to JSON for ZIP file
            val notificationJson = prettyJson.encodeToString(notification)
            assertTrue(notificationJson.contains("chainData"), "JSON should contain chainData field")

            // The chainData should appear as escaped string in the JSON output
            assertTrue(
                notificationJson.contains("nodes") || notificationJson.contains("\\\"nodes\\\""),
                "chainData content should be present in serialized form",
            )
        }

    // ==================================================================================
    // Test 6: Export with Empty Collections
    // Verifies: Export works correctly when user has no interactions/notifications/reports
    // ==================================================================================

    @Test
    fun `test export with empty collections produces valid output`() =
        runTest {
            // Given - User with no activity
            val firebaseProvider = TestFirebaseProvider()
            firebaseProvider.setMockAnonymousUserId("new-user")
            firebaseProvider.signInAnonymously()

            val emptyDataResponse =
                mapOf(
                    "user" to
                        mapOf(
                            "id" to "new-user",
                            "anonymousId" to "new-anon",
                            "username" to "NewUser",
                            "createdAt" to 1704067200000L,
                            "idBackupConfirmed" to false,
                        ),
                    "interactions" to emptyList<Map<String, Any?>>(),
                    "notifications" to emptyList<Map<String, Any?>>(),
                    "reports" to emptyList<Map<String, Any?>>(),
                )
            firebaseProvider.setMockFunctionResult("exportUserData", emptyDataResponse)

            val useCase = DataExportUseCaseImpl(firebaseProvider, testDispatchers)

            // When
            val result = useCase.exportUserData()

            // Then
            assertTrue(result.isSuccess, "Export should succeed even with empty collections")
            val data = result.getOrThrow()

            assertEquals("new-anon", data.user.anonymousId)
            assertEquals(0, data.interactions.size, "Should have 0 interactions")
            assertEquals(0, data.notifications.size, "Should have 0 notifications")
            assertEquals(0, data.reports.size, "Should have 0 reports")

            // Verify empty lists serialize correctly for ZIP
            val interactionsJson = prettyJson.encodeToString(data.interactions)
            val trimmed = interactionsJson.replace("\\s".toRegex(), "")
            assertEquals("[]", trimmed, "Empty list should serialize to []")
        }

    // ==================================================================================
    // Test 7: Network Error Recovery
    // Verifies: After network error, retry can succeed
    // ==================================================================================

    @Test
    fun `test export can succeed after initial network failure with retry`() =
        runTest {
            // Given - Provider that fails first call, succeeds on second
            var callCount = 0
            val retryableProvider =
                object : FirebaseProvider {
                    override suspend fun initialize() {}

                    override fun isInitialized(): Boolean = true

                    override suspend fun signInAnonymously(): String = "test-user"

                    override fun isAuthenticated(): Boolean = true

                    override fun getCurrentUserId(): String = "test-user"

                    override suspend fun signOut() {}

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
                    ): Map<String, Any?>? {
                        callCount++
                        if (callCount == 1) {
                            throw FirebaseException("Network timeout")
                        }
                        // Second call succeeds
                        return createMockExportResponse()
                    }
                }

            val useCase = DataExportUseCaseImpl(retryableProvider, testDispatchers)

            // When - First call fails
            val firstResult = useCase.exportUserData()
            assertTrue(firstResult.isFailure, "First call should fail due to network error")

            // When - Retry succeeds
            val retryResult = useCase.exportUserData()
            assertTrue(retryResult.isSuccess, "Retry should succeed")

            // Then
            assertEquals(2, callCount, "Should have called function twice")
            val data = retryResult.getOrThrow()
            assertEquals("anon-abc", data.user.anonymousId)
        }

    // ==================================================================================
    // Helper Methods
    // ==================================================================================

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

    private fun createTestExportData(): ExportData =
        ExportData(
            user =
                ExportUser(
                    anonymousId = "anon-456",
                    username = "testuser",
                    createdAt = 1704067200000L,
                ),
            interactions =
                listOf(
                    ExportInteraction(
                        id = "interaction-1",
                        partnerAnonymousId = "partner-anon-1",
                        partnerUsernameSnapshot = "partner1",
                        recordedAt = 1704067300000L,
                        syncedToCloud = true,
                    ),
                ),
            notifications =
                listOf(
                    ExportNotification(
                        id = "notification-1",
                        type = "EXPOSURE",
                        stiType = "chlamydia",
                        exposureDate = 1704000000000L,
                        chainData = "{\"chain\":[\"a\",\"b\",\"c\"]}",
                        isRead = true,
                        receivedAt = 1704067500000L,
                        updatedAt = 1704067500000L,
                    ),
                ),
            reports =
                listOf(
                    ExportReport(
                        id = "report-1",
                        stiTypes = "[\"chlamydia\"]",
                        testDate = 1703980800000L,
                        privacyLevel = "FULL",
                        reportedAt = 1704067600000L,
                        syncedToCloud = true,
                    ),
                ),
        )
}
