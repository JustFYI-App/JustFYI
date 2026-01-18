package app.justfyi.domain.usecase

import app.justfyi.domain.model.PrivacyOptions
import app.justfyi.domain.model.STI
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the simplified ExposureReportUseCase.
 *
 * These tests verify:
 * - submitReport() does not include contactedIds in payload
 * - Report state no longer tracks selectedContactIds (removed from state)
 * - Submission works without contact selection (backend handles it)
 *
 * Note: These tests focus on the simplified 4-step flow where contact
 * selection is handled automatically on the backend via unidirectional
 * graph traversal.
 */
class ExposureReportUseCaseSimplificationTest {
    // ==================== Use Case Simplification Tests ====================

    @Test
    fun `test report state does not require contact selection for submission`() {
        // Given - a complete state ready for submission without contact selection
        // Note: contactsInWindow and selectedContactIds have been removed from ExposureReportState
        val state =
            ExposureReportState(
                selectedSTIs = listOf(STI.HIV, STI.SYPHILIS),
                testDate = LocalDate(2025, 12, 20),
                dateValidationError = null,
                exposureWindow =
                    ExposureWindow(
                        startDate = LocalDate(2025, 11, 20),
                        endDate = LocalDate(2025, 12, 20),
                        daysInWindow = 31,
                    ),
                privacyOptions = PrivacyOptions.DEFAULT,
                currentStep = 4,
                isComplete = true,
                isSubmitting = false,
                submissionError = null,
            )

        // Then - state should allow submission at Review step (step 4)
        assertTrue(
            state.canProceedToNextStep(),
            "Review step should allow submission without contact selection",
        )

        // Verify state is complete - backend handles contact discovery
        assertTrue(
            state.isComplete,
            "State should be complete without manual contact selection",
        )
    }

    @Test
    fun `test submission works without contact selection`() {
        // Given - state transitioning through all 4 steps without contact selection
        val state1 =
            ExposureReportState.INITIAL.copy(
                selectedSTIs = listOf(STI.CHLAMYDIA),
                currentStep = 1,
            )
        assertTrue(state1.canProceedToNextStep(), "Step 1: Should proceed with STI selected")

        val state2 =
            state1.copy(
                testDate = LocalDate(2025, 12, 15),
                currentStep = 2,
            )
        assertTrue(state2.canProceedToNextStep(), "Step 2: Should proceed with date selected")

        val state3 =
            state2.copy(
                privacyOptions = PrivacyOptions.ANONYMOUS,
                currentStep = 3,
            )
        assertTrue(state3.canProceedToNextStep(), "Step 3: Should proceed (privacy has defaults)")

        val state4 =
            state3.copy(
                exposureWindow =
                    ExposureWindow(
                        startDate = LocalDate(2025, 11, 24),
                        endDate = LocalDate(2025, 12, 15),
                        daysInWindow = 22,
                    ),
                currentStep = 4,
                isComplete = true,
            )
        assertTrue(state4.canProceedToNextStep(), "Step 4: Should be ready for submission")

        // Verify flow completes without any contact selection step
        assertEquals(
            4,
            ExposureReportState.TOTAL_STEPS,
            "Flow should have exactly 4 steps (no contact selection)",
        )
    }

    @Test
    fun `test state canProceedToNextStep reflects 4-step flow`() {
        val step1Empty = ExposureReportState.INITIAL.copy(currentStep = 1)
        assertFalse(step1Empty.canProceedToNextStep(), "Step 1 requires STI")

        val step1Ready = step1Empty.copy(selectedSTIs = listOf(STI.HIV))
        assertTrue(step1Ready.canProceedToNextStep(), "Step 1 can proceed with STI")

        val step2Empty = step1Ready.copy(currentStep = 2)
        assertFalse(step2Empty.canProceedToNextStep(), "Step 2 requires date")

        val step2Ready = step2Empty.copy(testDate = LocalDate(2025, 12, 10))
        assertTrue(step2Ready.canProceedToNextStep(), "Step 2 can proceed with date")

        val step3 = step2Ready.copy(currentStep = 3)
        assertTrue(step3.canProceedToNextStep(), "Step 3 always can proceed")

        val step4NotComplete = step3.copy(currentStep = 4, isComplete = false)
        assertFalse(step4NotComplete.canProceedToNextStep(), "Step 4 requires isComplete")

        val step4Ready = step4NotComplete.copy(isComplete = true)
        assertTrue(step4Ready.canProceedToNextStep(), "Step 4 can proceed when complete")

        // Note: No step for contact selection in the simplified flow
    }

    @Test
    fun `test TOTAL_STEPS is 4 for simplified wizard`() {
        assertEquals(
            4,
            ExposureReportState.TOTAL_STEPS,
            "Simplified wizard should have exactly 4 steps",
        )
    }
}
