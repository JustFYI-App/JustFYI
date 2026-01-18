package app.justfyi.domain.usecase

import app.justfyi.domain.model.ExportData

/**
 * Use case for exporting user data from Firestore.
 *
 * This use case provides GDPR-compliant data export functionality,
 * allowing users to download all their server-side data from Firebase.
 *
 * The export includes data from 4 Firestore collections:
 * - users: User profile data (fcmToken excluded for privacy)
 * - interactions: Recorded interactions with other users
 * - notifications: Exposure notifications received
 * - reports: Exposure reports submitted by the user
 *
 * Expected error types:
 * - IllegalStateException: User is not authenticated
 * - FirebaseException: Network or Cloud Function errors
 * - IllegalArgumentException: Response parsing errors
 *
 * Usage:
 * ```
 * val result = dataExportUseCase.exportUserData()
 * result.onSuccess { exportData ->
 *     // Process exportData (create ZIP, share, etc.)
 * }.onFailure { error ->
 *     // Handle error (show message, retry, etc.)
 * }
 * ```
 */
interface DataExportUseCase {
    /**
     * Exports all user data from Firestore via the exportUserData Cloud Function.
     *
     * This function:
     * 1. Verifies the user is authenticated
     * 2. Calls the "exportUserData" Cloud Function
     * 3. Parses the response into ExportData domain model
     * 4. Returns Result.success with ExportData or Result.failure with appropriate error
     *
     * @return Result containing ExportData on success, or an exception on failure.
     *         Possible exceptions:
     *         - IllegalStateException if user is not authenticated
     *         - FirebaseException if Cloud Function call fails
     *         - IllegalArgumentException if response cannot be parsed
     */
    suspend fun exportUserData(): Result<ExportData>
}
