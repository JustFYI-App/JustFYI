package app.justfyi.domain.usecase

import app.justfyi.domain.model.ExposureReport
import app.justfyi.domain.model.PrivacyOptions
import app.justfyi.domain.model.STI
import app.justfyi.domain.repository.ExposureReportRepository
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * Multiplatform implementation of ExposureReportUseCase.
 * Orchestrates the simplified 4-step exposure report wizard flow.
 *
 * Key features:
 * - State management for multi-step wizard
 * - Incubation period calculation for exposure window (internal use)
 * - Submission to Firebase via ExposureReportRepository
 *
 * Note: Contact selection has been removed from this implementation.
 * The backend now automatically determines contacts using unidirectional
 * graph traversal with 10-hop chain propagation.
 */
@Inject
class ExposureReportUseCaseImpl(
    private val exposureReportRepository: ExposureReportRepository,
    private val interactionHistoryUseCase: InteractionHistoryUseCase,
    private val incubationCalculator: IncubationCalculator,
    private val dispatchers: AppCoroutineDispatchers,
) : ExposureReportUseCase {
    private val _reportState = MutableStateFlow(ExposureReportState.INITIAL)

    override val reportState: Flow<ExposureReportState> = _reportState.asStateFlow()

    override suspend fun createReport(): ExposureReportState {
        _reportState.value = ExposureReportState.INITIAL
        return _reportState.value
    }

    override suspend fun selectSTIs(types: List<STI>): ExposureReportState {
        require(types.isNotEmpty()) { "At least one STI type must be selected" }

        _reportState.update { current ->
            current.copy(
                selectedSTIs = types,
                // Reset downstream state when STIs change (affects incubation calculation)
                exposureWindow = null,
            )
        }
        return _reportState.value
    }

    override suspend fun setTestDate(date: LocalDate): ExposureReportState {
        val validationResult = incubationCalculator.validateTestDate(date)

        _reportState.update { current ->
            current.copy(
                testDate = date,
                dateValidationError = validationResult.errorType,
                // Reset downstream state when date changes
                exposureWindow = null,
            )
        }
        return _reportState.value
    }

    override suspend fun calculateExposureWindow(): ExposureReportState =
        withContext(dispatchers.io) {
            val currentState = _reportState.value

            check(currentState.selectedSTIs.isNotEmpty()) {
                "STIs must be selected before calculating exposure window"
            }
            check(currentState.testDate != null) {
                "Test date must be set before calculating exposure window"
            }
            check(currentState.dateValidationError == null) {
                "Test date has validation errors"
            }

            val window =
                incubationCalculator.calculateExposureWindow(
                    selectedSTIs = currentState.selectedSTIs,
                    testDate = currentState.testDate,
                )

            _reportState.update { current ->
                current.copy(exposureWindow = window)
            }
            _reportState.value
        }

    override suspend fun setPrivacyOptions(options: PrivacyOptions): ExposureReportState {
        _reportState.update { current ->
            current.copy(privacyOptions = options)
        }
        return _reportState.value
    }

    override suspend fun submitReport(): Result<ExposureReport> =
        withContext(dispatchers.io) {
            val currentState = _reportState.value

            // Validate all required fields
            check(currentState.selectedSTIs.isNotEmpty()) {
                "STIs must be selected"
            }
            check(currentState.testDate != null) {
                "Test date must be set"
            }
            check(currentState.dateValidationError == null) {
                "Test date has validation errors"
            }
            // Note: exposureWindow is no longer required - backend handles window calculation

            _reportState.update { it.copy(isSubmitting = true, submissionError = null) }

            try {
                // Convert testDate to milliseconds
                val testDateMillis =
                    currentState.testDate
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()

                // Submit to repository - no contactedIds since backend handles contact discovery
                val report =
                    exposureReportRepository.submitReport(
                        stiTypes = STI.toJsonArray(currentState.selectedSTIs),
                        testDate = testDateMillis,
                        privacyLevel = currentState.privacyOptions.toPrivacyLevel(),
                    )

                _reportState.update {
                    it.copy(
                        isSubmitting = false,
                        isComplete = true,
                    )
                }

                Result.success(report)
            } catch (e: Exception) {
                _reportState.update {
                    it.copy(
                        isSubmitting = false,
                        submissionError = e.message ?: "Unknown error occurred",
                    )
                }
                Result.failure(e)
            }
        }

    override suspend fun cancelReport() {
        _reportState.value = ExposureReportState.INITIAL
    }

    override fun getCurrentState(): ExposureReportState = _reportState.value
}
