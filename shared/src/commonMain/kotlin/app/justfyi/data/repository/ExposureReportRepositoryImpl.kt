package app.justfyi.data.repository

import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.data.local.ExposureReportQueries
import app.justfyi.domain.model.ExposureReport
import app.justfyi.domain.model.TestStatus
import app.justfyi.domain.repository.ExposureReportRepository
import app.justfyi.util.AppCoroutineDispatchers
import app.justfyi.util.HashUtils
import app.justfyi.util.Logger
import app.justfyi.util.currentTimeMillis
import app.justfyi.util.generateUuid
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.withContext
import app.justfyi.data.local.ExposureReport as DbExposureReport

/**
 * Multiplatform implementation of ExposureReportRepository using GitLive Firebase SDK.
 * Reports are saved locally first, then submitted via Cloud Functions.
 *
 * This implementation uses the FirebaseProvider abstraction which works
 * on both Android and iOS through the GitLive SDK.
 *
 * CLOUD FUNCTION FLOW:
 * - Positive reports call `reportPositiveTest` which handles chain propagation
 * - The backend computes domain-separated hashes for privacy
 * - Chain linking is automatically detected if user has existing notifications
 */
@Inject
class ExposureReportRepositoryImpl(
    private val exposureReportQueries: ExposureReportQueries,
    private val firebaseProvider: FirebaseProvider,
    private val dispatchers: AppCoroutineDispatchers,
) : ExposureReportRepository {
    override suspend fun submitReport(
        stiTypes: String,
        testDate: Long,
        privacyLevel: String,
        testResult: TestStatus,
    ): ExposureReport =
        withContext(dispatchers.io) {
            val report =
                ExposureReport(
                    id = generateUuid(),
                    stiTypes = stiTypes,
                    testDate = testDate,
                    privacyLevel = privacyLevel,
                    reportedAt = currentTimeMillis(),
                    syncedToCloud = false,
                    testResult = testResult,
                )

            // Local-first: save to SQLDelight immediately
            // Note: contacted_ids column kept for schema compatibility but left empty
            exposureReportQueries.insertReport(
                id = report.id,
                sti_types = report.stiTypes,
                test_date = report.testDate,
                privacy_level = report.privacyLevel,
                contacted_ids = "[]", // Empty - backend handles contact discovery
                reported_at = report.reportedAt,
                synced_to_cloud = 0,
                test_result = report.testResult.name,
            )

            // Submit to Firebase and trigger Cloud Function
            submitToCloud(report)

            report
        }

    override suspend fun getPendingReports(): List<ExposureReport> =
        withContext(dispatchers.io) {
            exposureReportQueries
                .getPendingSyncReports()
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun markSynced(reportId: String): Unit =
        withContext(dispatchers.io) {
            exposureReportQueries.markAsSynced(reportId)
            Unit
        }

    override suspend fun getReportById(reportId: String): ExposureReport? =
        withContext(dispatchers.io) {
            exposureReportQueries
                .getReportById(reportId)
                .executeAsOneOrNull()
                ?.toDomain()
        }

    override suspend fun getAllReports(): List<ExposureReport> =
        withContext(dispatchers.io) {
            exposureReportQueries
                .getAllReports()
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun retryPendingReports(): Unit =
        withContext(dispatchers.io) {
            val pendingReports = exposureReportQueries.getPendingSyncReports().executeAsList()

            pendingReports.forEach { dbReport ->
                val report = dbReport.toDomain()
                submitToCloud(report)
            }
            Unit
        }

    override suspend fun deleteAllReports(): Unit =
        withContext(dispatchers.io) {
            exposureReportQueries.deleteAllReports()
            Unit
        }

    override suspend fun deleteReport(reportId: String): Result<Unit> =
        withContext(dispatchers.io) {
            try {
                Logger.d(TAG, "deleteReport: Starting deletion for reportId=$reportId")

                // Verify user is authenticated before calling Cloud Function
                if (!firebaseProvider.isAuthenticated()) {
                    Logger.e(TAG, "deleteReport: User not authenticated")
                    return@withContext Result.failure(
                        IllegalStateException("Please sign in to delete reports"),
                    )
                }

                // Call the Cloud Function to delete the report and notify impacted users
                val functionData = mapOf("reportId" to reportId)
                firebaseProvider.callFunction(FUNCTION_DELETE_REPORT, functionData)

                Logger.d(TAG, "deleteReport: Cloud Function succeeded, removing local record")

                // On success, remove the report from local database
                exposureReportQueries.deleteReport(reportId)

                Logger.d(TAG, "deleteReport: Local record removed successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Logger.e(TAG, "deleteReport: Failed to delete report $reportId", e)
                Result.failure(e)
            }
        }

    override suspend fun syncReportsFromCloud(): Int =
        withContext(dispatchers.io) {
            val userId = firebaseProvider.getCurrentUserId()
            Logger.d(TAG, "syncReportsFromCloud: userId=$userId")
            if (userId == null) return@withContext 0

            val hashedReporterId = HashUtils.hashForReport(userId)
            Logger.d(TAG, "syncReportsFromCloud: hashedReporterId=$hashedReporterId")

            val remoteReports = firebaseProvider.queryCollection(
                collection = COLLECTION_REPORTS,
                whereField = "reporterId",
                whereValue = hashedReporterId,
            )

            Logger.d(TAG, "syncReportsFromCloud: Found ${remoteReports.size} remote reports")

            var syncedCount = 0
            for (remoteReport in remoteReports) {
                Logger.d(TAG, "syncReportsFromCloud: Raw report keys: ${remoteReport.keys}")

                val reportId = remoteReport["_documentId"] as? String
                Logger.d(TAG, "syncReportsFromCloud: reportId=$reportId")
                if (reportId == null) continue

                val existingReport = exposureReportQueries.getReportById(reportId).executeAsOneOrNull()
                if (existingReport != null) {
                    Logger.d(TAG, "syncReportsFromCloud: Report $reportId already exists locally")
                    continue
                }

                val stiTypes = remoteReport["stiTypes"]
                val testDate = remoteReport["testDate"]
                val privacyLevel = remoteReport["privacyLevel"]
                val reportedAt = remoteReport["reportedAt"]
                val testResult = remoteReport["testResult"]

                exposureReportQueries.insertReport(
                    id = reportId,
                    sti_types = stiTypes as String,
                    test_date = (testDate as Number).toLong(),
                    privacy_level = privacyLevel as String,
                    contacted_ids = "[]",
                    reported_at = (reportedAt as Number).toLong(),
                    synced_to_cloud = 1,
                    test_result = testResult as String,
                )
                syncedCount++
                Logger.d(TAG, "syncReportsFromCloud: Inserted report $reportId")
            }

            Logger.d(TAG, "syncReportsFromCloud: Synced $syncedCount new reports total")
            syncedCount
        }

    private suspend fun submitToCloud(report: ExposureReport) {
        try {
            Logger.d(TAG, "Submitting ${report.testResult} report ${report.id}")

            // Call the appropriate Cloud Function based on test result
            val (functionName, data) = when (report.testResult) {
                TestStatus.POSITIVE -> {
                    FUNCTION_REPORT_POSITIVE to mapOf(
                        "stiTypes" to report.stiTypes,
                        "testDate" to report.testDate,
                        "privacyLevel" to report.privacyLevel,
                    )
                }
                TestStatus.NEGATIVE -> {
                    // Backend expects stiType as a single string, not a JSON array
                    // Extract the first STI type from the JSON array
                    val firstStiType = extractFirstStiType(report.stiTypes)
                    FUNCTION_REPORT_NEGATIVE to mapOf(
                        "stiType" to firstStiType,
                    )
                }
                else -> {
                    Logger.w(TAG, "Unknown test result: ${report.testResult}, defaulting to positive")
                    FUNCTION_REPORT_POSITIVE to mapOf(
                        "stiTypes" to report.stiTypes,
                        "testDate" to report.testDate,
                        "privacyLevel" to report.privacyLevel,
                    )
                }
            }

            val result = firebaseProvider.callFunction(
                functionName = functionName,
                data = data,
            )

            val success = result?.get("success") as? Boolean ?: false
            if (success) {
                // Update local report with server-generated ID
                val serverReportId = result["reportId"] as? String
                if (serverReportId != null && serverReportId != report.id) {
                    exposureReportQueries.deleteReport(report.id)
                    exposureReportQueries.insertReport(
                        id = serverReportId,
                        sti_types = report.stiTypes,
                        test_date = report.testDate,
                        privacy_level = report.privacyLevel,
                        contacted_ids = "[]",
                        reported_at = report.reportedAt,
                        synced_to_cloud = 1,
                        test_result = report.testResult.name,
                    )
                    Logger.d(TAG, "Report synced with server ID: $serverReportId")
                } else {
                    exposureReportQueries.markAsSynced(report.id)
                    Logger.d(TAG, "Report ${report.id} submitted successfully")
                }

                // Log chain linking info if available (only for positive reports)
                val linkedReportId = result["linkedReportId"] as? String
                if (linkedReportId != null) {
                    Logger.d(TAG, "Report linked to previous report: $linkedReportId")
                }
            } else {
                Logger.w(TAG, "$functionName returned success=false")
            }
        } catch (e: Exception) {
            // Cloud Function call failed, report remains pending
            // Will be retried via retryPendingReports()
            Logger.w(TAG, "Failed to submit report ${report.id}: ${e.message}")
        }
    }

    /**
     * Extracts the first STI type from a JSON array string.
     * e.g., '["HIV","SYPHILIS"]' -> "HIV"
     *
     * @param stiTypesJson JSON array string of STI types
     * @return The first STI type, or null if parsing fails or array is empty
     */
    private fun extractFirstStiType(stiTypesJson: String): String? {
        return try {
            // Simple parsing without kotlinx.serialization dependency
            // Expected format: ["TYPE1","TYPE2",...]
            val trimmed = stiTypesJson.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                return null
            }
            val content = trimmed.drop(1).dropLast(1).trim()
            if (content.isEmpty()) {
                return null
            }
            // Find the first quoted string
            val firstQuoteStart = content.indexOf('"')
            if (firstQuoteStart == -1) {
                return null
            }
            val firstQuoteEnd = content.indexOf('"', firstQuoteStart + 1)
            if (firstQuoteEnd == -1) {
                return null
            }
            content.substring(firstQuoteStart + 1, firstQuoteEnd)
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to extract first STI type from: $stiTypesJson")
            null
        }
    }

    private fun DbExposureReport.toDomain(): ExposureReport =
        ExposureReport(
            id = id,
            stiTypes = sti_types,
            testDate = test_date,
            privacyLevel = privacy_level,
            reportedAt = reported_at,
            syncedToCloud = synced_to_cloud == 1L,
            testResult = try {
                TestStatus.valueOf(test_result)
            } catch (e: IllegalArgumentException) {
                TestStatus.POSITIVE // Default for legacy reports
            },
        )

    companion object {
        private const val TAG = "ExposureReportRepoImpl"
        private const val COLLECTION_REPORTS = "reports"
        const val FUNCTION_REPORT_POSITIVE = "reportPositiveTest"
        const val FUNCTION_REPORT_NEGATIVE = "reportNegativeTest"
        const val FUNCTION_DELETE_REPORT = "deleteExposureReport"
    }
}
