package app.justfyi.domain.usecase

import app.justfyi.domain.model.Interaction
import app.justfyi.domain.model.NearbyUser

/**
 * Use case interface for recording interactions with nearby users.
 * Handles both single and batch (multi-select) interaction recording.
 *
 * Key responsibilities:
 * - Record single interaction with a nearby user
 * - Record batch interactions (multi-select capability)
 * - Capture username snapshot at recording time
 * - Local-first: save to database first, then sync to cloud
 * - Provide retry mechanism for failed recordings
 *
 * Independent Recording Model:
 * - User A records interaction without User B's consent or knowledge
 * - No handshake, no obligation that both users need to accept
 * - One-sided recording is intentional per spec
 */
interface RecordInteractionUseCase {
    /**
     * Records a single interaction with a nearby user.
     * The username is captured as a snapshot at recording time and preserved.
     *
     * Local-first behavior:
     * 1. Saves immediately to local database
     * 2. Attempts sync to cloud in background
     * 3. If sync fails, interaction is marked for retry
     *
     * @param nearbyUser The nearby user to record interaction with
     * @return Result containing the recorded Interaction or an error
     */
    suspend fun recordInteraction(nearbyUser: NearbyUser): Result<Interaction>

    /**
     * Records multiple interactions at once (multi-select capability).
     * All username snapshots are captured at recording time.
     *
     * Local-first behavior:
     * 1. Saves all interactions immediately to local database in a transaction
     * 2. Attempts sync to cloud for each interaction
     * 3. Failed syncs are marked for retry individually
     *
     * @param users List of nearby users to record interactions with
     * @return Result containing the list of recorded Interactions or an error
     */
    suspend fun recordInteractions(users: List<NearbyUser>): Result<List<Interaction>>

    /**
     * Retries syncing failed interactions to the cloud.
     * Called when connectivity is restored or user manually triggers retry.
     *
     * @return Result indicating success or failure, with count of synced interactions
     */
    suspend fun retryFailedRecordings(): Result<RetryResult>

    /**
     * Gets the count of interactions that failed to sync and need retry.
     *
     * @return Number of pending retry interactions
     */
    suspend fun getPendingRetryCount(): Int

    /**
     * Checks if there are any interactions pending retry.
     *
     * @return true if there are failed recordings that need retry
     */
    suspend fun hasPendingRetries(): Boolean
}

/**
 * Result of a retry operation.
 */
data class RetryResult(
    /** Number of interactions successfully synced */
    val successCount: Int,
    /** Number of interactions that still failed */
    val failureCount: Int,
    /** IDs of interactions that failed again (for UI display) */
    val failedIds: List<String>,
) {
    /** Whether all retries were successful */
    val allSucceeded: Boolean get() = failureCount == 0
}

/**
 * Sealed class representing interaction recording errors.
 */
sealed class RecordingError : Exception() {
    /**
     * Recording failed due to database error.
     */
    data class DatabaseError(
        override val message: String,
    ) : RecordingError()

    /**
     * Sync to cloud failed (recording is saved locally for retry).
     */
    data class SyncFailed(
        val interactionId: String,
        override val message: String,
    ) : RecordingError()

    /**
     * Invalid nearby user data provided.
     */
    data class InvalidUserData(
        override val message: String,
    ) : RecordingError()

    /**
     * Empty list provided for batch recording.
     */
    object EmptyUserList : RecordingError() {
        override val message: String = "Cannot record interactions with empty user list"
    }
}
