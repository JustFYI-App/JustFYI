package app.justfyi.di

import app.justfyi.domain.repository.InteractionRepository
import app.justfyi.domain.usecase.InteractionHistoryUseCase
import app.justfyi.domain.usecase.InteractionHistoryUseCaseImpl
import app.justfyi.domain.usecase.RecordInteractionUseCase
import app.justfyi.domain.usecase.RecordInteractionUseCaseImpl
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing interaction-related use cases for iOS.
 * All use cases are scoped to DataScope as they depend on data layer services.
 *
 * This module provides:
 * - RecordInteractionUseCase: Recording single and batch interactions
 * - InteractionHistoryUseCase: Accessing and managing interaction history
 *
 * The implementations are iOS-specific but functionally identical to Android
 * since they only depend on InteractionRepository (which uses FirebaseProvider).
 *
 * Scope: DataScope
 * - Interaction use cases depend on InteractionRepository (from DataScope)
 * - Scoped with data layer for consistent dependency resolution
 *
 * Dependencies:
 * - InteractionRepository from IosRepositoryModule (DataScope)
 */
@ContributesTo(DataScope::class)
interface IosInteractionModule {
    companion object {
        /**
         * Provides RecordInteractionUseCase implementation for iOS.
         * Handles single and batch interaction recording with:
         * - Username snapshot capture at recording time
         * - Local-first write with background sync
         * - Retry mechanism for failed cloud syncs
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideRecordInteractionUseCase(
            interactionRepository: InteractionRepository,
            dispatchers: AppCoroutineDispatchers,
        ): RecordInteractionUseCase = RecordInteractionUseCaseImpl(interactionRepository, dispatchers)

        /**
         * Provides InteractionHistoryUseCase implementation for iOS.
         * Handles interaction history access with:
         * - Reactive Flow for UI updates
         * - Date window queries for exposure reporting
         * - 120-day retention cleanup
         * - GDPR-compliant deletion
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideInteractionHistoryUseCase(
            interactionRepository: InteractionRepository,
            dispatchers: AppCoroutineDispatchers,
        ): InteractionHistoryUseCase = InteractionHistoryUseCaseImpl(interactionRepository, dispatchers)
    }
}
