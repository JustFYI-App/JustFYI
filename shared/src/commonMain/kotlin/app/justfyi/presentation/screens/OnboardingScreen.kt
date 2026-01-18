package app.justfyi.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.presentation.feature.onboarding.OnboardingUiState
import app.justfyi.presentation.feature.onboarding.OnboardingViewModel
import app.justfyi.presentation.navigation.NavigationActions
import app.justfyi.presentation.screens.onboarding.IdBackupStep
import app.justfyi.presentation.screens.onboarding.OnboardingNavigationButtons
import app.justfyi.presentation.screens.onboarding.OnboardingProgressIndicator
import app.justfyi.presentation.screens.onboarding.PermissionsStep
import app.justfyi.presentation.screens.onboarding.UsernameStep
import app.justfyi.presentation.screens.onboarding.WelcomeStep
import androidx.compose.ui.tooling.preview.Preview

/**
 * Onboarding screen for first-time users.
 * Guides users through 4 steps: Welcome, ID Backup, Permissions, and Username.
 *
 * @param viewModel The OnboardingViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param modifier Modifier for the screen
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    navigationActions: NavigationActions,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle side effects
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is OnboardingUiState.Success) {
            // Show copied message
            if (state.showCopiedMessage) {
                snackbarHostState.showSnackbar(
                    message = "ID copied to clipboard",
                    duration = SnackbarDuration.Short,
                )
                viewModel.clearCopiedMessage()
            }

            // Handle onboarding completion
            if (state.isOnboardingComplete) {
                navigationActions.completeOnboardingAndNavigateHome()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { JustFyiSnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is OnboardingUiState.Loading -> {
                    LoadingIndicator(message = "Setting up...")
                }

                is OnboardingUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        onRetry = { viewModel.retryOnboarding() },
                    )
                }

                is OnboardingUiState.Success -> {
                    OnboardingContent(
                        state = state,
                        currentStep = currentStep,
                        canProceed = viewModel.canProceedFromStep(currentStep),
                        onStartOnboarding = { viewModel.startOnboarding() },
                        onRetry = { viewModel.retryOnboarding() },
                        onEnterRecoveryMode = { viewModel.enterRecoveryMode() },
                        onExitRecoveryMode = { viewModel.exitRecoveryMode() },
                        onResetToChoice = { viewModel.resetToChoice() },
                        onUpdateRecoveryId = { id -> viewModel.updateRecoveryId(id) },
                        onSubmitRecoveryId = { viewModel.submitRecoveryId() },
                        onToggleReveal = { viewModel.toggleIdReveal() },
                        onCopyId = { viewModel.copyIdToClipboard() },
                        onConfirmBackup = { viewModel.confirmBackup() },
                        onBluetoothResult = { granted -> viewModel.updateBluetoothPermission(granted) },
                        onNotificationResult = { granted -> viewModel.updateNotificationPermission(granted) },
                        onUsernameChange = { name -> viewModel.updateUsernameField(name) },
                        onSetUsername = { name -> viewModel.setUsername(name) },
                        onBack = { viewModel.previousStep() },
                        onNext = { viewModel.nextStep() },
                        onComplete = { viewModel.completeOnboarding() },
                        onSkipPermissions = { viewModel.skipPermissions() },
                        onSkipUsername = { viewModel.skipUsername() },
                    )
                }
            }
        }
    }
}

/**
 * Main content composable for OnboardingScreen.
 * Separated from the screen to enable preview without ViewModel dependency.
 */
@Composable
private fun OnboardingContent(
    state: OnboardingUiState.Success,
    currentStep: Int,
    canProceed: Boolean,
    onStartOnboarding: () -> Unit,
    onRetry: () -> Unit,
    onEnterRecoveryMode: () -> Unit,
    onExitRecoveryMode: () -> Unit,
    onResetToChoice: () -> Unit,
    onUpdateRecoveryId: (String) -> Unit,
    onSubmitRecoveryId: () -> Unit,
    onToggleReveal: () -> Unit,
    onCopyId: () -> Unit,
    onConfirmBackup: () -> Unit,
    onBluetoothResult: (Boolean) -> Unit,
    onNotificationResult: (Boolean) -> Unit,
    onUsernameChange: (String) -> Unit,
    onSetUsername: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit,
    onSkipPermissions: () -> Unit,
    onSkipUsername: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Step indicator
        OnboardingProgressIndicator(
            currentStep = currentStep,
            totalSteps = OnboardingViewModel.TOTAL_STEPS,
        )

        // Step content with animated transitions
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = currentStep,
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopStart,
                transitionSpec = {
                    if (targetState > initialState) {
                        // Forward: slide in from right
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut()) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    } else {
                        // Backward: slide in from left
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut()) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    }
                },
                label = "OnboardingStepTransition",
            ) { step ->
                when (step) {
                    1 ->
                        WelcomeStep(
                            uiState = state,
                            onStartOnboarding = onStartOnboarding,
                            onRetry = onRetry,
                            onEnterRecoveryMode = onEnterRecoveryMode,
                            onExitRecoveryMode = onExitRecoveryMode,
                            onResetToChoice = onResetToChoice,
                            onUpdateRecoveryId = onUpdateRecoveryId,
                            onSubmitRecoveryId = onSubmitRecoveryId,
                        )

                    2 ->
                        IdBackupStep(
                            formattedId = state.formattedId,
                            isIdRevealed = state.isIdRevealed,
                            isBackupConfirmed = state.isBackupConfirmed,
                            onToggleReveal = onToggleReveal,
                            onCopyId = onCopyId,
                            onConfirmBackup = onConfirmBackup,
                        )

                    3 ->
                        PermissionsStep(
                            bluetoothGranted = state.bluetoothPermissionGranted,
                            notificationGranted = state.notificationPermissionGranted,
                            onBluetoothResult = onBluetoothResult,
                            onNotificationResult = onNotificationResult,
                        )

                    4 ->
                        UsernameStep(
                            username = state.username,
                            usernameError = state.usernameError,
                            isLoading = false,
                            onUsernameChange = onUsernameChange,
                            onSetUsername = onSetUsername,
                            onSkip = {
                                onSkipUsername()
                                onComplete()
                            },
                        )
                }
            }
        }

        // Navigation buttons
        OnboardingNavigationButtons(
            currentStep = currentStep,
            totalSteps = OnboardingViewModel.TOTAL_STEPS,
            canProceed = canProceed,
            isLoading = false,
            onBack = onBack,
            onNext = onNext,
            onComplete = onComplete,
            onSkip =
                if (currentStep in listOf(3, 4)) {
                    {
                        if (currentStep == 4) {
                            onSkipUsername()
                            onComplete()
                        } else {
                            onSkipPermissions()
                            onNext()
                        }
                    }
                } else {
                    null
                },
        )
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun OnboardingContentStep1Preview() {
    MaterialTheme {
        OnboardingContent(
            state =
                OnboardingUiState.Success(
                    generatedId = "ABC123DEF456GHI789JKL012",
                    formattedId = "ABC1-23DE-F456-GHI7-89JK-L012",
                    isIdRevealed = false,
                    isBackupConfirmed = false,
                    showCopiedMessage = false,
                    bluetoothPermissionGranted = false,
                    notificationPermissionGranted = false,
                    username = "",
                    usernameError = null,
                    isOnboardingComplete = false,
                    isRecoveryMode = false,
                    recoveryId = "",
                    recoveryError = null,
                ),
            currentStep = 1,
            canProceed = true,
            onStartOnboarding = {},
            onRetry = {},
            onEnterRecoveryMode = {},
            onExitRecoveryMode = {},
            onResetToChoice = {},
            onUpdateRecoveryId = {},
            onSubmitRecoveryId = {},
            onToggleReveal = {},
            onCopyId = {},
            onConfirmBackup = {},
            onBluetoothResult = {},
            onNotificationResult = {},
            onUsernameChange = {},
            onSetUsername = {},
            onBack = {},
            onNext = {},
            onComplete = {},
            onSkipPermissions = {},
            onSkipUsername = {},
        )
    }
}

@Preview
@Composable
private fun OnboardingContentStep2Preview() {
    MaterialTheme {
        OnboardingContent(
            state =
                OnboardingUiState.Success(
                    generatedId = "ABC123DEF456GHI789JKL012",
                    formattedId = "ABC1-23DE-F456-GHI7-89JK-L012",
                    isIdRevealed = true,
                    isBackupConfirmed = false,
                    showCopiedMessage = false,
                    bluetoothPermissionGranted = false,
                    notificationPermissionGranted = false,
                    username = "",
                    usernameError = null,
                    isOnboardingComplete = false,
                    isRecoveryMode = false,
                    recoveryId = "",
                    recoveryError = null,
                ),
            currentStep = 2,
            canProceed = false,
            onStartOnboarding = {},
            onRetry = {},
            onEnterRecoveryMode = {},
            onExitRecoveryMode = {},
            onResetToChoice = {},
            onUpdateRecoveryId = {},
            onSubmitRecoveryId = {},
            onToggleReveal = {},
            onCopyId = {},
            onConfirmBackup = {},
            onBluetoothResult = {},
            onNotificationResult = {},
            onUsernameChange = {},
            onSetUsername = {},
            onBack = {},
            onNext = {},
            onComplete = {},
            onSkipPermissions = {},
            onSkipUsername = {},
        )
    }
}

@Preview
@Composable
private fun OnboardingContentStep4Preview() {
    MaterialTheme {
        OnboardingContent(
            state =
                OnboardingUiState.Success(
                    generatedId = "ABC123DEF456GHI789JKL012",
                    formattedId = "ABC1-23DE-F456-GHI7-89JK-L012",
                    isIdRevealed = true,
                    isBackupConfirmed = true,
                    showCopiedMessage = false,
                    bluetoothPermissionGranted = true,
                    notificationPermissionGranted = true,
                    username = "JohnDoe",
                    usernameError = null,
                    isOnboardingComplete = false,
                    isRecoveryMode = false,
                    recoveryId = "",
                    recoveryError = null,
                ),
            currentStep = 4,
            canProceed = true,
            onStartOnboarding = {},
            onRetry = {},
            onEnterRecoveryMode = {},
            onExitRecoveryMode = {},
            onResetToChoice = {},
            onUpdateRecoveryId = {},
            onSubmitRecoveryId = {},
            onToggleReveal = {},
            onCopyId = {},
            onConfirmBackup = {},
            onBluetoothResult = {},
            onNotificationResult = {},
            onUsernameChange = {},
            onSetUsername = {},
            onBack = {},
            onNext = {},
            onComplete = {},
            onSkipPermissions = {},
            onSkipUsername = {},
        )
    }
}
