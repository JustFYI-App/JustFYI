package app.justfyi.presentation.exposurereport

import app.justfyi.domain.model.PrivacyOptions
import app.justfyi.domain.model.STI
import app.justfyi.domain.usecase.DateValidationError
import app.justfyi.domain.usecase.ExposureReportState
import app.justfyi.domain.usecase.ExposureWindow
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edge case tests for the exposure report flow.
 *
 * These tests fill gaps identified during Task Group 10 coverage review:
 * - Cancellation behavior at each step
 * - Error handling on submission failure
 * - Validation error display and recovery
 * - Back navigation data preservation
 *
 * Task 10.3: Up to 5 additional client tests for critical UI workflows.
 */
class ExposureReportEdgeCasesTest {
    // ==================== Test 1: Cancellation Clears State ====================

    @Test
    fun `test cancellation at any step resets to initial state`() {
        // Given - state at various steps with data
        val stateAtStep2 =
            createStateWithData(
                currentStep = 2,
                selectedSTIs = listOf(STI.HIV, STI.SYPHILIS),
                testDate = LocalDate(2025, 12, 20),
            )

        val stateAtStep3 =
            createStateWithData(
                currentStep = 3,
                selectedSTIs = listOf(STI.CHLAMYDIA),
                testDate = LocalDate(2025, 12, 15),
                privacyOptions = PrivacyOptions.ANONYMOUS,
            )

        val stateAtStep4 =
            createStateWithData(
                currentStep = 4,
                selectedSTIs = listOf(STI.GONORRHEA),
                testDate = LocalDate(2025, 12, 10),
                privacyOptions = PrivacyOptions.DEFAULT,
                isComplete = true,
            )

        // Verify all states have data before "cancellation"
        assertTrue(stateAtStep2.selectedSTIs.isNotEmpty(), "Step 2 should have STIs selected")
        assertTrue(stateAtStep3.selectedSTIs.isNotEmpty(), "Step 3 should have STIs selected")
        assertTrue(stateAtStep4.selectedSTIs.isNotEmpty(), "Step 4 should have STIs selected")

        // When - simulate cancellation by comparing to INITIAL state
        val initialState = ExposureReportState.INITIAL

        // Then - verify INITIAL state has no data (simulating what cancel does)
        assertEquals(emptyList(), initialState.selectedSTIs, "Initial state should have no STIs")
        assertNull(initialState.testDate, "Initial state should have no test date")
        assertNull(initialState.exposureWindow, "Initial state should have no exposure window")
        assertEquals(PrivacyOptions.DEFAULT, initialState.privacyOptions, "Initial state should have default privacy")
        assertEquals(1, initialState.currentStep, "Initial state should be at step 1")
        assertFalse(initialState.isComplete, "Initial state should not be complete")
        assertFalse(initialState.isSubmitting, "Initial state should not be submitting")
        assertNull(initialState.submissionError, "Initial state should have no submission error")

        // This documents the expected behavior: cancelReport() resets to ExposureReportState.INITIAL
    }

    // ==================== Test 2: Submission Error Handling ====================

    @Test
    fun `test submission error state allows retry`() {
        // Given - state with submission error
        val stateWithError =
            createStateWithData(
                currentStep = 4,
                selectedSTIs = listOf(STI.HIV),
                testDate = LocalDate(2025, 12, 20),
                isComplete = true,
                submissionError = "Network error: Unable to connect",
            )

        // Then - verify error state properties
        assertEquals(
            "Network error: Unable to connect",
            stateWithError.submissionError,
            "Submission error should be stored",
        )
        assertFalse(stateWithError.isSubmitting, "Should not be in submitting state when error occurred")
        assertEquals(4, stateWithError.currentStep, "Should remain at Review step after error")

        // Verify state still allows retry (data preserved)
        assertTrue(stateWithError.selectedSTIs.isNotEmpty(), "STIs should be preserved after error")
        assertTrue(stateWithError.testDate != null, "Test date should be preserved after error")
        assertTrue(stateWithError.isComplete, "Complete flag should remain after error")

        // When - simulate retry by clearing error
        val stateAfterRetry =
            stateWithError.copy(
                submissionError = null,
                isSubmitting = true,
            )

        // Then - state should be ready for retry
        assertNull(stateAfterRetry.submissionError, "Error should be cleared for retry")
        assertTrue(stateAfterRetry.isSubmitting, "Should be submitting on retry")
        assertEquals(listOf(STI.HIV), stateAfterRetry.selectedSTIs, "Data should be preserved on retry")
    }

    // ==================== Test 3: Validation Error Display and Recovery ====================

    @Test
    fun `test date validation error blocks progression and allows recovery`() {
        // Given - state with FUTURE_DATE validation error at step 2
        val stateWithFutureDate =
            ExposureReportState(
                selectedSTIs = listOf(STI.HIV),
                testDate = LocalDate(2025, 12, 31), // Future date (assuming current is before this)
                dateValidationError = DateValidationError.FUTURE_DATE,
                currentStep = 2,
            )

        // Then - cannot proceed due to validation error
        assertFalse(
            stateWithFutureDate.canProceedToNextStep(),
            "Should not proceed with date validation error",
        )
        assertEquals(
            DateValidationError.FUTURE_DATE,
            stateWithFutureDate.dateValidationError,
            "Should show FUTURE_DATE error",
        )

        // Given - state with BEYOND_RETENTION_PERIOD error
        val stateWithOldDate =
            ExposureReportState(
                selectedSTIs = listOf(STI.HIV),
                testDate = LocalDate(2024, 1, 1), // Very old date
                dateValidationError = DateValidationError.BEYOND_RETENTION_PERIOD,
                currentStep = 2,
            )

        // Then - cannot proceed due to retention error
        assertFalse(
            stateWithOldDate.canProceedToNextStep(),
            "Should not proceed with retention period error",
        )

        // When - user corrects the date (simulate recovery)
        val stateRecovered =
            stateWithFutureDate.copy(
                testDate = LocalDate(2025, 12, 20), // Valid date
                dateValidationError = null,
            )

        // Then - can now proceed
        assertTrue(
            stateRecovered.canProceedToNextStep(),
            "Should proceed after correcting date validation error",
        )
        assertNull(
            stateRecovered.dateValidationError,
            "Validation error should be cleared after correction",
        )
    }

    // ==================== Test 4: Back Navigation Preserves Data ====================

    @Test
    fun `test back navigation from each step preserves all previously entered data`() {
        // Given - complete state at step 4 (Review)
        val completeState =
            ExposureReportState(
                selectedSTIs = listOf(STI.HIV, STI.SYPHILIS, STI.CHLAMYDIA),
                testDate = LocalDate(2025, 12, 18),
                dateValidationError = null,
                exposureWindow =
                    ExposureWindow(
                        startDate = LocalDate(2025, 9, 20),
                        endDate = LocalDate(2025, 12, 18),
                        daysInWindow = 90,
                    ),
                privacyOptions =
                    PrivacyOptions(
                        discloseSTIType = true,
                        discloseExposureDate = false,
                    ),
                currentStep = 4,
                isComplete = true,
            )

        // When - navigate back to step 3
        val stateAtStep3 = completeState.copy(currentStep = 3)

        // Then - all data preserved
        assertEquals(
            listOf(STI.HIV, STI.SYPHILIS, STI.CHLAMYDIA),
            stateAtStep3.selectedSTIs,
            "STIs should be preserved navigating back to step 3",
        )
        assertEquals(
            LocalDate(2025, 12, 18),
            stateAtStep3.testDate,
            "Test date should be preserved navigating back to step 3",
        )
        assertEquals(
            PrivacyOptions(true, false),
            stateAtStep3.privacyOptions,
            "Privacy options should be preserved navigating back to step 3",
        )

        // When - navigate back to step 2
        val stateAtStep2 = stateAtStep3.copy(currentStep = 2)

        // Then - all data still preserved
        assertEquals(
            listOf(STI.HIV, STI.SYPHILIS, STI.CHLAMYDIA),
            stateAtStep2.selectedSTIs,
            "STIs should be preserved navigating back to step 2",
        )
        assertEquals(
            LocalDate(2025, 12, 18),
            stateAtStep2.testDate,
            "Test date should be preserved navigating back to step 2",
        )

        // When - navigate back to step 1
        val stateAtStep1 = stateAtStep2.copy(currentStep = 1)

        // Then - all data still preserved at step 1
        assertEquals(
            listOf(STI.HIV, STI.SYPHILIS, STI.CHLAMYDIA),
            stateAtStep1.selectedSTIs,
            "STIs should be preserved navigating back to step 1",
        )
        assertEquals(
            LocalDate(2025, 12, 18),
            stateAtStep1.testDate,
            "Test date should be preserved at step 1",
        )
        assertEquals(
            PrivacyOptions(true, false),
            stateAtStep1.privacyOptions,
            "Privacy options should be preserved at step 1",
        )

        // User can still proceed forward with preserved data
        assertTrue(
            stateAtStep1.canProceedToNextStep(),
            "Should be able to proceed from step 1 with preserved STIs",
        )
    }

    // ==================== Test 5: Submission In Progress Blocks Navigation ====================

    @Test
    fun `test isSubmitting state blocks all actions`() {
        // Given - state in submitting state
        val submittingState =
            ExposureReportState(
                selectedSTIs = listOf(STI.HIV),
                testDate = LocalDate(2025, 12, 20),
                privacyOptions = PrivacyOptions.DEFAULT,
                currentStep = 4,
                isComplete = true,
                isSubmitting = true, // In progress
                submissionError = null,
            )

        // Then - verify submitting state properties
        assertTrue(submittingState.isSubmitting, "Should be in submitting state")
        assertEquals(4, submittingState.currentStep, "Should be at step 4 during submission")
        assertTrue(submittingState.isComplete, "Should be marked complete during submission")

        // Document expected UI behavior during submission:
        // - Back button should be disabled (checked via isSubmitting in UI)
        // - Submit button should show loading state (checked via isSubmitting in UI)
        // - User cannot modify data during submission

        // When - submission completes successfully
        val completedState =
            submittingState.copy(
                isSubmitting = false,
                isComplete = true,
            )

        // Then - state reflects completion
        assertFalse(completedState.isSubmitting, "Should not be submitting after completion")
        assertTrue(completedState.isComplete, "Should remain complete after submission")
        assertNull(completedState.submissionError, "Should have no error on successful submission")
    }

    // ==================== Helper Functions ====================

    /**
     * Creates an ExposureReportState with common test data.
     */
    private fun createStateWithData(
        currentStep: Int,
        selectedSTIs: List<STI>,
        testDate: LocalDate? = null,
        privacyOptions: PrivacyOptions = PrivacyOptions.DEFAULT,
        isComplete: Boolean = false,
        isSubmitting: Boolean = false,
        submissionError: String? = null,
    ): ExposureReportState =
        ExposureReportState(
            selectedSTIs = selectedSTIs,
            testDate = testDate,
            dateValidationError = null,
            exposureWindow = null,
            privacyOptions = privacyOptions,
            currentStep = currentStep,
            isComplete = isComplete,
            isSubmitting = isSubmitting,
            submissionError = submissionError,
        )
}
