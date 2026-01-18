package app.justfyi.presentation.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justfyi.presentation.components.JustFyiButton
import app.justfyi.presentation.components.JustFyiButtonVariant
import app.justfyi.presentation.components.JustFyiCard
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.presentation.feature.onboarding.OnboardingUiState
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Step 1: Welcome screen with choice to create new account or recover existing.
 * Does NOT auto-generate an ID. User must explicitly choose an option.
 */
@Composable
fun WelcomeStep(
    uiState: OnboardingUiState,
    onStartOnboarding: () -> Unit,
    onRetry: () -> Unit,
    onEnterRecoveryMode: () -> Unit,
    onExitRecoveryMode: () -> Unit,
    onResetToChoice: () -> Unit,
    onUpdateRecoveryId: (String) -> Unit,
    onSubmitRecoveryId: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(Res.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.onboarding_welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (val state = uiState) {
            is OnboardingUiState.Loading -> {
                LoadingIndicator(message = stringResource(Res.string.onboarding_loading_account))
            }
            is OnboardingUiState.Error -> {
                WelcomeErrorCard(
                    error = state.message,
                    onRetry = onRetry,
                )
            }
            is OnboardingUiState.Success -> {
                if (state.isRecoveryMode) {
                    // Show recovery mode UI
                    RecoveryModeContent(
                        recoveryId = state.recoveryId,
                        recoveryError = state.recoveryError,
                        onUpdateRecoveryId = onUpdateRecoveryId,
                        onSubmitRecoveryId = onSubmitRecoveryId,
                        onCancel = onResetToChoice,
                    )
                } else if (state.generatedId != null) {
                    // ID already generated - show success with option to start over
                    WelcomeSuccessCard(formattedId = state.formattedId)
                    Spacer(modifier = Modifier.height(24.dp))
                    WelcomeWarningCard()
                    Spacer(modifier = Modifier.height(16.dp))
                    JustFyiButton(
                        text = stringResource(Res.string.onboarding_start_over),
                        onClick = onResetToChoice,
                        variant = JustFyiButtonVariant.TEXT,
                    )
                } else {
                    // Initial state - show choice between new account and recovery
                    WelcomeChoiceContent(
                        onCreateNewAccount = onStartOnboarding,
                        onRecoverAccount = onEnterRecoveryMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeChoiceContent(
    onCreateNewAccount: () -> Unit,
    onRecoverAccount: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JustFyiCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_get_started),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.onboarding_get_started_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))

                JustFyiButton(
                    text = stringResource(Res.string.onboarding_create_new_account),
                    onClick = onCreateNewAccount,
                    variant = JustFyiButtonVariant.PRIMARY,
                    fullWidth = true,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.onboarding_already_have_account),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        JustFyiButton(
            text = stringResource(Res.string.onboarding_recover_existing),
            onClick = onRecoverAccount,
            variant = JustFyiButtonVariant.SECONDARY,
        )
    }
}

@Composable
private fun WelcomeErrorCard(
    error: String,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            JustFyiButton(
                text = stringResource(Res.string.onboarding_try_again),
                onClick = onRetry,
                variant = JustFyiButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
private fun WelcomeSuccessCard(formattedId: String) {
    JustFyiCard {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_your_anonymous_id),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formattedId,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WelcomeWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_id_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun RecoveryModeContent(
    recoveryId: String,
    recoveryError: String?,
    onUpdateRecoveryId: (String) -> Unit,
    onSubmitRecoveryId: () -> Unit,
    onCancel: () -> Unit,
) {
    JustFyiCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.onboarding_recover_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.onboarding_recover_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = recoveryId,
                onValueChange = onUpdateRecoveryId,
                label = { Text(stringResource(Res.string.onboarding_anonymous_id)) },
                placeholder = { Text(stringResource(Res.string.onboarding_anonymous_id_placeholder)) },
                isError = recoveryError != null,
                supportingText =
                    if (recoveryError != null) {
                        { Text(recoveryError, color = MaterialTheme.colorScheme.error) }
                    } else {
                        null
                    },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle =
                    TextStyle(
                        fontFamily = FontFamily.Monospace,
                    ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            JustFyiButton(
                text = stringResource(Res.string.onboarding_recover_account),
                onClick = onSubmitRecoveryId,
                variant = JustFyiButtonVariant.PRIMARY,
                fullWidth = true,
            )

            Spacer(modifier = Modifier.height(8.dp))

            JustFyiButton(
                text = stringResource(Res.string.onboarding_create_new_instead),
                onClick = onCancel,
                variant = JustFyiButtonVariant.TEXT,
                fullWidth = true,
            )
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun WelcomeChoiceContentPreview() {
    MaterialTheme {
        WelcomeChoiceContent(
            onCreateNewAccount = {},
            onRecoverAccount = {},
        )
    }
}

@Preview
@Composable
private fun WelcomeSuccessCardPreview() {
    MaterialTheme {
        WelcomeSuccessCard(formattedId = "ABC1-23DE-F456-GHI7-89JK-L012")
    }
}

@Preview
@Composable
private fun WelcomeErrorCardPreview() {
    MaterialTheme {
        WelcomeErrorCard(
            error = "Failed to generate ID. Please try again.",
            onRetry = {},
        )
    }
}
