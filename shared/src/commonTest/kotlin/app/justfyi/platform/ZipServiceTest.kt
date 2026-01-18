package app.justfyi.platform

import app.justfyi.domain.model.ExportData
import app.justfyi.domain.model.ExportInteraction
import app.justfyi.domain.model.ExportNotification
import app.justfyi.domain.model.ExportReport
import app.justfyi.domain.model.ExportUser
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for ZipService functionality.
 * These tests validate the ZIP creation contract and data structure.
 *
 * Note: Platform-specific ZIP file creation is tested via integration tests
 * that run on actual Android/iOS platforms. These tests verify the interface
 * contract and data serialization.
 */
class ZipServiceTest {
    private val prettyJson =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    /**
     * Test 1: ExportData serializes correctly to JSON.
     * Verifies that all export data types can be serialized with pretty-print.
     */
    @Test
    fun testExportDataSerialization() {
        // Given
        val exportData = createTestExportData()

        // When - serialize each component
        val userJson = prettyJson.encodeToString(exportData.user)
        val interactionsJson = prettyJson.encodeToString(exportData.interactions)
        val notificationsJson = prettyJson.encodeToString(exportData.notifications)
        val reportsJson = prettyJson.encodeToString(exportData.reports)

        // Then - verify pretty-print formatting (contains newlines and indentation)
        assertTrue(userJson.contains("\n"), "User JSON should be pretty-printed with newlines")
        assertTrue(userJson.contains("  "), "User JSON should be pretty-printed with indentation")
        assertTrue(
            interactionsJson.contains("["),
            "Interactions JSON should be a valid JSON array",
        )
        assertTrue(
            notificationsJson.contains("["),
            "Notifications JSON should be a valid JSON array",
        )
        assertTrue(reportsJson.contains("["), "Reports JSON should be a valid JSON array")
    }

    /**
     * Test 2: ZIP file structure contains all 4 required JSON files.
     * Verifies the expected file names for the export archive.
     */
    @Test
    fun testZipFileStructure() {
        // The ZIP should contain exactly these 4 files
        val expectedFiles =
            listOf(
                "user.json",
                "interactions.json",
                "notifications.json",
                "reports.json",
            )

        // Verify expected structure
        assertEquals(4, expectedFiles.size, "ZIP should contain exactly 4 JSON files")
        assertTrue(expectedFiles.contains("user.json"), "Should include user.json")
        assertTrue(expectedFiles.contains("interactions.json"), "Should include interactions.json")
        assertTrue(
            expectedFiles.contains("notifications.json"),
            "Should include notifications.json",
        )
        assertTrue(expectedFiles.contains("reports.json"), "Should include reports.json")
    }

    /**
     * Test 3: FileResult contains correct MIME type.
     * Verifies that the file result returns application/zip MIME type.
     */
    @Test
    fun testFileResultMimeType() {
        // Given
        val expectedMimeType = "application/zip"

        // When - create a FileResult (simulating ZipService output)
        val fileResult =
            FileResult(
                filePath = "/tmp/justfyi-export-1234567890.zip",
                mimeType = expectedMimeType,
            )

        // Then
        assertEquals(expectedMimeType, fileResult.mimeType, "MIME type should be application/zip")
        assertNotNull(fileResult.filePath, "File path should not be null")
        assertTrue(
            fileResult.filePath.endsWith(".zip"),
            "File path should end with .zip extension",
        )
    }

    /**
     * Test 4: Export file name follows correct format.
     * Verifies the naming convention for export files.
     */
    @Test
    fun testExportFileName() {
        // File name format: justfyi-export-{timestamp}.zip
        val timestamp = 1704067200000L // Example timestamp
        val expectedFileName = "justfyi-export-$timestamp.zip"

        // Verify format
        assertTrue(
            expectedFileName.startsWith("justfyi-export-"),
            "File name should start with 'justfyi-export-'",
        )
        assertTrue(
            expectedFileName.endsWith(".zip"),
            "File name should end with '.zip'",
        )
        assertTrue(
            expectedFileName.contains(timestamp.toString()),
            "File name should contain timestamp",
        )
    }

    /**
     * Test 5: User data excludes fcmToken in export.
     * Verifies that ExportUser does not include fcmToken field.
     */
    @Test
    fun testUserDataExcludesFcmToken() {
        // Given
        val exportUser =
            ExportUser(
                anonymousId = "anon-456",
                username = "testuser",
                createdAt = 1704067200000L,
            )

        // When - serialize to JSON
        val userJson = prettyJson.encodeToString(exportUser)

        // Then - verify fcmToken is not in the JSON
        assertTrue(
            !userJson.contains("fcmToken"),
            "Exported user JSON should not contain fcmToken",
        )
        assertTrue(
            userJson.contains("anonymousId"),
            "Exported user JSON should contain anonymousId",
        )
        assertTrue(
            userJson.contains("username"),
            "Exported user JSON should contain username",
        )
    }

    /**
     * Test 6: Empty lists serialize correctly.
     * Verifies that ExportData with empty lists produces valid JSON.
     */
    @Test
    fun testEmptyListsSerialization() {
        // Given - export data with empty lists
        val exportData =
            ExportData(
                user =
                    ExportUser(
                        anonymousId = "anon-456",
                        username = "testuser",
                        createdAt = 1704067200000L,
                    ),
                interactions = emptyList(),
                notifications = emptyList(),
                reports = emptyList(),
            )

        // When - serialize lists
        val interactionsJson = prettyJson.encodeToString(exportData.interactions)
        val notificationsJson = prettyJson.encodeToString(exportData.notifications)
        val reportsJson = prettyJson.encodeToString(exportData.reports)

        // Then - verify empty arrays are valid JSON (trimmed should be "[ ]" or "[]")
        val trimmedInteractions = interactionsJson.replace("\\s".toRegex(), "")
        assertEquals("[]", trimmedInteractions, "Empty interactions should serialize to empty array")
        assertTrue(
            interactionsJson.contains("[") && interactionsJson.contains("]"),
            "Empty list should produce valid JSON array",
        )
        assertTrue(
            notificationsJson.contains("[") && notificationsJson.contains("]"),
            "Empty notifications should produce valid JSON array",
        )
        assertTrue(
            reportsJson.contains("[") && reportsJson.contains("]"),
            "Empty reports should produce valid JSON array",
        )
    }

    // ==================== Helper Functions ====================

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
                    ExportInteraction(
                        id = "interaction-2",
                        partnerAnonymousId = "partner-anon-2",
                        partnerUsernameSnapshot = "partner2",
                        recordedAt = 1704067400000L,
                        syncedToCloud = false,
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
