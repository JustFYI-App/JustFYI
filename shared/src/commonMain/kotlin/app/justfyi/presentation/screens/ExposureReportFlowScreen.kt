package app.justfyi.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.justfyi.domain.model.PrivacyOptions
import app.justfyi.domain.model.STI
import app.justfyi.domain.usecase.ExposureReportState
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiDialog
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.feature.exposure.ExposureReportFlowUiState
import app.justfyi.presentation.feature.exposure.ExposureReportFlowViewModel
import app.justfyi.presentation.navigation.NavigationActions
import app.justfyi.presentation.screens.exposurereport.DatePickerStep
import app.justfyi.presentation.screens.exposurereport.EXPOSURE_REPORT_TOTAL_STEPS
import app.justfyi.presentation.screens.exposurereport.ExposureReportNavigationButtons
import app.justfyi.presentation.screens.exposurereport.ExposureReportProgressIndicator
import app.justfyi.presentation.screens.exposurereport.PrivacyOptionsStep
import app.justfyi.presentation.screens.exposurereport.ReviewStep
import app.justfyi.presentation.screens.exposurereport.STISelectionStep
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Main container for the simplified 4-step exposure report flow.
 *
 * Simplified flow (reduced from 6 to 4 steps):
 * - Step 1: STI Selection
 * - Step 2: Test Date
 * - Step 3: Privacy Options (previously step 5)
 * - Step 4: Review & Submit (previously step 6)
 *
 * Removed steps (now handled server-side):
 * - Exposure Window display (backend calculates)
 * - Contact Selection (backend automatically determines)
 *
 * @param viewModel The ExposureReportFlowViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param onReportComplete Callback when report is submitted successfully
 * @param modifier Modifier for the screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposureReportFlowScreen(
    viewModel: ExposureReportFlowViewModel,
    navigationActions: NavigationActions,
    onReportComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCancelDialog by remember { mutableStateOf(false) }

    // Handle UI state side effects
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ExposureReportFlowUiState.Active -> {
                // Show error message
                state.error?.let { error ->
                    snackbarHostState.showSnackbar(
                        message = error,
                        duration = SnackbarDuration.Long,
                    )
                    viewModel.clearError()
                }

                // Show validation error
                state.validationError?.let { error ->
                    snackbarHostState.showSnackbar(
                        message = error,
                        duration = SnackbarDuration.Short,
                    )
                    viewModel.clearValidationError()
                }

                // Handle successful submission
                if (state.isSubmitted) {
                    onReportComplete()
                }
            }
            is ExposureReportFlowUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }

    // Cancel Confirmation Dialog
    if (showCancelDialog) {
        JustFyiDialog(
            title = stringResource(Res.string.report_cancel),
            message = stringResource(Res.string.report_cancel_message),
            confirmText = stringResource(Res.string.report_yes_cancel),
            dismissText = stringResource(Res.string.report_continue),
            onConfirm = {
                viewModel.cancelReport()
                navigationActions.navigateBack()
            },
            onDismiss = { showCancelDialog = false },
            isDestructive = true,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.report_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showCancelDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.cd_cancel),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        snackbarHost = { JustFyiSnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (val state = uiState) {
            is ExposureReportFlowUiState.Error -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                ) {
                    ErrorMessage(
                        message = state.message,
                        onRetry = { viewModel.cancelReport() },
                    )
                }
            }

            is ExposureReportFlowUiState.Active -> {
                ExposureReportFlowContent(
                    state = state,
                    modifier = Modifier.padding(paddingValues),
                    onToggleSTI = viewModel::toggleSTI,
                    onDateSelected = viewModel::setTestDate,
                    onToggleSTIDisclosure = viewModel::toggleSTIDisclosure,
                    onToggleDateDisclosure = viewModel::toggleDateDisclosure,
                    onBack = viewModel::previousStep,
                    onNext = viewModel::nextStep,
                    onSubmit = viewModel::submitReport,
                )
            }
        }
    }
}

/**
 * Main content composable for ExposureReportFlowScreen.
 * Separated from the screen to enable preview without ViewModel dependency.
 *
 * Simplified 4-step flow:
 * - Step 1: STI Selection
 * - Step 2: Date Picker
 * - Step 3: Privacy Options
 * - Step 4: Review
 */
@Composable
private fun ExposureReportFlowContent(
    state: ExposureReportFlowUiState.Active,
    modifier: Modifier = Modifier,
    onToggleSTI: (STI) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onToggleSTIDisclosure: (Boolean) -> Unit,
    onToggleDateDisclosure: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Progress indicator
        ExposureReportProgressIndicator(
            currentStep = state.currentStep,
            totalSteps = EXPOSURE_REPORT_TOTAL_STEPS,
        )

        // Step content with animated transitions
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        // Forward: slide in from right
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        // Backward: slide in from left
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "ExposureReportStepTransition",
            ) { step ->
                when (step) {
                    1 ->
                        STISelectionStep(
                            selectedSTIs = state.reportState.selectedSTIs,
                            onToggleSTI = onToggleSTI,
                        )
                    2 ->
                        DatePickerStep(
                            testDate = state.reportState.testDate,
                            validationError = state.reportState.dateValidationError,
                            onDateSelected = onDateSelected,
                        )
                    3 ->
                        PrivacyOptionsStep(
                            privacyOptions = state.reportState.privacyOptions,
                            onToggleSTIDisclosure = onToggleSTIDisclosure,
                            onToggleDateDisclosure = onToggleDateDisclosure,
                        )
                    4 ->
                        ReviewStep(
                            reportState = state.reportState,
                            isSubmitting = state.isSubmitting,
                        )
                }
            }
        }

        // Navigation buttons
        ExposureReportNavigationButtons(
            currentStep = state.currentStep,
            totalSteps = EXPOSURE_REPORT_TOTAL_STEPS,
            canProceed = state.reportState.canProceedToNextStep(),
            isSubmitting = state.isSubmitting,
            onBack = onBack,
            onNext = onNext,
            onSubmit = onSubmit,
        )
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun ExposureReportFlowContentStep1Preview() {
    MaterialTheme {
        ExposureReportFlowContent(
            state =
                ExposureReportFlowUiState.Active(
                    currentStep = 1,
                    reportState = ExposureReportState(),
                    isCalculating = false,
                    isSubmitting = false,
                    isSubmitted = false,
                    validationError = null,
                    error = null,
                ),
            onToggleSTI = {},
            onDateSelected = {},
            onToggleSTIDisclosure = {},
            onToggleDateDisclosure = {},
            onBack = {},
            onNext = {},
            onSubmit = {},
        )
    }
}

@Preview
@Composable
private fun ExposureReportFlowContentStep3Preview() {
    MaterialTheme {
        ExposureReportFlowContent(
            state =
                ExposureReportFlowUiState.Active(
                    currentStep = 3,
                    reportState =
                        ExposureReportState(
                            selectedSTIs = listOf(STI.CHLAMYDIA, STI.GONORRHEA),
                            privacyOptions =
                                PrivacyOptions(
                                    discloseSTIType = true,
                                    discloseExposureDate = false,
                                ),
                        ),
                    isCalculating = false,
                    isSubmitting = false,
                    isSubmitted = false,
                    validationError = null,
                    error = null,
                ),
            onToggleSTI = {},
            onDateSelected = {},
            onToggleSTIDisclosure = {},
            onToggleDateDisclosure = {},
            onBack = {},
            onNext = {},
            onSubmit = {},
        )
    }
}

@Preview
@Composable
private fun ExposureReportFlowContentStep4Preview() {
    MaterialTheme {
        ExposureReportFlowContent(
            state =
                ExposureReportFlowUiState.Active(
                    currentStep = 4,
                    reportState =
                        ExposureReportState(
                            selectedSTIs = listOf(STI.HIV),
                            privacyOptions = PrivacyOptions.DEFAULT,
                        ),
                    isCalculating = false,
                    isSubmitting = false,
                    isSubmitted = false,
                    validationError = null,
                    error = null,
                ),
            onToggleSTI = {},
            onDateSelected = {},
            onToggleSTIDisclosure = {},
            onToggleDateDisclosure = {},
            onBack = {},
            onNext = {},
            onSubmit = {},
        )
    }
}
