package app.justfyi.domain.repository

import app.justfyi.domain.model.Interaction
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for interaction data operations.
 * Follows local-first pattern: write to local DB first, then sync to Firebase in background.
 */
interface InteractionRepository {
    /**
     * Records a single interaction with another user.
     * The partner's username is captured as a snapshot at recording time.
     * Local-first: saves locally, then syncs to Firebase in background.
     *
     * @param partnerAnonymousId The partner's anonymous ID
     * @param partnerUsernameSnapshot The partner's username at recording time
     * @return The created interaction
     */
    suspend fun recordInteraction(
        partnerAnonymousId: String,
        partnerUsernameSnapshot: String,
    ): Interaction

    /**
     * Records multiple interactions at once (multi-select capability).
     * Each partner's username is captured as a snapshot at recording time.
     * Local-first: saves all locally, then syncs to Firebase in background.
     *
     * @param interactions List of pairs of (anonymousId, usernameSnapshot)
     * @return The list of created interactions
     */
    suspend fun recordBatchInteractions(interactions: List<Pair<String, String>>): List<Interaction>

    /**
     * Gets all interactions as a Flow for reactive updates.
     * Ordered by date (newest first).
     */
    fun getInteractions(): Flow<List<Interaction>>

    /**
     * Gets interactions within a specific date range.
     * Used for calculating exposure window in reports.
     *
     * @param startDate Start of the range (millis since epoch, inclusive)
     * @param endDate End of the range (millis since epoch, inclusive)
     * @return List of interactions within the range
     */
    suspend fun getInteractionsInDateRange(
        startDate: Long,
        endDate: Long,
    ): List<Interaction>

    /**
     * Gets interactions from the last N days.
     * Used for the 120-day retention period queries.
     *
     * @param days Number of days to look back
     * @return List of interactions within the period
     */
    suspend fun getInteractionsInLastDays(days: Int): List<Interaction>

    /**
     * Deletes interactions older than 120 days.
     * Called by the cleanup routine for data retention compliance.
     *
     * @return Number of interactions deleted
     */
    suspend fun deleteOldInteractions(): Int

    /**
     * Gets interactions that haven't been synced to cloud yet.
     */
    suspend fun getUnsyncedInteractions(): List<Interaction>

    /**
     * Triggers a sync of unsynced interactions to Firebase.
     * Should be called when connectivity is restored.
     */
    suspend fun syncToCloud()

    /**
     * Deletes all interactions (GDPR compliance).
     */
    suspend fun deleteAllInteractions()
}
