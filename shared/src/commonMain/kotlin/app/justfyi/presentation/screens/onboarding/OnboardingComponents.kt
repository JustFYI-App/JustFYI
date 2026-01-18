package app.justfyi.presentation.screens.onboarding

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.presentation.components.JustFyiButton
import app.justfyi.presentation.components.JustFyiButtonVariant
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Total number of steps in the onboarding flow.
 */
const val ONBOARDING_TOTAL_STEPS = 4

/**
 * Progress indicator showing current step in the onboarding flow.
 */
@Composable
fun OnboardingProgressIndicator(
    currentStep: Int,
    totalSteps: Int = ONBOARDING_TOTAL_STEPS,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.onboarding_step_count, currentStep, totalSteps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = getStepName(currentStep),
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
 * Navigation buttons for the onboarding flow.
 */
@Composable
fun OnboardingNavigationButtons(
    currentStep: Int,
    totalSteps: Int = ONBOARDING_TOTAL_STEPS,
    canProceed: Boolean,
    isLoading: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        // Skip button for optional steps
        if (onSkip != null && currentStep in listOf(3, 4)) {
            JustFyiButton(
                text =
                    if (currentStep ==
                        4
                    ) {
                        stringResource(Res.string.onboarding_skip_and_finish)
                    } else {
                        stringResource(Res.string.onboarding_skip_for_now)
                    },
                onClick = onSkip,
                variant = JustFyiButtonVariant.TEXT,
                fullWidth = true,
                enabled = !isLoading,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (currentStep > 1) {
                JustFyiButton(
                    text = stringResource(Res.string.nav_back),
                    onClick = onBack,
                    variant = JustFyiButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                )
            }

            if (currentStep < totalSteps) {
                JustFyiButton(
                    text = stringResource(Res.string.nav_next),
                    onClick = onNext,
                    variant = JustFyiButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f),
                    enabled = canProceed && !isLoading,
                )
            } else {
                JustFyiButton(
                    text = stringResource(Res.string.onboarding_complete_setup),
                    onClick = onComplete,
                    variant = JustFyiButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    isLoading = isLoading,
                )
            }
        }
    }
}

/**
 * Returns the display name for each onboarding step.
 */
@Composable
fun getStepName(step: Int): String =
    when (step) {
        1 -> stringResource(Res.string.onboarding_step_welcome)
        2 -> stringResource(Res.string.onboarding_step_id_backup)
        3 -> stringResource(Res.string.onboarding_step_permissions)
        4 -> stringResource(Res.string.onboarding_step_username)
        else -> ""
    }

// ============== Previews ==============

@Preview
@Composable
private fun OnboardingProgressIndicatorStep1Preview() {
    MaterialTheme {
        OnboardingProgressIndicator(currentStep = 1, totalSteps = 4)
    }
}

@Preview
@Composable
private fun OnboardingProgressIndicatorStep3Preview() {
    MaterialTheme {
        OnboardingProgressIndicator(currentStep = 3, totalSteps = 4)
    }
}

@Preview
@Composable
private fun OnboardingNavigationButtonsFirstStepPreview() {
    MaterialTheme {
        OnboardingNavigationButtons(
            currentStep = 1,
            totalSteps = 4,
            canProceed = true,
            isLoading = false,
            onBack = {},
            onNext = {},
            onComplete = {},
            onSkip = null,
        )
    }
}

@Preview
@Composable
private fun OnboardingNavigationButtonsLastStepPreview() {
    MaterialTheme {
        OnboardingNavigationButtons(
            currentStep = 4,
            totalSteps = 4,
            canProceed = true,
            isLoading = false,
            onBack = {},
            onNext = {},
            onComplete = {},
            onSkip = {},
        )
    }
}
