package app.justfyi.domain.usecase

import app.justfyi.domain.model.Interaction
import app.justfyi.domain.repository.InteractionRepository
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Multiplatform implementation of InteractionHistoryUseCase.
 * Provides access to interaction history with filtering and cleanup.
 *
 * Key features:
 * - Reactive history updates via Flow
 * - Date range queries for exposure window calculation
 * - 120-day data retention cleanup
 * - GDPR-compliant deletion
 */
@Inject
class InteractionHistoryUseCaseImpl(
    private val interactionRepository: InteractionRepository,
    private val dispatchers: AppCoroutineDispatchers,
) : InteractionHistoryUseCase {
    override fun getInteractionHistory(): Flow<List<Interaction>> =
        interactionRepository
            .getInteractions()
            .map { interactions ->
                // Ensure newest first ordering
                interactions.sortedByDescending { it.recordedAt }
            }

    override suspend fun getInteractionsInWindow(
        startDate: Long,
        endDate: Long,
    ): List<Interaction> =
        withContext(dispatchers.io) {
            require(startDate <= endDate) {
                "Start date ($startDate) must be before or equal to end date ($endDate)"
            }
            interactionRepository
                .getInteractionsInDateRange(startDate, endDate)
                .sortedByDescending { it.recordedAt }
        }

    override suspend fun getInteractionsInLastDays(days: Int): List<Interaction> =
        withContext(dispatchers.io) {
            require(days > 0) { "Days must be positive" }
            interactionRepository
                .getInteractionsInLastDays(days)
                .sortedByDescending { it.recordedAt }
        }

    override suspend fun deleteOldInteractions(): Int =
        withContext(dispatchers.io) {
            interactionRepository.deleteOldInteractions()
        }

    override suspend fun getInteractionCount(): Long =
        withContext(dispatchers.io) {
            val currentInteractions =
                interactionRepository.getInteractionsInLastDays(
                    InteractionHistoryUseCase.RETENTION_DAYS,
                )
            currentInteractions.size.toLong()
        }

    override suspend fun hasInteractions(): Boolean =
        withContext(dispatchers.io) {
            interactionRepository
                .getInteractionsInLastDays(
                    InteractionHistoryUseCase.RETENTION_DAYS,
                ).isNotEmpty()
        }

    override suspend fun getInteractionById(interactionId: String): Interaction? =
        withContext(dispatchers.io) {
            // Query the full list and filter - repository doesn't expose getById
            // This is acceptable since interactions list is bounded by retention period
            interactionRepository
                .getInteractionsInLastDays(
                    InteractionHistoryUseCase.RETENTION_DAYS,
                ).find { it.id == interactionId }
        }

    override suspend fun deleteAllInteractions(): Unit =
        withContext(dispatchers.io) {
            interactionRepository.deleteAllInteractions()
        }
}
