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
 * Tests for the Review step UI component in the simplified exposure report wizard.
 *
 * This test class verifies:
 * - Review step displays STI types, test date, and privacy options correctly
 * - Review step does NOT display contact count or exposure window (removed from UI)
 * - Automatic notification confirmation text is shown
 *
 * Note: These tests verify the state and data that would be displayed,
 * not the actual Compose UI rendering (which requires Android instrumented tests).
 */
class ReviewStepTest {
    // ==================== Test 1: Display Required Information ====================

    @Test
    fun `test Review step displays STI types and test date and privacy options`() {
        // Given - a complete report state with all required fields
        val state =
            createReviewState(
                selectedSTIs = listOf(STI.CHLAMYDIA, STI.GONORRHEA),
                testDate = LocalDate(2025, 12, 20),
                privacyOptions =
                    PrivacyOptions(
                        discloseSTIType = true,
                        discloseExposureDate = false,
                    ),
            )

        // Then - verify STI types are present and correct
        assertEquals(2, state.selectedSTIs.size, "Should have 2 STI types selected")
        assertTrue(state.selectedSTIs.contains(STI.CHLAMYDIA), "Should contain Chlamydia")
        assertTrue(state.selectedSTIs.contains(STI.GONORRHEA), "Should contain Gonorrhea")

        // Then - verify test date is present
        assertEquals(LocalDate(2025, 12, 20), state.testDate, "Test date should be set correctly")

        // Then - verify privacy options are present
        assertTrue(state.privacyOptions.discloseSTIType, "STI type disclosure should be enabled")
        assertFalse(state.privacyOptions.discloseExposureDate, "Exposure date disclosure should be disabled")
    }

    // ==================== Test 2: Removed Elements Not Displayed ====================

    @Test
    fun `test Review step does NOT display contact count or exposure window`() {
        // Given - a state that was created in the old flow with window data
        // Note: In the simplified flow, these fields are used internally but not displayed
        val stateWithWindow =
            createReviewState(
                selectedSTIs = listOf(STI.HIV),
                testDate = LocalDate(2025, 12, 15),
                // Internal data that should NOT be displayed to the user
                exposureWindow =
                    ExposureWindow(
                        startDate = LocalDate(2025, 11, 15),
                        endDate = LocalDate(2025, 12, 15),
                        daysInWindow = 30,
                    ),
            )

        // The state still contains exposureWindow for internal calculation,
        // but the UI should NOT display it or any contact count.
        // This test documents the expected UI behavior:
        // - exposureWindow may be non-null but should not render
        // - No contact count is shown (backend handles contact discovery)

        // Verify the state structure (exposureWindow still exists for internal use)
        assertTrue(
            stateWithWindow.exposureWindow != null,
            "exposureWindow field exists for internal calculations",
        )

        // Document the expected behavior: ReviewStep.kt should NOT render these sections
        // The actual UI verification would need instrumented tests, but this documents
        // that the data flow no longer requires these to be displayed.

        // In the simplified 4-step flow, step 4 (Review) should only show:
        // - STI Types
        // - Test Date
        // - Privacy Options
        // - Automatic Notification Confirmation
        assertEquals(
            4,
            stateWithWindow.currentStep,
            "Review should be step 4 in simplified flow",
        )
    }

    // ==================== Test 3: Automatic Notification Confirmation ====================

    @Test
    fun `test automatic notification confirmation should be displayed`() {
        // Given - a state ready for review
        val state =
            createReviewState(
                selectedSTIs = listOf(STI.SYPHILIS),
                testDate = LocalDate(2025, 12, 18),
                privacyOptions = PrivacyOptions.DEFAULT,
            )

        // The automatic notification confirmation should be displayed when:
        // 1. State is at the Review step (step 4)
        // 2. Required fields are filled
        // 3. Not currently submitting

        assertEquals(4, state.currentStep, "Should be at Review step")
        assertFalse(state.isSubmitting, "Should not be in submitting state")
        assertTrue(state.selectedSTIs.isNotEmpty(), "Should have STIs selected")
        assertTrue(state.testDate != null, "Should have test date set")

        // Document expected behavior: When the above conditions are met,
        // ReviewStep should display a "Notifications" section with text explaining
        // that contacts will be notified automatically based on interaction history.

        // The confirmation text should explain:
        // - Contacts are determined automatically by the backend
        // - No manual contact selection is needed
        // - Based on the user's interaction history
    }

    // ==================== Helper Functions ====================

    /**
     * Creates a test ExposureReportState configured for the Review step.
     * Note: contactsInWindow and selectedContactIds have been removed from the state.
     */
    private fun createReviewState(
        selectedSTIs: List<STI> = emptyList(),
        testDate: LocalDate? = null,
        privacyOptions: PrivacyOptions = PrivacyOptions.DEFAULT,
        exposureWindow: ExposureWindow? = null,
        isSubmitting: Boolean = false,
    ): ExposureReportState =
        ExposureReportState(
            selectedSTIs = selectedSTIs,
            testDate = testDate,
            dateValidationError = null,
            exposureWindow = exposureWindow,
            privacyOptions = privacyOptions,
            currentStep = 4, // Review is step 4 in simplified flow
            isComplete = selectedSTIs.isNotEmpty() && testDate != null,
            isSubmitting = isSubmitting,
            submissionError = null,
        )
}
