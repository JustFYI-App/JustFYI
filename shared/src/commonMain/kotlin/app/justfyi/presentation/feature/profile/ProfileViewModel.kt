package app.justfyi.presentation.feature.profile

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justfyi.domain.usecase.AuthUseCase
import app.justfyi.domain.usecase.IdBackupUseCase
import app.justfyi.domain.usecase.UsernameUseCase
import app.justfyi.domain.usecase.UsernameValidationResult
import app.justfyi.platform.ClipboardService
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Profile screen.
 * Manages user ID display, username editing, and account recovery.
 *
 * Dependencies (injected via Metro DI):
 * - AuthUseCase: Authentication and account recovery operations
 * - UsernameUseCase: Username validation and update operations
 * - IdBackupUseCase: ID formatting and backup utilities
 * - ClipboardService: Platform-agnostic clipboard operations
 */
@Inject
class ProfileViewModel(
    private val authUseCase: AuthUseCase,
    private val usernameUseCase: UsernameUseCase,
    private val idBackupUseCase: IdBackupUseCase,
    private val clipboardService: ClipboardService,
) : ViewModel() {
    private val _anonymousId = MutableStateFlow("")
    private val _formattedId = MutableStateFlow("")
    private val _isIdRevealed = MutableStateFlow(false)
    private val _username = MutableStateFlow("")
    private val _showBackupPrompt = MutableStateFlow(false)
    val showBackupPrompt: StateFlow<Boolean> = _showBackupPrompt

    private val _isLoading = MutableStateFlow(true)
    private val _isUpdatingUsername = MutableStateFlow(false)
    private val _isRecovering = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _showCopiedMessage = MutableStateFlow(false)
    private val _showUsernameUpdated = MutableStateFlow(false)
    private val _showRecoverySuccess = MutableStateFlow(false)

    /**
     * Combined UI state using sealed interface pattern.
     * Uses stateIn with WhileSubscribed(5000) for proper lifecycle handling.
     */
    val uiState: StateFlow<ProfileUiState> =
        combine(
            _anonymousId,
            _formattedId,
            _isIdRevealed,
            _username,
            _isLoading,
        ) { anonymousId, formattedId, isIdRevealed, username, isLoading ->
            ProfileDataState(anonymousId, formattedId, isIdRevealed, username, isLoading)
        }.combine(
            combine(
                _isUpdatingUsername,
                _isRecovering,
                _error,
                _showCopiedMessage,
                _showUsernameUpdated,
            ) { isUpdatingUsername, isRecovering, error, showCopiedMessage, showUsernameUpdated ->
                ProfileActionState(isUpdatingUsername, isRecovering, error, showCopiedMessage, showUsernameUpdated)
            },
        ) { dataState, actionState ->
            Pair(dataState, actionState)
        }.combine(_showRecoverySuccess) { (dataState, actionState), showRecoverySuccess ->
            when {
                actionState.error != null -> ProfileUiState.Error(actionState.error)
                dataState.isLoading -> ProfileUiState.Loading
                else ->
                    ProfileUiState.Success(
                        anonymousId = dataState.anonymousId,
                        formattedId = dataState.formattedId,
                        isIdRevealed = dataState.isIdRevealed,
                        username = dataState.username,
                        isUpdatingUsername = actionState.isUpdatingUsername,
                        isRecovering = actionState.isRecovering,
                        showCopiedMessage = actionState.showCopiedMessage,
                        showUsernameUpdated = actionState.showUsernameUpdated,
                        showRecoverySuccess = showRecoverySuccess,
                    )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProfileUiState.Loading,
        )

    init {
        loadUserProfile()
        checkBackupPrompt()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Get current user ID
                val userId = authUseCase.getCurrentUserId()
                if (userId != null) {
                    _anonymousId.value = userId
                    _formattedId.value = idBackupUseCase.formatIdForDisplay(userId)
                }

                // Get current username
                val currentUsername = usernameUseCase.getCurrentUsername()
                _username.value = currentUsername ?: ""

                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to load profile"
            }
        }
    }

    private fun checkBackupPrompt() {
        viewModelScope.launch {
            try {
                val isConfirmed = authUseCase.isIdBackupConfirmed()
                _showBackupPrompt.value = !isConfirmed
            } catch (e: Exception) {
                // Ignore errors, don't show prompt on failure
            }
        }
    }

    /**
     * Toggles the visibility of the anonymous ID.
     */
    fun toggleIdReveal() {
        _isIdRevealed.value = !_isIdRevealed.value
    }

    /**
     * Copies the anonymous ID to clipboard.
     */
    fun copyIdToClipboard() {
        val id = _formattedId.value
        if (id.isNotEmpty()) {
            clipboardService.copyText("Just FYI ID", id)
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
     * Validates a username according to requirements.
     *
     * @param name The username to validate
     * @return Validation result with error message if invalid
     */
    fun validateUsername(name: String): UsernameValidationResult = usernameUseCase.validateUsername(name)

    /**
     * Updates the user's username.
     *
     * @param newName The new username
     */
    fun updateUsername(newName: String) {
        viewModelScope.launch {
            _isUpdatingUsername.value = true
            _error.value = null

            val result = usernameUseCase.setUsername(newName)

            result.onSuccess { finalUsername ->
                _username.value = finalUsername
                _isUpdatingUsername.value = false
                _showUsernameUpdated.value = true
            }
            result.onFailure { e ->
                _isUpdatingUsername.value = false
                _error.value = e.message ?: "Failed to update username"
            }
        }
    }

    /**
     * Clears the username updated message.
     */
    fun clearUsernameUpdatedMessage() {
        _showUsernameUpdated.value = false
    }

    /**
     * Confirms that the user has backed up their ID.
     */
    fun confirmBackup() {
        viewModelScope.launch {
            try {
                authUseCase.confirmIdBackup()
                _showBackupPrompt.value = false
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to confirm backup"
            }
        }
    }

    /**
     * Dismisses the backup prompt temporarily.
     */
    fun dismissBackupPrompt() {
        _showBackupPrompt.value = false
    }

    /**
     * Attempts to recover an account using a saved ID.
     *
     * @param savedId The saved anonymous ID to recover
     */
    fun recoverAccount(savedId: String) {
        viewModelScope.launch {
            _isRecovering.value = true
            _error.value = null

            // Parse and validate the ID format
            val cleanId = idBackupUseCase.parseFormattedId(savedId)
            if (!idBackupUseCase.isValidIdFormat(cleanId)) {
                _isRecovering.value = false
                _error.value = "Invalid ID format. Please check and try again."
                return@launch
            }

            val result = authUseCase.recoverAccount(cleanId)

            result.onSuccess { user ->
                _anonymousId.value = user.anonymousId
                _formattedId.value = idBackupUseCase.formatIdForDisplay(user.anonymousId)
                _username.value = user.username
                _isRecovering.value = false
                _showRecoverySuccess.value = true
            }
            result.onFailure { e ->
                _isRecovering.value = false
                _error.value = e.message ?: "Account recovery failed"
            }
        }
    }

    /**
     * Clears the recovery success message.
     */
    fun clearRecoverySuccessMessage() {
        _showRecoverySuccess.value = false
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }
}

// Internal data classes for combine operation
private data class ProfileDataState(
    val anonymousId: String,
    val formattedId: String,
    val isIdRevealed: Boolean,
    val username: String,
    val isLoading: Boolean,
)

private data class ProfileActionState(
    val isUpdatingUsername: Boolean,
    val isRecovering: Boolean,
    val error: String?,
    val showCopiedMessage: Boolean,
    val showUsernameUpdated: Boolean,
)

/**
 * UI state for the Profile screen using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface ProfileUiState {
    /**
     * Loading state - shown while loading profile data.
     */
    data object Loading : ProfileUiState

    /**
     * Success state - contains profile data.
     */
    data class Success(
        val anonymousId: String,
        val formattedId: String,
        val isIdRevealed: Boolean,
        val username: String,
        val isUpdatingUsername: Boolean,
        val isRecovering: Boolean,
        val showCopiedMessage: Boolean,
        val showUsernameUpdated: Boolean,
        val showRecoverySuccess: Boolean,
    ) : ProfileUiState

    /**
     * Error state - contains error message.
     */
    data class Error(
        val message: String,
    ) : ProfileUiState
}
