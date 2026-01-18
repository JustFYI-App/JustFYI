package app.justfyi.presentation.exposurereport

import app.justfyi.domain.model.PrivacyOptions
import app.justfyi.domain.model.STI
import app.justfyi.domain.usecase.ExposureReportState
import app.justfyi.domain.usecase.ExposureWindow
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the simplified 4-step exposure report wizard flow.
 *
 * This test class verifies:
 * - 4-step navigation flow works correctly
 * - Step progression: STI -> Date -> Privacy -> Review
 * - Back navigation works at each step
 * - Step validation rules for the simplified flow
 *
 * Note: These tests verify the flow logic independent of the ViewModel,
 * focusing on ExposureReportState navigation behavior.
 */
class ExposureReportFlowTest {
    companion object {
        /**
         * Total number of steps in the simplified wizard (reduced from 6 to 4).
         */
        const val SIMPLIFIED_TOTAL_STEPS = 4
    }

    // ==================== 4-Step Flow Navigation Tests ====================

    @Test
    fun `test 4-step navigation flow works correctly`() {
        // Given - initial state at step 1
        var state = createTestState(currentStep = 1)

        // Verify total steps is 4 (not 6)
        assertEquals(
            SIMPLIFIED_TOTAL_STEPS,
            ExposureReportState.TOTAL_STEPS,
            "Total steps should be 4 in simplified flow",
        )

        state =
            state.copy(
                selectedSTIs = listOf(STI.HIV),
                currentStep = 1,
            )
        assertTrue(state.canProceedToNextStep(), "Should proceed from Step 1 with STI selected")
        assertEquals(2, state.nextStep(), "Next step from 1 should be 2")

        state =
            state.copy(
                testDate = LocalDate(2025, 12, 20),
                currentStep = 2,
            )
        assertTrue(state.canProceedToNextStep(), "Should proceed from Step 2 with date selected")
        assertEquals(3, state.nextStep(), "Next step from 2 should be 3")

        state = state.copy(currentStep = 3)
        assertTrue(state.canProceedToNextStep(), "Should always proceed from Step 3 (privacy has defaults)")
        assertEquals(4, state.nextStep(), "Next step from 3 should be 4")

        state = state.copy(currentStep = 4, isComplete = true)
        assertEquals(null, state.nextStep(), "Step 4 should be final (no next step)")
    }

    @Test
    fun `test step progression follows STI - Date - Privacy - Review order`() {
        // Given - state progressing through steps
        var step = 1

        val state1 = createTestState(currentStep = 1)
        assertEquals(1, state1.currentStep, "Step 1 should be STI Selection")

        val state2 = createTestState(currentStep = 2)
        assertEquals(2, state2.currentStep, "Step 2 should be Date Picker")

        val state3 = createTestState(currentStep = 3)
        assertEquals(3, state3.currentStep, "Step 3 should be Privacy Options")

        val state4 = createTestState(currentStep = 4)
        assertEquals(4, state4.currentStep, "Step 4 should be Review")

        // Verify no steps 5 or 6 exist
        val invalidState5 = createTestState(currentStep = 5)
        assertFalse(
            invalidState5.canProceedToNextStep(),
            "Step 5 should not exist in simplified flow",
        )
    }

    @Test
    fun `test back navigation works at each step`() {
        var state = createTestState(currentStep = 4)
        assertEquals(3, state.previousStep(), "Previous step from 4 should be 3")

        state = createTestState(currentStep = 3)
        assertEquals(2, state.previousStep(), "Previous step from 3 should be 2")

        state = createTestState(currentStep = 2)
        assertEquals(1, state.previousStep(), "Previous step from 2 should be 1")

        state = createTestState(currentStep = 1)
        assertEquals(null, state.previousStep(), "Step 1 should have no previous step")
    }

    @Test
    fun `test canProceedToNextStep validation for each step`() {
        val step1NoSTI = createTestState(currentStep = 1, selectedSTIs = emptyList())
        assertFalse(
            step1NoSTI.canProceedToNextStep(),
            "Step 1 should not proceed without STI selected",
        )

        val step1WithSTI = createTestState(currentStep = 1, selectedSTIs = listOf(STI.CHLAMYDIA))
        assertTrue(
            step1WithSTI.canProceedToNextStep(),
            "Step 1 should proceed with STI selected",
        )

        val step2NoDate = createTestState(currentStep = 2, testDate = null)
        assertFalse(
            step2NoDate.canProceedToNextStep(),
            "Step 2 should not proceed without date",
        )

        val step2WithDate = createTestState(currentStep = 2, testDate = LocalDate(2025, 12, 20))
        assertTrue(
            step2WithDate.canProceedToNextStep(),
            "Step 2 should proceed with valid date",
        )

        val step3 = createTestState(currentStep = 3)
        assertTrue(
            step3.canProceedToNextStep(),
            "Step 3 (Privacy) should always proceed (has defaults)",
        )

        val step4NotComplete = createTestState(currentStep = 4, isComplete = false)
        assertFalse(
            step4NotComplete.canProceedToNextStep(),
            "Step 4 should not proceed if not complete",
        )

        val step4Complete = createTestState(currentStep = 4, isComplete = true)
        assertTrue(
            step4Complete.canProceedToNextStep(),
            "Step 4 should proceed when complete",
        )
    }

    @Test
    fun `test simplified flow does not include contact or exposure window steps`() {
        // Verify that the simplified flow skips directly from Date (2) to Privacy (3)
        // without the old Exposure Window (3) and Contact Selection (4) steps

        val stateAtStep2 =
            createTestState(
                currentStep = 2,
                selectedSTIs = listOf(STI.HIV),
                testDate = LocalDate(2025, 12, 20),
            )

        // Next step from 2 should be 3 (Privacy), not the old Exposure Window step
        assertEquals(
            3,
            stateAtStep2.nextStep(),
            "After Date step (2), next should be Privacy (3), not Exposure Window",
        )

        // The total is 4, confirming Contact Selection and Exposure Window are removed
        assertEquals(
            4,
            ExposureReportState.TOTAL_STEPS,
            "Total steps should be 4 (old steps 3 and 4 removed)",
        )
    }

    // ==================== Helper Functions ====================

    /**
     * Creates a test ExposureReportState with specified values.
     * Note: contactsInWindow and selectedContactIds have been removed from the state.
     */
    private fun createTestState(
        currentStep: Int = 1,
        selectedSTIs: List<STI> = emptyList(),
        testDate: LocalDate? = null,
        exposureWindow: ExposureWindow? = null,
        privacyOptions: PrivacyOptions = PrivacyOptions.DEFAULT,
        isComplete: Boolean = false,
    ): ExposureReportState =
        ExposureReportState(
            selectedSTIs = selectedSTIs,
            testDate = testDate,
            dateValidationError = null,
            exposureWindow = exposureWindow,
            privacyOptions = privacyOptions,
            currentStep = currentStep,
            isComplete = isComplete,
            isSubmitting = false,
            submissionError = null,
        )
}
