package app.justfyi.domain.usecase

import app.justfyi.domain.model.Interaction
import kotlinx.coroutines.flow.Flow

/**
 * Use case interface for accessing interaction history.
 * Provides methods to query, filter, and manage recorded interactions.
 *
 * Key responsibilities:
 * - Get full interaction history as a Flow for reactive updates
 * - Query interactions within specific date windows (for exposure reporting)
 * - Delete old interactions (>180 days) for data retention compliance
 *
 * Data Retention:
 * - Interactions are retained for 180 days per GDPR/privacy requirements (covers HPV incubation)
 * - Cleanup should be triggered periodically
 */
interface InteractionHistoryUseCase {
    /**
     * Gets the full interaction history as a Flow.
     * Updates automatically when new interactions are recorded.
     * Ordered by date (newest first).
     *
     * @return Flow of all interactions, ordered by recordedAt descending
     */
    fun getInteractionHistory(): Flow<List<Interaction>>

    /**
     * Gets interactions within a specific date window.
     * Used for exposure reporting to find contacts within the exposure period.
     *
     * @param startDate Start of the window (inclusive, millis since epoch)
     * @param endDate End of the window (inclusive, millis since epoch)
     * @return List of interactions within the date range
     */
    suspend fun getInteractionsInWindow(
        startDate: Long,
        endDate: Long,
    ): List<Interaction>

    /**
     * Gets interactions from the last N days.
     * Useful for quick queries like "last 30 days" or "last 120 days".
     *
     * @param days Number of days to look back from now
     * @return List of interactions within the period
     */
    suspend fun getInteractionsInLastDays(days: Int): List<Interaction>

    /**
     * Deletes interactions older than 180 days.
     * Should be called periodically for data retention compliance.
     *
     * @return Number of interactions deleted
     */
    suspend fun deleteOldInteractions(): Int

    /**
     * Gets the total count of interactions in history.
     *
     * @return Total number of recorded interactions
     */
    suspend fun getInteractionCount(): Long

    /**
     * Checks if there are any interactions in history.
     *
     * @return true if at least one interaction exists
     */
    suspend fun hasInteractions(): Boolean

    /**
     * Gets a single interaction by its ID.
     *
     * @param interactionId The unique identifier of the interaction
     * @return The interaction if found, null otherwise
     */
    suspend fun getInteractionById(interactionId: String): Interaction?

    /**
     * Deletes all interactions (GDPR compliance - account deletion).
     * This is called when user requests account deletion.
     */
    suspend fun deleteAllInteractions()

    companion object {
        /** Standard retention period in days (covers HPV 30-180 day incubation) */
        const val RETENTION_DAYS = 180
    }
}
