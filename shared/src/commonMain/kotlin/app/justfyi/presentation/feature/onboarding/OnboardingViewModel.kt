package app.justfyi.presentation.feature.onboarding

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justfyi.domain.repository.SettingsRepository
import app.justfyi.domain.usecase.AuthUseCase
import app.justfyi.domain.usecase.IdBackupUseCase
import app.justfyi.domain.usecase.UsernameUseCase
import app.justfyi.domain.usecase.UsernameValidationResult
import app.justfyi.platform.BlePermissionHandler
import app.justfyi.platform.ClipboardService
import app.justfyi.platform.NotificationPermissionHandler
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Onboarding flow (4-step wizard).
 * Manages navigation between steps and onboarding state.
 *
 * Steps:
 * 1. Welcome/ID Generation - Creates anonymous account and displays generated ID
 * 2. ID Backup - User confirms they've saved their ID for recovery
 * 3. Permissions - Request Bluetooth and notification permissions
 * 4. Username Setup - Optional username configuration
 *
 * Dependencies (injected via Metro DI):
 * - AuthUseCase: Anonymous authentication and ID generation
 * - UsernameUseCase: Username validation and setting
 * - IdBackupUseCase: ID formatting for display
 * - SettingsRepository: Persisting onboarding completion state
 * - BlePermissionHandler: BLE permission checking and rationale
 * - NotificationPermissionHandler: Notification permission checking
 * - ClipboardService: Platform-agnostic clipboard operations
 */
@Inject
class OnboardingViewModel(
    private val authUseCase: AuthUseCase,
    private val usernameUseCase: UsernameUseCase,
    private val idBackupUseCase: IdBackupUseCase,
    private val settingsRepository: SettingsRepository,
    private val blePermissionHandler: BlePermissionHandler,
    private val notificationPermissionHandler: NotificationPermissionHandler,
    private val clipboardService: ClipboardService,
) : ViewModel() {
    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep

    companion object {
        const val TOTAL_STEPS = 4
    }

    // Internal state flows - Group 1: Core state
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _generatedId = MutableStateFlow<String?>(null)
    private val _formattedId = MutableStateFlow("")
    private val _isIdRevealed = MutableStateFlow(false)

    // Internal state flows - Group 2: Backup and permissions
    private val _isBackupConfirmed = MutableStateFlow(false)
    private val _showCopiedMessage = MutableStateFlow(false)
    private val _bluetoothPermissionGranted = MutableStateFlow(false)
    private val _notificationPermissionGranted = MutableStateFlow(false)

    // Internal state flows - Group 3: Username and completion
    private val _username = MutableStateFlow("")
    private val _usernameError = MutableStateFlow<String?>(null)
    private val _isOnboardingComplete = MutableStateFlow(false)

    // Internal state flows - Group 4: Account Recovery
    private val _isRecoveryMode = MutableStateFlow(false)
    private val _recoveryId = MutableStateFlow("")
    private val _recoveryError = MutableStateFlow<String?>(null)
    private val _isRecovering = MutableStateFlow(false)

    /**
     * Combined UI state using sealed interface pattern.
     * Uses stateIn with WhileSubscribed(5000) for proper lifecycle handling.
     */
    val uiState: StateFlow<OnboardingUiState> =
        combine(
            combine(
                _isLoading,
                _error,
                _generatedId,
                _formattedId,
                _isIdRevealed,
            ) { isLoading, error, generatedId, formattedId, isIdRevealed ->
                OnboardingCoreState(isLoading, error, generatedId, formattedId, isIdRevealed)
            },
            combine(
                _isBackupConfirmed,
                _showCopiedMessage,
                _bluetoothPermissionGranted,
                _notificationPermissionGranted,
            ) {
                isBackupConfirmed,
                showCopiedMessage,
                bluetoothPermissionGranted,
                notificationPermissionGranted,
                ->
                OnboardingPermissionState(
                    isBackupConfirmed,
                    showCopiedMessage,
                    bluetoothPermissionGranted,
                    notificationPermissionGranted,
                )
            },
            combine(
                _username,
                _usernameError,
                _isOnboardingComplete,
            ) { username, usernameError, isOnboardingComplete ->
                OnboardingUsernameState(username, usernameError, isOnboardingComplete)
            },
            combine(
                _isRecoveryMode,
                _recoveryId,
                _recoveryError,
                _isRecovering,
            ) { isRecoveryMode, recoveryId, recoveryError, isRecovering ->
                OnboardingRecoveryState(isRecoveryMode, recoveryId, recoveryError, isRecovering)
            },
        ) { coreState, permissionState, usernameState, recoveryState ->
            when {
                coreState.error != null -> OnboardingUiState.Error(coreState.error)
                coreState.isLoading || recoveryState.isRecovering -> OnboardingUiState.Loading
                else ->
                    OnboardingUiState.Success(
                        generatedId = coreState.generatedId,
                        formattedId = coreState.formattedId,
                        isIdRevealed = coreState.isIdRevealed,
                        isBackupConfirmed = permissionState.isBackupConfirmed,
                        showCopiedMessage = permissionState.showCopiedMessage,
                        bluetoothPermissionGranted = permissionState.bluetoothPermissionGranted,
                        notificationPermissionGranted = permissionState.notificationPermissionGranted,
                        username = usernameState.username,
                        usernameError = usernameState.usernameError,
                        isOnboardingComplete = usernameState.isOnboardingComplete,
                        isRecoveryMode = recoveryState.isRecoveryMode,
                        recoveryId = recoveryState.recoveryId,
                        recoveryError = recoveryState.recoveryError,
                    )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OnboardingUiState.Loading,
        )

    // ==================== Step 1: Welcome/ID Generation ====================

    /**
     * Starts the onboarding process by signing in anonymously.
     * Called when the user enters step 1 or taps retry after an error.
     */
    fun startOnboarding() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = authUseCase.signInAnonymously()

            result.onSuccess { user ->
                val formattedId = idBackupUseCase.formatIdForDisplay(user.anonymousId)
                _isLoading.value = false
                _generatedId.value = user.anonymousId
                _formattedId.value = formattedId
                _error.value = null
            }

            result.onFailure { e ->
                _isLoading.value = false
                _error.value = e.message ?: "Failed to create account. Please try again."
            }
        }
    }

    /**
     * Retries the onboarding process after an error.
     */
    fun retryOnboarding() {
        clearError()
        startOnboarding()
    }

    // ==================== Account Recovery ====================

    /**
     * Enters recovery mode to allow entering an existing ID.
     */
    fun enterRecoveryMode() {
        _isRecoveryMode.value = true
        _recoveryId.value = ""
        _recoveryError.value = null
    }

    /**
     * Exits recovery mode and returns to choice screen.
     */
    fun exitRecoveryMode() {
        _isRecoveryMode.value = false
        _recoveryId.value = ""
        _recoveryError.value = null
    }

    /**
     * Resets to the initial choice screen.
     * Clears any generated ID or recovery state.
     */
    fun resetToChoice() {
        _generatedId.value = null
        _formattedId.value = ""
        _isRecoveryMode.value = false
        _recoveryId.value = ""
        _recoveryError.value = null
        _error.value = null
    }

    /**
     * Updates the recovery ID input field.
     *
     * @param id The ID entered by the user
     */
    fun updateRecoveryId(id: String) {
        _recoveryId.value = id
        // Clear error when user starts typing
        if (_recoveryError.value != null) {
            _recoveryError.value = null
        }
    }

    /**
     * Validates the recovery ID format.
     *
     * @return Error message or null if valid
     */
    fun validateRecoveryId(): String? {
        val id = _recoveryId.value
        if (id.isBlank()) {
            return "Please enter your ID"
        }
        if (!idBackupUseCase.isValidIdFormat(id)) {
            return "Invalid ID format. Please check and try again."
        }
        return null
    }

    /**
     * Submits the recovery ID to restore the account.
     */
    fun submitRecoveryId() {
        val validationError = validateRecoveryId()
        if (validationError != null) {
            _recoveryError.value = validationError
            return
        }

        viewModelScope.launch {
            _isRecovering.value = true
            _recoveryError.value = null

            val cleanedId = idBackupUseCase.parseFormattedId(_recoveryId.value)
            val result = authUseCase.recoverAccount(cleanedId)

            result.onSuccess { user ->
                _isRecovering.value = false
                _isRecoveryMode.value = false
                _generatedId.value = user.anonymousId
                _formattedId.value = idBackupUseCase.formatIdForDisplay(user.anonymousId)
                _isBackupConfirmed.value = true
                _username.value = user.username
                _recoveryError.value = null
                // Check if all permissions are already granted
                if (hasAllBluetoothPermissions() && notificationPermissionHandler.hasPermission()) {
                    // All permissions granted - complete onboarding
                    completeOnboardingForRecovery()
                } else {
                    // Skip to permissions step (step 3)
                    _currentStep.value = 3
                }
            }

            result.onFailure { e ->
                _isRecovering.value = false
                _recoveryError.value = e.message ?: "Failed to recover account. Please check your ID and try again."
            }
        }
    }

    // ==================== Step 2: ID Backup ====================

    /**
     * Toggles the visibility of the anonymous ID.
     */
    fun toggleIdReveal() {
        _isIdRevealed.value = !_isIdRevealed.value
    }

    /**
     * Copies the formatted ID to clipboard.
     * Shows a confirmation message via showCopiedMessage state.
     */
    fun copyIdToClipboard() {
        val formattedId = _formattedId.value
        if (formattedId.isNotEmpty()) {
            clipboardService.copyText("Just FYI ID", formattedId)
            _showCopiedMessage.value = true
        }
    }

    /**
     * Clears the copied message indicator.
     */
    fun clearCopiedMessage() {
        _showCopiedMessage.value = false
    }

    /**
     * Confirms that the user has backed up their ID.
     * Calls AuthUseCase.confirmIdBackup() and enables navigation to next step.
     */
    fun confirmBackup() {
        viewModelScope.launch {
            try {
                authUseCase.confirmIdBackup()
                _isBackupConfirmed.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to confirm backup"
            }
        }
    }

    // ==================== Step 3: Permissions ====================

    /**
     * Updates the Bluetooth permission state.
     * Called when the permission result is received.
     *
     * @param granted Whether Bluetooth permissions were granted
     */
    fun updateBluetoothPermission(granted: Boolean) {
        _bluetoothPermissionGranted.value = granted
    }

    /**
     * Updates the notification permission state.
     * Called when the permission result is received.
     *
     * @param granted Whether notification permission was granted
     */
    fun updateNotificationPermission(granted: Boolean) {
        _notificationPermissionGranted.value = granted
    }

    /**
     * Skips the permissions step without all permissions granted.
     * Shows a warning but allows the user to proceed.
     */
    fun skipPermissions() {
        // Permissions are optional - user can skip
        // The skip action itself doesn't need to update state,
        // as nextStep() will allow proceeding from step 3
    }

    /**
     * Gets the required BLE permissions list for the current Android version.
     *
     * @return List of permission strings
     */
    fun getRequiredBluetoothPermissions(): List<String> = blePermissionHandler.getRequiredPermissions()

    /**
     * Gets the permission rationale text for Bluetooth permissions.
     *
     * @return User-friendly explanation of why permissions are needed
     */
    fun getBluetoothPermissionRationale(): String = blePermissionHandler.getPermissionRationale()

    /**
     * Checks if all Bluetooth permissions are already granted.
     *
     * @return true if all required BLE permissions are granted
     */
    fun hasAllBluetoothPermissions(): Boolean = blePermissionHandler.hasAllPermissions()

    // ==================== Step 4: Username Setup ====================

    /**
     * Sets the username for the current user.
     * Validates the username and updates state accordingly.
     *
     * @param name The desired username
     */
    fun setUsername(name: String) {
        viewModelScope.launch {
            // Validate first
            val validationResult = usernameUseCase.validateUsername(name)

            if (!validationResult.isValid()) {
                _usernameError.value = getValidationErrorMessage(validationResult)
                return@launch
            }

            // Clear any previous error and set username
            _usernameError.value = null
            _isLoading.value = true

            val result = usernameUseCase.setUsername(name)

            result.onSuccess { finalUsername ->
                _isLoading.value = false
                _username.value = finalUsername
                _usernameError.value = null
            }

            result.onFailure { e ->
                _isLoading.value = false
                _usernameError.value = e.message ?: "Failed to set username"
            }
        }
    }

    /**
     * Validates a username and returns an error message if invalid.
     *
     * @param name The username to validate
     * @return Error message string or null if valid
     */
    fun validateUsername(name: String): String? {
        val result = usernameUseCase.validateUsername(name)
        return if (result.isValid()) null else getValidationErrorMessage(result)
    }

    /**
     * Skips the username setup step.
     * Uses the default anonymous username.
     */
    fun skipUsername() {
        // Username is optional - user can skip
        // No state change needed, just proceed to completion
    }

    /**
     * Updates the username field without persisting.
     * Used for real-time validation feedback.
     *
     * @param name The username text from input field
     */
    fun updateUsernameField(name: String) {
        val validationResult = usernameUseCase.validateUsername(name)
        _username.value = name
        _usernameError.value =
            if (name.isEmpty()) {
                null
            } else {
                if (validationResult.isValid()) null else getValidationErrorMessage(validationResult)
            }
    }

    private fun getValidationErrorMessage(result: UsernameValidationResult): String =
        when (result) {
            is UsernameValidationResult.Empty -> "Username cannot be empty"
            is UsernameValidationResult.TooLong -> "Username cannot exceed ${result.maxLength} characters"
            is UsernameValidationResult.NonAsciiCharacters -> "Username can only contain ASCII characters"
            is UsernameValidationResult.NonPrintableCharacters -> "Username contains invalid characters"
            is UsernameValidationResult.Valid -> "" // Should not happen
        }

    // ==================== Navigation and Completion ====================

    /**
     * Navigates to the next step if validation passes.
     */
    fun nextStep() {
        val current = _currentStep.value
        if (!canProceedFromStep(current)) {
            return
        }

        if (current < TOTAL_STEPS) {
            _currentStep.value = current + 1
        }
    }

    /**
     * Navigates to the previous step.
     * Minimum step is 1.
     */
    fun previousStep() {
        val current = _currentStep.value
        if (current > 1) {
            _currentStep.value = current - 1
        }
    }

    /**
     * Checks if the user can proceed from the given step.
     * Each step has specific validation requirements.
     *
     * @param step The step number to check
     * @return true if the user can proceed to the next step
     */
    fun canProceedFromStep(step: Int): Boolean {
        val currentUiState = uiState.value
        return when (step) {
            1 ->
                currentUiState is OnboardingUiState.Success &&
                    currentUiState.generatedId != null
            2 ->
                currentUiState is OnboardingUiState.Success &&
                    currentUiState.isBackupConfirmed
            3 -> true // Permissions can be skipped
            4 -> true // Username is optional
            else -> false
        }
    }

    /**
     * Completes the onboarding flow.
     * Saves the username if entered, then persists the completion state
     * and allows navigation to Home.
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            try {
                // Save username if one was entered
                val currentUsername = _username.value
                if (currentUsername.isNotEmpty()) {
                    usernameUseCase.setUsername(currentUsername)
                }

                settingsRepository.setOnboardingComplete(true)
                _isOnboardingComplete.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to complete onboarding"
            }
        }
    }

    /**
     * Completes onboarding for a recovered account.
     * Skips all remaining steps since the user already completed onboarding before.
     */
    private fun completeOnboardingForRecovery() {
        viewModelScope.launch {
            try {
                settingsRepository.setOnboardingComplete(true)
                _isOnboardingComplete.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to complete recovery"
            }
        }
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Clears the username error state.
     */
    fun clearUsernameError() {
        _usernameError.value = null
    }
}

// Internal data classes for combine operation
private data class OnboardingCoreState(
    val isLoading: Boolean,
    val error: String?,
    val generatedId: String?,
    val formattedId: String,
    val isIdRevealed: Boolean,
)

private data class OnboardingPermissionState(
    val isBackupConfirmed: Boolean,
    val showCopiedMessage: Boolean,
    val bluetoothPermissionGranted: Boolean,
    val notificationPermissionGranted: Boolean,
)

private data class OnboardingUsernameState(
    val username: String,
    val usernameError: String?,
    val isOnboardingComplete: Boolean,
)

private data class OnboardingRecoveryState(
    val isRecoveryMode: Boolean,
    val recoveryId: String,
    val recoveryError: String?,
    val isRecovering: Boolean,
)

/**
 * UI state for the Onboarding flow using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface OnboardingUiState {
    /**
     * Loading state - shown during async operations.
     */
    data object Loading : OnboardingUiState

    /**
     * Success state - contains all onboarding data.
     */
    data class Success(
        val generatedId: String?,
        val formattedId: String,
        val isIdRevealed: Boolean,
        val isBackupConfirmed: Boolean,
        val showCopiedMessage: Boolean,
        val bluetoothPermissionGranted: Boolean,
        val notificationPermissionGranted: Boolean,
        val username: String,
        val usernameError: String?,
        val isOnboardingComplete: Boolean,
        // Account Recovery
        val isRecoveryMode: Boolean,
        val recoveryId: String,
        val recoveryError: String?,
    ) : OnboardingUiState

    /**
     * Error state - contains error message.
     */
    data class Error(
        val message: String,
    ) : OnboardingUiState
}
