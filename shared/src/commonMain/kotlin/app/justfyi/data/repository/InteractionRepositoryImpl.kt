package app.justfyi.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.data.local.InteractionQueries
import app.justfyi.domain.model.Interaction
import app.justfyi.domain.repository.InteractionRepository
import app.justfyi.util.AppCoroutineDispatchers
import app.justfyi.util.HashUtils
import app.justfyi.util.Logger
import app.justfyi.util.currentTimeMillis
import app.justfyi.util.generateUuid
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.justfyi.data.local.Interaction as DbInteraction

/**
 * Multiplatform implementation of InteractionRepository using GitLive Firebase SDK.
 * Follows local-first pattern: writes to SQLDelight immediately, syncs to Firebase in background.
 * Username snapshots are preserved at recording time.
 *
 * This implementation uses the FirebaseProvider abstraction which works
 * on both Android and iOS through the GitLive SDK.
 *
 * DOMAIN-SEPARATED HASHING:
 * The ownerId is hashed using HashUtils.hashForInteraction() before storing to Firestore.
 * This prevents cross-collection correlation if the database is breached.
 * The partnerAnonymousId is already hashed by the BLE exchange (no additional hashing needed).
 */
@Inject
class InteractionRepositoryImpl(
    private val interactionQueries: InteractionQueries,
    private val firebaseProvider: FirebaseProvider,
    private val dispatchers: AppCoroutineDispatchers,
) : InteractionRepository {
    override suspend fun recordInteraction(
        partnerAnonymousId: String,
        partnerUsernameSnapshot: String,
    ): Interaction =
        withContext(dispatchers.io) {
            val interaction =
                Interaction(
                    id = generateUuid(),
                    partnerAnonymousId = partnerAnonymousId,
                    partnerUsernameSnapshot = partnerUsernameSnapshot,
                    recordedAt = currentTimeMillis(),
                    syncedToCloud = false,
                )

            // Local-first: save to SQLDelight immediately
            interactionQueries.insertInteraction(
                id = interaction.id,
                partner_anonymous_id = interaction.partnerAnonymousId,
                partner_username_snapshot = interaction.partnerUsernameSnapshot,
                recorded_at = interaction.recordedAt,
                synced_to_cloud = 0,
            )

            // Background sync to Firebase
            syncInteractionToCloud(interaction)

            interaction
        }

    override suspend fun recordBatchInteractions(interactions: List<Pair<String, String>>): List<Interaction> =
        withContext(dispatchers.io) {
            val now = currentTimeMillis()
            val createdInteractions =
                interactions.map { (anonymousId, usernameSnapshot) ->
                    Interaction(
                        id = generateUuid(),
                        partnerAnonymousId = anonymousId,
                        partnerUsernameSnapshot = usernameSnapshot,
                        recordedAt = now,
                        syncedToCloud = false,
                    )
                }

            // Local-first: save all to SQLDelight in a transaction
            interactionQueries.transaction {
                createdInteractions.forEach { interaction ->
                    interactionQueries.insertInteraction(
                        id = interaction.id,
                        partner_anonymous_id = interaction.partnerAnonymousId,
                        partner_username_snapshot = interaction.partnerUsernameSnapshot,
                        recorded_at = interaction.recordedAt,
                        synced_to_cloud = 0,
                    )
                }
            }

            // Background sync to Firebase
            createdInteractions.forEach { interaction ->
                syncInteractionToCloud(interaction)
            }

            createdInteractions
        }

    override fun getInteractions(): Flow<List<Interaction>> =
        interactionQueries
            .getAllInteractions()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getInteractionsInDateRange(
        startDate: Long,
        endDate: Long,
    ): List<Interaction> =
        withContext(dispatchers.io) {
            interactionQueries
                .getInteractionsInDateRange(startDate, endDate)
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun getInteractionsInLastDays(days: Int): List<Interaction> =
        withContext(dispatchers.io) {
            val cutoffDate = currentTimeMillis() - (days * DAY_IN_MILLIS)
            interactionQueries
                .getInteractionsWithinDays(cutoffDate)
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun deleteOldInteractions(): Int =
        withContext(dispatchers.io) {
            val cutoffDate = currentTimeMillis() - (RETENTION_DAYS * DAY_IN_MILLIS)
            val countBefore = interactionQueries.getInteractionCount().executeAsOne()
            interactionQueries.deleteInteractionsOlderThan(cutoffDate)
            val countAfter = interactionQueries.getInteractionCount().executeAsOne()
            (countBefore - countAfter).toInt()
        }

    override suspend fun getUnsyncedInteractions(): List<Interaction> =
        withContext(dispatchers.io) {
            interactionQueries
                .getUnsyncedInteractions()
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun syncToCloud(): Unit =
        withContext(dispatchers.io) {
            val unsyncedInteractions = interactionQueries.getUnsyncedInteractions().executeAsList()

            unsyncedInteractions.forEach { dbInteraction ->
                val interaction = dbInteraction.toDomain()
                if (syncInteractionToCloud(interaction)) {
                    interactionQueries.updateSyncStatus(synced_to_cloud = 1, id = interaction.id)
                }
            }
            Unit
        }

    override suspend fun deleteAllInteractions(): Unit =
        withContext(dispatchers.io) {
            interactionQueries.deleteAllInteractions()
            Unit
        }

    private suspend fun syncInteractionToCloud(interaction: Interaction): Boolean {
        return try {
            // Get current user ID for ownerId field required by Firestore security rules
            val ownerId = firebaseProvider.getCurrentUserId()
            if (ownerId == null) {
                Logger.w(TAG, "Cannot sync interaction - user not authenticated")
                return false
            }

            // Debug logging for hash computation
            Logger.d(TAG, "syncInteractionToCloud: ownerId='$ownerId'")

            // Hash the ownerId for domain-separated storage
            // This prevents cross-collection correlation if database is breached
            val hashedOwnerId = HashUtils.hashForInteraction(ownerId)
            Logger.d(TAG, "syncInteractionToCloud: hashedOwnerId='$hashedOwnerId'")

            val data =
                mapOf(
                    FIELD_OWNER_ID to hashedOwnerId,
                    FIELD_PARTNER_ANONYMOUS_ID to interaction.partnerAnonymousId,
                    FIELD_PARTNER_USERNAME_SNAPSHOT to interaction.partnerUsernameSnapshot,
                    FIELD_RECORDED_AT to interaction.recordedAt,
                )
            firebaseProvider.setDocument(
                collection = COLLECTION_INTERACTIONS,
                documentId = interaction.id,
                data = data,
                merge = false,
            )

            // Mark as synced locally
            interactionQueries.updateSyncStatus(synced_to_cloud = 1, id = interaction.id)
            true
        } catch (e: Exception) {
            // Sync failed, will retry later
            Logger.w(TAG, "Failed to sync interaction ${interaction.id}: ${e.message}")
            false
        }
    }

    private fun DbInteraction.toDomain(): Interaction =
        Interaction(
            id = id,
            partnerAnonymousId = partner_anonymous_id,
            partnerUsernameSnapshot = partner_username_snapshot,
            recordedAt = recorded_at,
            syncedToCloud = synced_to_cloud == 1L,
        )

    companion object {
        private const val TAG = "InteractionRepoImpl"
        const val COLLECTION_INTERACTIONS = "interactions"
        const val FIELD_OWNER_ID = "ownerId"
        const val FIELD_PARTNER_ANONYMOUS_ID = "partnerAnonymousId"
        const val FIELD_PARTNER_USERNAME_SNAPSHOT = "partnerUsernameSnapshot"
        const val FIELD_RECORDED_AT = "recordedAt"
        const val RETENTION_DAYS = 180
        const val DAY_IN_MILLIS = 24L * 60 * 60 * 1000
    }
}
