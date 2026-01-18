package app.justfyi.di

import app.justfyi.domain.repository.ExposureReportRepository
import app.justfyi.domain.usecase.ExposureReportUseCase
import app.justfyi.domain.usecase.ExposureReportUseCaseImpl
import app.justfyi.domain.usecase.IncubationCalculator
import app.justfyi.domain.usecase.IncubationCalculatorImpl
import app.justfyi.domain.usecase.InteractionHistoryUseCase
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing exposure reporting components.
 * All components are scoped to DataScope as they depend on data layer services.
 *
 * This module provides:
 * - IncubationCalculator: Calculates exposure windows based on STI incubation periods
 * - ExposureReportUseCase: Orchestrates the multi-step exposure reporting flow
 *
 * Scope: DataScope
 * - Exposure reporting depends on ExposureReportRepository and InteractionHistoryUseCase
 * - Both dependencies are from DataScope, ensuring consistent scope resolution
 *
 * Dependencies:
 * - ExposureReportRepository from RepositoryModule (DataScope)
 * - InteractionHistoryUseCase from InteractionModule (DataScope)
 */
@ContributesTo(DataScope::class)
interface ExposureReportModule {
    companion object {
        /**
         * Provides IncubationCalculator implementation.
         * Handles exposure window calculations based on:
         * - STI-specific incubation periods
         * - Maximum period selection for multi-STI reports
         * - 120-day retention period constraints
         * - Test date validation
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideIncubationCalculator(): IncubationCalculator = IncubationCalculatorImpl()

        /**
         * Provides ExposureReportUseCase implementation.
         * Orchestrates the 6-step exposure reporting wizard:
         * 1. STI selection (multi-select)
         * 2. Test date picker with validation
         * 3. Exposure window calculation and display
         * 4. Contact selection from window
         * 5. Privacy options configuration
         * 6. Review and submission
         *
         * Integrates with:
         * - InteractionHistoryUseCase for querying contacts in window
         * - ExposureReportRepository for persistence and Firebase submission
         * - IncubationCalculator for window calculations
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideExposureReportUseCase(
            exposureReportRepository: ExposureReportRepository,
            interactionHistoryUseCase: InteractionHistoryUseCase,
            incubationCalculator: IncubationCalculator,
            dispatchers: AppCoroutineDispatchers,
        ): ExposureReportUseCase =
            ExposureReportUseCaseImpl(
                exposureReportRepository = exposureReportRepository,
                interactionHistoryUseCase = interactionHistoryUseCase,
                incubationCalculator = incubationCalculator,
                dispatchers = dispatchers,
            )
    }
}
