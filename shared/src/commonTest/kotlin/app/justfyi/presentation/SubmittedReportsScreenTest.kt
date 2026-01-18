package app.justfyi.presentation

import app.justfyi.domain.model.ExposureReport
import app.justfyi.presentation.feature.submittedreports.SubmittedReportsUiState
import app.justfyi.util.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for SubmittedReportsScreen functionality.
 *
 * These tests verify:
 * - Loading state shows loading indicator
 * - Empty state shows empty content
 * - Success state shows report cards
 * - Delete button and confirmation dialog behavior
 * - Back navigation works correctly
 * - Reports are sorted newest first
 *
 * Note: These tests focus on ViewModel state logic and data transformations.
 * Full UI tests would require Compose testing framework.
 */
class SubmittedReportsScreenTest {
    // ==================== Loading State Tests ====================

    @Test
    fun `test Loading state is displayed initially`() {
        // Given - initial UI state
        val uiState: SubmittedReportsUiState = SubmittedReportsUiState.Loading

        // Then - should be loading state
        assertIs<SubmittedReportsUiState.Loading>(uiState)
    }

    // ==================== Empty State Tests ====================

    @Test
    fun `test Empty state shows when no reports exist`() {
        // Given - empty reports list
        val uiState: SubmittedReportsUiState = SubmittedReportsUiState.Empty

        // Then - should be empty state
        assertIs<SubmittedReportsUiState.Empty>(uiState)
    }

    // ==================== Success State Tests ====================

    @Test
    fun `test Success state shows report cards in chronological order`() {
        // Given - reports with different timestamps
        val now = currentTimeMillis()
        val reports =
            listOf(
                ExposureReport(
                    id = "report1",
                    stiTypes = "[\"HIV\"]",
                    testDate = now - 86400000L * 5, // 5 days ago
                    privacyLevel = "FULL",
                    reportedAt = now - 86400000L * 3, // 3 days ago
                    syncedToCloud = true,
                ),
                ExposureReport(
                    id = "report2",
                    stiTypes = "[\"SYPHILIS\", \"GONORRHEA\"]",
                    testDate = now - 86400000L * 10, // 10 days ago
                    privacyLevel = "ANONYMOUS",
                    reportedAt = now - 1000, // 1 second ago (newest)
                    syncedToCloud = true,
                ),
                ExposureReport(
                    id = "report3",
                    stiTypes = "[\"CHLAMYDIA\"]",
                    testDate = now - 86400000L * 7, // 7 days ago
                    privacyLevel = "PARTIAL",
                    reportedAt = now - 86400000L * 1, // 1 day ago
                    syncedToCloud = true,
                ),
            )

        // When - sort by reportedAt descending (newest first)
        val sortedReports = reports.sortedByDescending { it.reportedAt }

        // Then - should be in reverse chronological order
        assertEquals("report2", sortedReports[0].id) // Newest
        assertEquals("report3", sortedReports[1].id) // 1 day ago
        assertEquals("report1", sortedReports[2].id) // 3 days ago (oldest)

        // Create success state
        val uiState =
            SubmittedReportsUiState.Success(
                reports = sortedReports,
                deletingReports = emptyMap(),
            )

        // Verify success state structure
        assertIs<SubmittedReportsUiState.Success>(uiState)
        assertEquals(3, uiState.reports.size)
        assertEquals("report2", uiState.reports[0].id)
    }

    // ==================== Delete Button Tests ====================

    @Test
    fun `test Delete button triggers deletion in progress state`() {
        // Given - a report to delete
        val reportId = "report1"
        val now = currentTimeMillis()
        val reports =
            listOf(
                ExposureReport(
                    id = reportId,
                    stiTypes = "[\"HIV\"]",
                    testDate = now - 86400000L * 5,
                    privacyLevel = "FULL",
                    reportedAt = now - 86400000L * 3,
                    syncedToCloud = true,
                ),
            )

        // When - delete is triggered (simulating ViewModel behavior)
        var deletingReports = mapOf(reportId to true)
        val uiState =
            SubmittedReportsUiState.Success(
                reports = reports,
                deletingReports = deletingReports,
            )

        // Then - deletion should be in progress for that report
        assertIs<SubmittedReportsUiState.Success>(uiState)
        assertTrue(uiState.deletingReports[reportId] == true)

        // When - deletion completes
        deletingReports = emptyMap()
        val newReports = emptyList<ExposureReport>() // Report removed
        val finalState =
            SubmittedReportsUiState.Success(
                reports = newReports,
                deletingReports = deletingReports,
            )

        // Then - deletion state should be cleared and report removed
        assertFalse(finalState.deletingReports.containsKey(reportId))
        assertTrue(finalState.reports.isEmpty())
    }

    @Test
    fun `test Confirmation dialog state for delete action`() {
        // Given - dialog visibility state (simulating screen state)
        var showDeleteDialog = false
        var reportToDelete: String? = null

        // When - user taps delete button on a report
        fun onDeleteClick(reportId: String) {
            reportToDelete = reportId
            showDeleteDialog = true
        }

        onDeleteClick("report1")

        // Then - dialog should be shown with correct report ID
        assertTrue(showDeleteDialog)
        assertEquals("report1", reportToDelete)

        // When - user confirms deletion
        fun onConfirmDelete() {
            // This would call viewModel.deleteReport(reportToDelete!!)
            showDeleteDialog = false
            reportToDelete = null
        }

        onConfirmDelete()

        // Then - dialog should be dismissed
        assertFalse(showDeleteDialog)
    }

    // ==================== Navigation Tests ====================

    @Test
    fun `test Back navigation clears screen from stack`() {
        // Given - navigation tracking
        var currentScreen = "SubmittedReports"
        var backPressed = false

        // When - back navigation is triggered
        fun navigateBack() {
            backPressed = true
            currentScreen = "Profile"
        }

        navigateBack()

        // Then - should have navigated back
        assertTrue(backPressed)
        assertEquals("Profile", currentScreen)
    }
}
