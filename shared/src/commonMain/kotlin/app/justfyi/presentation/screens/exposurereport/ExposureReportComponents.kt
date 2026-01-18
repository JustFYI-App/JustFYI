package app.justfyi.presentation.screens.exposurereport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.domain.model.STI
import app.justfyi.presentation.components.JustFyiButton
import app.justfyi.presentation.components.JustFyiButtonVariant
import app.justfyi.util.DateTimeFormatter
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_continue_next
import justfyi.shared.generated.resources.cd_go_back
import justfyi.shared.generated.resources.cd_report_progress
import justfyi.shared.generated.resources.cd_submit_report
import justfyi.shared.generated.resources.nav_back
import justfyi.shared.generated.resources.nav_next
import justfyi.shared.generated.resources.report_step1_name
import justfyi.shared.generated.resources.report_step2_name
import justfyi.shared.generated.resources.report_step5_name
import justfyi.shared.generated.resources.report_step6_name
import justfyi.shared.generated.resources.report_step_count
import justfyi.shared.generated.resources.report_submit
import justfyi.shared.generated.resources.sti_chlamydia
import justfyi.shared.generated.resources.sti_gonorrhea
import justfyi.shared.generated.resources.sti_herpes
import justfyi.shared.generated.resources.sti_hiv
import justfyi.shared.generated.resources.sti_hpv
import justfyi.shared.generated.resources.sti_other
import justfyi.shared.generated.resources.sti_syphilis
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.Instant

/**
 * Total number of steps in the exposure report flow.
 *
 * Simplified from 6 to 4 steps as part of the exposure reporting flow fix:
 * - Step 1: STI Selection
 * - Step 2: Test Date
 * - Step 3: Privacy Options (moved from step 5)
 * - Step 4: Review & Submit (moved from step 6)
 *
 * Removed steps:
 * - Old Step 3: Exposure Window (now calculated server-side)
 * - Old Step 4: Contact Selection (now automatic server-side)
 */
const val EXPOSURE_REPORT_TOTAL_STEPS = 4

/**
 * Progress indicator showing current step in the exposure report flow.
 *
 * Accessibility: Provides a content description announcing current progress
 * including step number, total steps, and step name.
 */
@Composable
fun ExposureReportProgressIndicator(
    currentStep: Int,
    totalSteps: Int = EXPOSURE_REPORT_TOTAL_STEPS,
    modifier: Modifier = Modifier,
) {
    val stepName = getStepName(currentStep)
    val progressDescription = stringResource(Res.string.cd_report_progress, currentStep, totalSteps, stepName)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics {
                    contentDescription = progressDescription
                },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.report_step_count, currentStep, totalSteps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stepName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Navigation buttons for the exposure report flow.
 *
 * Accessibility: Each button has a content description describing its purpose
 * (go back, continue, or submit).
 */
@Composable
fun ExposureReportNavigationButtons(
    currentStep: Int,
    totalSteps: Int = EXPOSURE_REPORT_TOTAL_STEPS,
    canProceed: Boolean,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backDescription = stringResource(Res.string.cd_go_back)
    val nextDescription = stringResource(Res.string.cd_continue_next)
    val submitDescription = stringResource(Res.string.cd_submit_report)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (currentStep > 1) {
            JustFyiButton(
                text = stringResource(Res.string.nav_back),
                onClick = onBack,
                variant = JustFyiButtonVariant.SECONDARY,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = backDescription
                        },
                enabled = !isSubmitting,
            )
        }

        if (currentStep < totalSteps) {
            JustFyiButton(
                text = stringResource(Res.string.nav_next),
                onClick = onNext,
                variant = JustFyiButtonVariant.PRIMARY,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = nextDescription
                        },
                enabled = canProceed && !isSubmitting,
            )
        } else {
            JustFyiButton(
                text = stringResource(Res.string.report_submit),
                onClick = onSubmit,
                variant = JustFyiButtonVariant.PRIMARY,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = submitDescription
                        },
                enabled = !isSubmitting,
                isLoading = isSubmitting,
            )
        }
    }
}

// ==================== Helper Functions ====================

/**
 * Returns the display name for each step in the simplified 4-step flow.
 *
 * Step mapping (simplified from 6 to 4):
 * - Step 1: STI Selection (unchanged)
 * - Step 2: Test Date (unchanged)
 * - Step 3: Privacy Options (previously step 5)
 * - Step 4: Review (previously step 6)
 */
@Composable
fun getStepName(step: Int): String =
    when (step) {
        1 -> stringResource(Res.string.report_step1_name) // STI Selection
        2 -> stringResource(Res.string.report_step2_name) // Test Date
        3 -> stringResource(Res.string.report_step5_name) // Privacy Options (was step 5)
        4 -> stringResource(Res.string.report_step6_name) // Review (was step 6)
        else -> ""
    }

/**
 * Returns the display name for an STI type.
 */
@Composable
fun getSTIDisplayName(sti: STI): String =
    when (sti) {
        STI.HIV -> stringResource(Res.string.sti_hiv)
        STI.SYPHILIS -> stringResource(Res.string.sti_syphilis)
        STI.GONORRHEA -> stringResource(Res.string.sti_gonorrhea)
        STI.CHLAMYDIA -> stringResource(Res.string.sti_chlamydia)
        STI.HPV -> stringResource(Res.string.sti_hpv)
        STI.HERPES -> stringResource(Res.string.sti_herpes)
        STI.OTHER -> stringResource(Res.string.sti_other)
    }

/**
 * Formats a LocalDate for display.
 */
fun formatLocalDate(date: LocalDate): String = DateTimeFormatter.formatDateOnly(date)

/**
 * Formats a timestamp for display.
 */
fun formatTimestamp(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return DateTimeFormatter.formatDateTimeShort(localDateTime)
}

// ============== Previews ==============

@Preview
@Composable
private fun ExposureReportProgressIndicatorStep1Preview() {
    MaterialTheme {
        ExposureReportProgressIndicator(currentStep = 1)
    }
}

@Preview
@Composable
private fun ExposureReportProgressIndicatorStep3Preview() {
    MaterialTheme {
        ExposureReportProgressIndicator(currentStep = 3)
    }
}

@Preview
@Composable
private fun ExposureReportNavigationButtonsFirstStepPreview() {
    MaterialTheme {
        ExposureReportNavigationButtons(
            currentStep = 1,
            canProceed = true,
            isSubmitting = false,
            onBack = {},
            onNext = {},
            onSubmit = {},
        )
    }
}

@Preview
@Composable
private fun ExposureReportNavigationButtonsMiddleStepPreview() {
    MaterialTheme {
        ExposureReportNavigationButtons(
            currentStep = 2,
            canProceed = true,
            isSubmitting = false,
            onBack = {},
            onNext = {},
            onSubmit = {},
        )
    }
}

@Preview
@Composable
private fun ExposureReportNavigationButtonsLastStepPreview() {
    MaterialTheme {
        ExposureReportNavigationButtons(
            currentStep = 4,
            canProceed = true,
            isSubmitting = false,
            onBack = {},
            onNext = {},
            onSubmit = {},
        )
    }
}
