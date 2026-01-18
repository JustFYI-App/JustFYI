package app.justfyi.domain.usecase

import app.justfyi.domain.model.ExposureReport
import app.justfyi.domain.model.PrivacyOptions
import app.justfyi.domain.model.STI
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Use case for managing the exposure report flow.
 *
 * This use case orchestrates the simplified 4-step wizard flow for reporting
 * a positive STI test:
 * 1. STI selection (multi-select)
 * 2. Test date picker with validation
 * 3. Privacy options for notification detail level
 * 4. Review and confirm summary before sending
 *
 * Note: Contact selection and exposure window display have been removed.
 * The backend now automatically determines contacts using unidirectional
 * graph traversal with 10-hop chain propagation.
 *
 * Key responsibilities:
 * - Manage report state throughout the wizard flow
 * - Validate inputs at each step
 * - Submit final report to Cloud Function for processing
 */
interface ExposureReportUseCase {
    /**
     * Observable state of the current report being created.
     */
    val reportState: Flow<ExposureReportState>

    /**
     * Creates a new exposure report flow.
     * Resets any existing in-progress report and initializes fresh state.
     *
     * @return Initial report state
     */
    suspend fun createReport(): ExposureReportState

    /**
     * Sets the selected STI types for the report.
     * Multi-select is supported.
     *
     * @param types List of STI types to report
     * @return Updated report state
     * @throws IllegalArgumentException if types is empty
     */
    suspend fun selectSTIs(types: List<STI>): ExposureReportState

    /**
     * Sets the test date for the report.
     * Validates that the date is not in the future and within 120 days.
     *
     * @param date The date of the positive test
     * @return Updated report state with validation result
     */
    suspend fun setTestDate(date: LocalDate): ExposureReportState

    /**
     * Calculates the exposure window based on selected STIs and test date.
     * Uses the maximum incubation period when multiple STIs are selected.
     *
     * Note: This is still used for internal validation but no longer displayed
     * to the user. The backend calculates the window server-side.
     *
     * @return Updated report state with exposure window
     * @throws IllegalStateException if STIs or test date not set
     */
    suspend fun calculateExposureWindow(): ExposureReportState

    /**
     * Sets the privacy options for the report notifications.
     *
     * @param options Privacy settings for disclosure level
     * @return Updated report state
     */
    suspend fun setPrivacyOptions(options: PrivacyOptions): ExposureReportState

    /**
     * Submits the final report to Firebase.
     * Triggers the Cloud Function for notification processing.
     * The backend automatically determines contacts to notify using
     * unidirectional graph traversal.
     *
     * Note: contactedIds are no longer sent to the backend. The backend
     * discovers contacts by querying interactions where partnerAnonymousId
     * matches the reporter's ID.
     *
     * @return Result containing the submitted report or error
     * @throws IllegalStateException if required fields not set
     */
    suspend fun submitReport(): Result<ExposureReport>

    /**
     * Cancels the current report flow and clears state.
     */
    suspend fun cancelReport()

    /**
     * Gets the current report state synchronously.
     * Useful for validation checks.
     */
    fun getCurrentState(): ExposureReportState
}

/**
 * State of an in-progress exposure report.
 * Tracks all data collected through the simplified 4-step wizard flow.
 *
 * @property selectedSTIs List of selected STI types (Step 1)
 * @property testDate Date of positive test (Step 2)
 * @property dateValidationError Validation error for test date, if any
 * @property exposureWindow Calculated exposure window (internal use, not displayed)
 * @property privacyOptions Privacy settings for notifications (Step 3)
 * @property currentStep Current wizard step (1-4)
 * @property isComplete Whether all required data is collected
 * @property isSubmitting Whether submission is in progress
 * @property submissionError Error from submission attempt, if any
 */
data class ExposureReportState(
    val selectedSTIs: List<STI> = emptyList(),
    val testDate: LocalDate? = null,
    val dateValidationError: DateValidationError? = null,
    val exposureWindow: ExposureWindow? = null,
    val privacyOptions: PrivacyOptions = PrivacyOptions.DEFAULT,
    val currentStep: Int = 1,
    val isComplete: Boolean = false,
    val isSubmitting: Boolean = false,
    val submissionError: String? = null,
) {
    companion object {
        /**
         * Initial state for a new report.
         */
        val INITIAL = ExposureReportState()

        /**
         * Total number of steps in the wizard.
         *
         * Simplified from 6 to 4 steps:
         * - Step 1: STI Selection
         * - Step 2: Test Date
         * - Step 3: Privacy Options (previously step 5)
         * - Step 4: Review (previously step 6)
         *
         * Removed steps:
         * - Old Step 3: Exposure Window (now calculated server-side)
         * - Old Step 4: Contact Selection (now automatic server-side)
         */
        const val TOTAL_STEPS = 4
    }

    /**
     * Checks if the report can proceed to the next step.
     * Validates that required data for the current step is set.
     *
     * Simplified step validation:
     * - Step 1: At least one STI must be selected
     * - Step 2: Valid test date must be set
     * - Step 3: Privacy options have defaults (always can proceed)
     * - Step 4: Report must be complete for final submission
     */
    fun canProceedToNextStep(): Boolean =
        when (currentStep) {
            1 -> selectedSTIs.isNotEmpty()
            2 -> testDate != null && dateValidationError == null
            3 -> true // Privacy options have defaults
            4 -> isComplete
            else -> false
        }

    /**
     * Returns the next step number, or null if at the last step.
     */
    fun nextStep(): Int? = if (currentStep < TOTAL_STEPS) currentStep + 1 else null

    /**
     * Returns the previous step number, or null if at the first step.
     */
    fun previousStep(): Int? = if (currentStep > 1) currentStep - 1 else null
}
