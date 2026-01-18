package app.justfyi.domain.repository

import app.justfyi.domain.model.ExposureReport
import app.justfyi.domain.model.TestStatus

/**
 * Repository interface for test report operations.
 * Reports are saved locally first, then submitted to Firebase
 * to trigger the Cloud Function for notification processing.
 *
 * Note: Contact selection (contactedIds) has been removed from the submission.
 * The backend now automatically determines contacts using unidirectional
 * graph traversal with 10-hop chain propagation.
 */
interface ExposureReportRepository {
    /**
     * Submits a test report (positive or negative).
     * Local-first: saves locally, then triggers the Cloud Function.
     *
     * For positive reports, the backend automatically determines which contacts
     * to notify by querying interactions where partnerAnonymousId matches the
     * reporter's ID. This unidirectional approach ensures users can only notify
     * those who recorded interactions with them.
     *
     * For negative reports, the backend updates the reporter's notification
     * chain data and propagates the negative status to downstream users.
     *
     * @param stiTypes JSON array string of STI types
     * @param testDate Date of the test (millis since epoch)
     * @param privacyLevel Level of disclosure ("FULL", "PARTIAL", "ANONYMOUS")
     * @param testResult The test result: POSITIVE or NEGATIVE
     * @return The created report
     */
    suspend fun submitReport(
        stiTypes: String,
        testDate: Long,
        privacyLevel: String,
        testResult: TestStatus = TestStatus.POSITIVE,
    ): ExposureReport

    /**
     * Gets all reports that haven't been synced to cloud yet.
     * These are pending reports that need to be submitted when connectivity is restored.
     */
    suspend fun getPendingReports(): List<ExposureReport>

    /**
     * Marks a report as synced after successful submission to Firebase.
     * @param reportId The report ID
     */
    suspend fun markSynced(reportId: String)

    /**
     * Gets a report by ID.
     * @param reportId The report ID
     * @return The report, or null if not found
     */
    suspend fun getReportById(reportId: String): ExposureReport?

    /**
     * Gets all reports submitted by the user.
     * Ordered by date (newest first).
     */
    suspend fun getAllReports(): List<ExposureReport>

    /**
     * Retries submitting pending reports to Firebase.
     * Should be called when connectivity is restored.
     */
    suspend fun retryPendingReports()

    /**
     * Deletes all reports (GDPR compliance).
     */
    suspend fun deleteAllReports()

    /**
     * Deletes a specific report by calling the Cloud Function and removing the local record.
     *
     * This method performs two operations:
     * 1. Calls the `deleteExposureReport` Cloud Function which:
     *    - Validates that the authenticated user owns the report
     *    - Marks existing notifications for this report as deleted
     *    - Creates REPORT_DELETED notifications for impacted users
     * 2. On success, removes the report from the local SQLDelight database
     *
     * @param reportId The unique identifier of the report to delete
     * @return Result.success(Unit) if deletion succeeded, Result.failure with exception otherwise
     */
    suspend fun deleteReport(reportId: String): Result<Unit>

    /**
     * Syncs reports from Firestore to the local database.
     *
     * This method fetches the user's reports from Firestore and merges them
     * with local reports. It's useful for:
     * - Restoring reports after app data is cleared
     * - Syncing reports across devices
     * - Initial data load after login
     *
     * Reports are matched by ID - existing local reports are updated,
     * new remote reports are inserted.
     *
     * @return The number of reports synced from remote
     */
    suspend fun syncReportsFromCloud(): Int
}
