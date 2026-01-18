package app.justfyi.presentation.feature.exposure

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justfyi.domain.model.PrivacyOptions
import app.justfyi.domain.model.STI
import app.justfyi.domain.usecase.ExposureReportState
import app.justfyi.domain.usecase.ExposureReportUseCase
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * ViewModel for the Exposure Report Flow (simplified 4-step wizard).
 * Manages navigation between steps and report state.
 *
 * Simplified flow (reduced from 6 to 4 steps):
 * - Step 1: STI Selection
 * - Step 2: Test Date
 * - Step 3: Privacy Options (previously step 5)
 * - Step 4: Review & Submit (previously step 6)
 *
 * Removed functionality (now handled server-side):
 * - Exposure window display (backend calculates)
 * - Contact selection (backend automatically determines using unidirectional graph traversal)
 *
 * Dependencies (injected via Metro DI):
 * - ExposureReportUseCase: Orchestrates the exposure reporting flow
 */
@Inject
class ExposureReportFlowViewModel(
    private val exposureReportUseCase: ExposureReportUseCase,
) : ViewModel() {
    // Internal state flows
    private val _currentStep = MutableStateFlow(1)
    private val _reportState = MutableStateFlow(ExposureReportState.INITIAL)
    private val _isCalculating = MutableStateFlow(false)
    private val _isSubmitting = MutableStateFlow(false)
    private val _isSubmitted = MutableStateFlow(false)
    private val _validationError = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * Combined UI state using sealed interface pattern.
     * Uses stateIn with WhileSubscribed(5000) for proper lifecycle handling.
     */
    val uiState: StateFlow<ExposureReportFlowUiState> =
        combine(
            _currentStep,
            _reportState,
            _isCalculating,
            _isSubmitting,
            _isSubmitted,
            _validationError,
            _error,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val currentStep = values[0] as Int
            val reportState = values[1] as ExposureReportState
            val isCalculating = values[2] as Boolean
            val isSubmitting = values[3] as Boolean
            val isSubmitted = values[4] as Boolean
            val validationError = values[5] as String?
            val error = values[6] as String?

            when {
                error != null && !isSubmitting && !isCalculating -> ExposureReportFlowUiState.Error(error)
                else ->
                    ExposureReportFlowUiState.Active(
                        currentStep = currentStep,
                        reportState = reportState,
                        isCalculating = isCalculating,
                        isSubmitting = isSubmitting,
                        isSubmitted = isSubmitted,
                        validationError = validationError,
                        error = error,
                    )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue =
                ExposureReportFlowUiState.Active(
                    currentStep = 1,
                    reportState = ExposureReportState.INITIAL,
                    isCalculating = false,
                    isSubmitting = false,
                    isSubmitted = false,
                    validationError = null,
                    error = null,
                ),
        )

    init {
        initializeReport()
        observeReportState()
    }

    private fun initializeReport() {
        viewModelScope.launch {
            exposureReportUseCase.createReport()
        }
    }

    private fun observeReportState() {
        viewModelScope.launch {
            exposureReportUseCase.reportState.collect { state ->
                _reportState.value = state
            }
        }
    }

    /**
     * Navigates to the next step if validation passes.
     *
     * Simplified flow - no longer triggers exposure window calculation
     * or contact loading as these are now handled server-side.
     */
    fun nextStep() {
        val currentStep = _currentStep.value
        // Use current step for validation, not the reportState.currentStep
        val state = _reportState.value.copy(currentStep = currentStep)
        if (!state.canProceedToNextStep()) {
            _validationError.value = getValidationError()
            return
        }

        state.nextStep()?.let { next ->
            _currentStep.value = next
            _validationError.value = null

            // Note: Exposure window calculation is still performed internally
            // for validation purposes, but no longer displayed to the user.
            // The backend now handles contact determination automatically.
        }
    }

    /**
     * Navigates to the previous step.
     */
    fun previousStep() {
        val currentStep = _currentStep.value
        // Use current step for navigation, not the reportState.currentStep
        if (currentStep > 1) {
            _currentStep.value = currentStep - 1
            _validationError.value = null
        }
    }

    /**
     * Navigates to a specific step.
     */
    fun goToStep(step: Int) {
        if (step in 1..ExposureReportState.TOTAL_STEPS) {
            _currentStep.value = step
            _validationError.value = null
        }
    }

    // ==================== Step 1: STI Selection ====================

    /**
     * Toggles selection of an STI type.
     */
    fun toggleSTI(sti: STI) {
        viewModelScope.launch {
            val current = _reportState.value.selectedSTIs.toMutableList()
            if (current.contains(sti)) {
                current.remove(sti)
            } else {
                current.add(sti)
            }

            if (current.isNotEmpty()) {
                exposureReportUseCase.selectSTIs(current)
            }
        }
    }

    /**
     * Sets the selected STI types.
     */
    fun selectSTIs(stis: List<STI>) {
        viewModelScope.launch {
            if (stis.isNotEmpty()) {
                exposureReportUseCase.selectSTIs(stis)
            }
        }
    }

    // ==================== Step 2: Date Selection ====================

    /**
     * Sets the test date.
     */
    fun setTestDate(date: LocalDate) {
        viewModelScope.launch {
            exposureReportUseCase.setTestDate(date)
        }
    }

    // ==================== Step 3: Privacy Options (previously Step 5) ====================

    /**
     * Sets the privacy options.
     */
    fun setPrivacyOptions(options: PrivacyOptions) {
        viewModelScope.launch {
            exposureReportUseCase.setPrivacyOptions(options)
        }
    }

    /**
     * Toggles STI type disclosure.
     */
    fun toggleSTIDisclosure(disclose: Boolean) {
        val current = _reportState.value.privacyOptions
        setPrivacyOptions(current.copy(discloseSTIType = disclose))
    }

    /**
     * Toggles exposure date disclosure.
     */
    fun toggleDateDisclosure(disclose: Boolean) {
        val current = _reportState.value.privacyOptions
        setPrivacyOptions(current.copy(discloseExposureDate = disclose))
    }

    // ==================== Step 4: Review & Submit (previously Step 6) ====================

    /**
     * Submits the exposure report.
     *
     * The backend automatically determines contacts to notify using
     * unidirectional graph traversal with 10-hop chain propagation.
     */
    fun submitReport() {
        viewModelScope.launch {
            _isSubmitting.value = true

            val result = exposureReportUseCase.submitReport()

            result.onSuccess {
                _isSubmitting.value = false
                _isSubmitted.value = true
            }

            result.onFailure { e ->
                _isSubmitting.value = false
                _error.value = e.message ?: "Failed to submit report"
            }
        }
    }

    /**
     * Cancels the report flow.
     */
    fun cancelReport() {
        viewModelScope.launch {
            exposureReportUseCase.cancelReport()
            _currentStep.value = 1
            _isCalculating.value = false
            _isSubmitting.value = false
            _isSubmitted.value = false
            _validationError.value = null
            _error.value = null
        }
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Clears the validation error.
     */
    fun clearValidationError() {
        _validationError.value = null
    }

    private fun getValidationError(): String {
        val state = _reportState.value
        val currentStep = _currentStep.value
        return when (currentStep) {
            1 -> if (state.selectedSTIs.isEmpty()) "Please select at least one STI type" else ""
            2 ->
                when {
                    state.testDate == null -> "Please select a test date"
                    state.dateValidationError != null -> "Invalid date selected"
                    else -> ""
                }
            else -> ""
        }
    }

    // ==================== Deprecated Methods (kept for backward compatibility) ====================

    /**
     * Calculates exposure window internally for validation.
     * Note: This is no longer displayed to the user but may be used internally.
     *
     * @deprecated Exposure window is now calculated server-side.
     */
    @Deprecated("Exposure window is now calculated server-side")
    private fun calculateExposureWindow() {
        viewModelScope.launch {
            _isCalculating.value = true
            try {
                exposureReportUseCase.calculateExposureWindow()
                _isCalculating.value = false
            } catch (e: Exception) {
                _isCalculating.value = false
                _error.value = e.message ?: "Failed to calculate exposure window"
            }
        }
    }

    /**
     * @deprecated Contact selection is now automatic on the backend.
     */
    @Deprecated("Contact selection is now automatic on the backend")
    fun toggleContact(contactId: String) {
        // No-op: Contact selection is now automatic on the backend
    }

    /**
     * @deprecated Contact selection is now automatic on the backend.
     */
    @Deprecated("Contact selection is now automatic on the backend")
    fun selectAllContacts() {
        // No-op: Contact selection is now automatic on the backend
    }

    /**
     * @deprecated Contact selection is now automatic on the backend.
     */
    @Deprecated("Contact selection is now automatic on the backend")
    fun deselectAllContacts() {
        // No-op: Contact selection is now automatic on the backend
    }
}

/**
 * UI state for the Exposure Report Flow using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface ExposureReportFlowUiState {
    /**
     * Active state - the flow is in progress.
     */
    data class Active(
        val currentStep: Int,
        val reportState: ExposureReportState,
        val isCalculating: Boolean,
        val isSubmitting: Boolean,
        val isSubmitted: Boolean,
        val validationError: String?,
        val error: String?,
    ) : ExposureReportFlowUiState

    /**
     * Error state - a fatal error occurred.
     */
    data class Error(
        val message: String,
    ) : ExposureReportFlowUiState
}
