package app.justfyi.domain.usecase

import app.justfyi.domain.model.Interaction
import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.repository.InteractionRepository
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.withContext

/**
 * Multiplatform implementation of RecordInteractionUseCase.
 * Handles interaction recording with local-first approach.
 *
 * Key features:
 * - Records single and batch interactions
 * - Captures username snapshot at recording time
 * - Local-first: saves to database immediately, syncs in background
 * - Provides retry mechanism for failed cloud syncs
 */
@Inject
class RecordInteractionUseCaseImpl(
    private val interactionRepository: InteractionRepository,
    private val dispatchers: AppCoroutineDispatchers,
) : RecordInteractionUseCase {
    override suspend fun recordInteraction(nearbyUser: NearbyUser): Result<Interaction> =
        withContext(dispatchers.io) {
            try {
                // Validate input
                if (nearbyUser.anonymousIdHash.isBlank()) {
                    return@withContext Result.failure(
                        RecordingError.InvalidUserData("User anonymous ID hash is empty"),
                    )
                }
                if (nearbyUser.username.isBlank()) {
                    return@withContext Result.failure(
                        RecordingError.InvalidUserData("Username is empty"),
                    )
                }

                // Record interaction - captures username snapshot at this moment
                val interaction =
                    interactionRepository.recordInteraction(
                        partnerAnonymousId = nearbyUser.anonymousIdHash,
                        partnerUsernameSnapshot = nearbyUser.username,
                    )

                Result.success(interaction)
            } catch (e: Exception) {
                Result.failure(
                    RecordingError.DatabaseError(e.message ?: "Failed to record interaction"),
                )
            }
        }

    override suspend fun recordInteractions(users: List<NearbyUser>): Result<List<Interaction>> =
        withContext(dispatchers.io) {
            try {
                // Validate input
                if (users.isEmpty()) {
                    return@withContext Result.failure(RecordingError.EmptyUserList)
                }

                // Validate all users
                users.forEach { user ->
                    if (user.anonymousIdHash.isBlank()) {
                        return@withContext Result.failure(
                            RecordingError.InvalidUserData("User ${user.username} has empty anonymous ID hash"),
                        )
                    }
                }

                // Convert to repository format and record batch
                val interactionData =
                    users.map { user ->
                        user.anonymousIdHash to user.username // Pair of (anonymousId, usernameSnapshot)
                    }

                val interactions = interactionRepository.recordBatchInteractions(interactionData)
                Result.success(interactions)
            } catch (e: Exception) {
                Result.failure(
                    RecordingError.DatabaseError(e.message ?: "Failed to record batch interactions"),
                )
            }
        }

    override suspend fun retryFailedRecordings(): Result<RetryResult> =
        withContext(dispatchers.io) {
            try {
                // Get all unsynced interactions
                val unsyncedInteractions = interactionRepository.getUnsyncedInteractions()

                if (unsyncedInteractions.isEmpty()) {
                    return@withContext Result.success(
                        RetryResult(successCount = 0, failureCount = 0, failedIds = emptyList()),
                    )
                }

                // Attempt to sync
                interactionRepository.syncToCloud()

                // Check which ones are still unsynced after retry
                val stillUnsynced = interactionRepository.getUnsyncedInteractions()
                val stillUnsyncedIds = stillUnsynced.map { it.id }.toSet()

                val successCount = unsyncedInteractions.count { it.id !in stillUnsyncedIds }
                val failureCount = stillUnsynced.size

                Result.success(
                    RetryResult(
                        successCount = successCount,
                        failureCount = failureCount,
                        failedIds = stillUnsynced.map { it.id },
                    ),
                )
            } catch (e: Exception) {
                // If retry completely fails, report all as failed
                val unsynced =
                    try {
                        interactionRepository.getUnsyncedInteractions()
                    } catch (_: Exception) {
                        emptyList()
                    }

                Result.success(
                    RetryResult(
                        successCount = 0,
                        failureCount = unsynced.size,
                        failedIds = unsynced.map { it.id },
                    ),
                )
            }
        }

    override suspend fun getPendingRetryCount(): Int =
        withContext(dispatchers.io) {
            try {
                interactionRepository.getUnsyncedInteractions().size
            } catch (e: Exception) {
                0
            }
        }

    override suspend fun hasPendingRetries(): Boolean =
        withContext(dispatchers.io) {
            try {
                interactionRepository.getUnsyncedInteractions().isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }
}
