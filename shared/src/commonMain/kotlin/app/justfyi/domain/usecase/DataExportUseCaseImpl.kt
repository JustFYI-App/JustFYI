package app.justfyi.domain.usecase

import app.justfyi.data.firebase.FirebaseException
import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.domain.model.ExportData
import app.justfyi.domain.model.ExportInteraction
import app.justfyi.domain.model.ExportNotification
import app.justfyi.domain.model.ExportReport
import app.justfyi.domain.model.ExportUser
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.withContext

/**
 * Implementation of DataExportUseCase that calls the exportUserData Cloud Function.
 *
 * This implementation:
 * - Verifies user authentication before making the call
 * - Calls the "exportUserData" Cloud Function with no parameters
 * - Parses the Map<String, Any?>? response into ExportData domain model
 * - Handles missing or null fields gracefully with defaults
 * - Maps all errors to appropriate Result.failure types
 *
 * The Cloud Function returns:
 * ```
 * {
 *   user: { id, anonymousId, username, createdAt, idBackupConfirmed },
 *   interactions: [{ id, partnerAnonymousId, partnerUsernameSnapshot, recordedAt, syncedToCloud }],
 *   notifications: [{ id, type, stiType?, exposureDate?, chainData, isRead, receivedAt, updatedAt }],
 *   reports: [{ id, stiTypes, testDate, privacyLevel, reportedAt, syncedToCloud }]
 * }
 * ```
 *
 * Note: The user object does NOT include fcmToken (excluded server-side for privacy).
 * Note: chainData in notifications remains as raw JSON string (not parsed).
 */
@Inject
class DataExportUseCaseImpl(
    private val firebaseProvider: FirebaseProvider,
    private val dispatchers: AppCoroutineDispatchers,
) : DataExportUseCase {
    override suspend fun exportUserData(): Result<ExportData> =
        withContext(dispatchers.io) {
            try {
                // Verify user is authenticated
                if (!firebaseProvider.isAuthenticated()) {
                    return@withContext Result.failure(
                        IllegalStateException("User must be authenticated to export data"),
                    )
                }

                // Call the Cloud Function
                val response = firebaseProvider.callFunction(FUNCTION_NAME, emptyMap())

                // Handle null response
                if (response == null) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Export function returned null response"),
                    )
                }

                // Parse response to ExportData
                val exportData = parseResponse(response)
                Result.success(exportData)
            } catch (e: FirebaseException) {
                // Re-throw Firebase exceptions as-is
                Result.failure(e)
            } catch (e: Exception) {
                // Wrap other exceptions
                Result.failure(
                    IllegalArgumentException("Failed to parse export data: ${e.message}", e),
                )
            }
        }

    /**
     * Parses the Cloud Function response into an ExportData domain model.
     *
     * @param response The raw Map response from the Cloud Function
     * @return ExportData containing all parsed data
     * @throws IllegalArgumentException if required fields are missing
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseResponse(response: Map<String, Any?>): ExportData {
        // Parse user (may be null if user document doesn't exist yet)
        val userMap = response["user"] as? Map<String, Any?>
        val user = if (userMap != null) parseUser(userMap) else createEmptyUser()

        // Parse interactions (may be empty list)
        val interactionsList = response["interactions"] as? List<Map<String, Any?>> ?: emptyList()
        val interactions = interactionsList.map { parseInteraction(it) }

        // Parse notifications (may be empty list)
        val notificationsList = response["notifications"] as? List<Map<String, Any?>> ?: emptyList()
        val notifications = notificationsList.map { parseNotification(it) }

        // Parse reports (may be empty list)
        val reportsList = response["reports"] as? List<Map<String, Any?>> ?: emptyList()
        val reports = reportsList.map { parseReport(it) }

        return ExportData(
            user = user,
            interactions = interactions,
            notifications = notifications,
            reports = reports,
        )
    }

    /**
     * Parses user data from the response map.
     */
    private fun parseUser(map: Map<String, Any?>): ExportUser =
        ExportUser(
            anonymousId = map["anonymousId"] as? String ?: "",
            username = map["username"] as? String ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        )

    /**
     * Creates an empty user when the user document doesn't exist in Firestore.
     * This can happen if the user has authenticated but hasn't completed onboarding.
     */
    private fun createEmptyUser(): ExportUser =
        ExportUser(
            anonymousId = "",
            username = "",
            createdAt = 0L,
        )

    /**
     * Parses interaction data from the response map.
     */
    private fun parseInteraction(map: Map<String, Any?>): ExportInteraction =
        ExportInteraction(
            id = map["id"] as? String ?: "",
            partnerAnonymousId = map["partnerAnonymousId"] as? String ?: "",
            partnerUsernameSnapshot = map["partnerUsernameSnapshot"] as? String ?: "",
            recordedAt = (map["recordedAt"] as? Number)?.toLong() ?: 0L,
            syncedToCloud = map["syncedToCloud"] as? Boolean ?: false,
        )

    /**
     * Parses notification data from the response map.
     * Note: stiType and exposureDate are optional fields.
     * Note: chainData remains as raw JSON string.
     */
    private fun parseNotification(map: Map<String, Any?>): ExportNotification =
        ExportNotification(
            id = map["id"] as? String ?: "",
            type = map["type"] as? String ?: "",
            stiType = map["stiType"] as? String,
            exposureDate = (map["exposureDate"] as? Number)?.toLong(),
            chainData = map["chainData"] as? String ?: "{}",
            isRead = map["isRead"] as? Boolean ?: false,
            receivedAt = (map["receivedAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
        )

    /**
     * Parses report data from the response map.
     */
    private fun parseReport(map: Map<String, Any?>): ExportReport =
        ExportReport(
            id = map["id"] as? String ?: "",
            stiTypes = map["stiTypes"] as? String ?: "[]",
            testDate = (map["testDate"] as? Number)?.toLong() ?: 0L,
            privacyLevel = map["privacyLevel"] as? String ?: "",
            reportedAt = (map["reportedAt"] as? Number)?.toLong() ?: 0L,
            syncedToCloud = map["syncedToCloud"] as? Boolean ?: false,
        )

    companion object {
        /** Name of the Cloud Function for exporting user data */
        private const val FUNCTION_NAME = "exportUserData"
    }
}
