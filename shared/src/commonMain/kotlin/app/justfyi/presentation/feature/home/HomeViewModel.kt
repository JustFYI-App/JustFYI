package app.justfyi.presentation.feature.home

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justfyi.di.AppScope
import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.repository.BleRepository
import app.justfyi.domain.repository.BluetoothState
import app.justfyi.domain.repository.NotificationRepository
import app.justfyi.domain.repository.UserRepository
import app.justfyi.domain.usecase.RecordInteractionUseCase
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen.
 * Manages nearby users, user selection, and interaction recording.
 *
 * Dependencies (injected via Metro DI):
 * - BleRepository: Provides nearby user discovery via BLE
 * - RecordInteractionUseCase: Records interactions with selected users
 * - NotificationRepository: Provides unread notification count
 * - UserRepository: Observes user auth state to clear cached data on logout
 */
@SingleIn(AppScope::class)
@Inject
class HomeViewModel(
    private val bleRepository: BleRepository,
    private val recordInteractionUseCase: RecordInteractionUseCase,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _nearbyUsers = MutableStateFlow<List<NearbyUser>>(emptyList())

    private val _selectedUsers = MutableStateFlow<Set<String>>(emptySet())

    private val _unreadNotificationCount = MutableStateFlow(0)

    private val _bluetoothState = MutableStateFlow(BluetoothState.OFF)
    val bluetoothState: StateFlow<BluetoothState> = _bluetoothState

    private val _isLoading = MutableStateFlow(false)
    private val _isRecording = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _recordingSuccess = MutableStateFlow(false)
    private val _lastRecordedCount = MutableStateFlow(0)

    // Track if initial discovery has been done to prevent loading flash on return
    private var hasCompletedInitialDiscovery = false

    /**
     * Combined UI state using sealed interface pattern.
     * Uses stateIn with WhileSubscribed(5000) for proper lifecycle handling.
     */
    val uiState: StateFlow<HomeUiState> =
        combine(
            _nearbyUsers,
            _selectedUsers,
            _unreadNotificationCount,
            _isLoading,
            _isRecording,
            _error,
            _recordingSuccess,
            _lastRecordedCount,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val nearbyUsers = values[0] as List<NearbyUser>

            @Suppress("UNCHECKED_CAST")
            val selectedUsers = values[1] as Set<String>
            val unreadCount = values[2] as Int
            val isLoading = values[3] as Boolean
            val isRecording = values[4] as Boolean
            val error = values[5] as String?
            val recordingSuccess = values[6] as Boolean
            val lastRecordedCount = values[7] as Int

            when {
                error != null -> HomeUiState.Error(error)
                isLoading -> HomeUiState.Loading
                else ->
                    HomeUiState.Success(
                        nearbyUsers = nearbyUsers,
                        selectedUsers = selectedUsers,
                        unreadNotificationCount = unreadCount,
                        isRecording = isRecording,
                        recordingSuccess = recordingSuccess,
                        lastRecordedCount = lastRecordedCount,
                    )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            // Use Success with empty data as initial value to prevent flash
            // The actual loading state will be shown via the combine flow if needed
            initialValue =
                HomeUiState.Success(
                    nearbyUsers = emptyList(),
                    selectedUsers = emptySet(),
                    unreadNotificationCount = 0,
                    isRecording = false,
                    recordingSuccess = false,
                    lastRecordedCount = 0,
                ),
        )

    init {
        observeNearbyUsers()
        observeUnreadCount()
        observeBluetoothState()
        observeUserState()
        syncNotifications()
    }

    private fun observeNearbyUsers() {
        viewModelScope.launch {
            bleRepository
                .getNearbyUsers()
                .catch { e ->
                    _error.value = e.message ?: "Failed to scan for nearby users"
                }.collect { users ->
                    _nearbyUsers.value = users.sortedByDescending { it.signalStrength }
                    _isLoading.value = false
                    _error.value = null
                }
        }
    }

    private fun observeUnreadCount() {
        viewModelScope.launch {
            notificationRepository
                .getUnreadCount()
                .catch { /* Ignore notification count errors */ }
                .collect { count ->
                    _unreadNotificationCount.value = count
                }
        }
    }

    /**
     * Syncs notifications from cloud and starts realtime sync.
     * This ensures the notification badge shows the correct count on app launch.
     */
    private fun syncNotifications() {
        viewModelScope.launch {
            // Sync existing notifications from cloud
            notificationRepository.syncFromCloudForCurrentUser()
            // Start realtime sync for live updates
            notificationRepository.startRealtimeSyncForCurrentUser()
        }
    }

    private fun observeBluetoothState() {
        viewModelScope.launch {
            bleRepository.bluetoothState.collect { state ->
                _bluetoothState.value = state
            }
        }
    }

    /**
     * Observes user auth state and clears cached data when user logs out.
     * This ensures stale data is not shown when a new user signs in.
     */
    private fun observeUserState() {
        viewModelScope.launch {
            userRepository.observeCurrentUser().collect { user ->
                if (user == null) {
                    clearAllState()
                }
            }
        }
    }

    /**
     * Starts BLE discovery (advertising and scanning).
     * Should be called when the screen becomes visible.
     *
     * Note: Only shows loading state on first-ever discovery.
     * This prevents screen blinking when resuming from background or returning to screen.
     */
    fun startDiscovery() {
        viewModelScope.launch {
            // Only show loading on first discovery, never on subsequent calls
            // This prevents the screen from blinking when navigating back
            if (!hasCompletedInitialDiscovery) {
                _isLoading.value = true
            }
            _error.value = null

            val result = bleRepository.startDiscovery()
            result.onFailure { e ->
                _isLoading.value = false
                hasCompletedInitialDiscovery = true
                _error.value = e.message ?: "Failed to start BLE discovery"
            }
            result.onSuccess {
                _isLoading.value = false
                hasCompletedInitialDiscovery = true
                _error.value = null
            }
        }
    }

    /**
     * Stops BLE discovery.
     * Should be called when the screen is no longer visible.
     */
    fun stopDiscovery() {
        viewModelScope.launch {
            bleRepository.stopDiscovery()
        }
    }

    /**
     * Toggles selection state of a user.
     *
     * @param userId The ID of the user to toggle
     */
    fun toggleUserSelection(userId: String) {
        _selectedUsers.value =
            _selectedUsers.value.let { currentSelection ->
                if (currentSelection.contains(userId)) {
                    currentSelection - userId
                } else {
                    currentSelection + userId
                }
            }
    }

    /**
     * Selects all currently visible nearby users.
     */
    fun selectAllUsers() {
        _selectedUsers.value = _nearbyUsers.value.map { it.anonymousIdHash }.toSet()
    }

    /**
     * Clears all selections.
     */
    fun clearSelection() {
        _selectedUsers.value = emptySet()
    }

    /**
     * Records interactions with all selected users.
     */
    fun recordSelectedInteractions() {
        val selectedUserIds = _selectedUsers.value
        if (selectedUserIds.isEmpty()) return

        val usersToRecord = _nearbyUsers.value.filter { it.anonymousIdHash in selectedUserIds }
        if (usersToRecord.isEmpty()) return

        viewModelScope.launch {
            _isRecording.value = true
            _error.value = null

            val result = recordInteractionUseCase.recordInteractions(usersToRecord)

            result.onSuccess { interactions ->
                _isRecording.value = false
                _recordingSuccess.value = true
                _lastRecordedCount.value = interactions.size
                clearSelection()
            }
            result.onFailure { e ->
                _isRecording.value = false
                _error.value = e.message ?: "Failed to record interactions"
            }
        }
    }

    /**
     * Clears the recording success state.
     * Call after showing success message to user.
     */
    fun clearRecordingSuccess() {
        _recordingSuccess.value = false
        _lastRecordedCount.value = 0
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Checks if all required BLE permissions are granted.
     */
    fun hasRequiredPermissions(): Boolean = bleRepository.hasRequiredPermissions()

    /**
     * Gets the list of required permissions for BLE.
     */
    fun getRequiredPermissions(): List<String> = bleRepository.getRequiredPermissions()

    /**
     * Checks if BLE is supported on this device.
     */
    fun isBleSupported(): Boolean = bleRepository.isBleSupported()

    /**
     * Clears all cached state.
     * Called automatically when user logs out (observeUserState detects null user).
     */
    private fun clearAllState() {
        _nearbyUsers.value = emptyList()
        _selectedUsers.value = emptySet()
        _unreadNotificationCount.value = 0
        _isLoading.value = false
        _isRecording.value = false
        _error.value = null
        _recordingSuccess.value = false
        _lastRecordedCount.value = 0
        hasCompletedInitialDiscovery = false
    }
}

/**
 * UI state for the Home screen using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface HomeUiState {
    /**
     * Loading state - shown while scanning for nearby users.
     */
    data object Loading : HomeUiState

    /**
     * Success state - contains nearby users and related data.
     */
    data class Success(
        val nearbyUsers: List<NearbyUser>,
        val selectedUsers: Set<String>,
        val unreadNotificationCount: Int,
        val isRecording: Boolean,
        val recordingSuccess: Boolean,
        val lastRecordedCount: Int,
    ) : HomeUiState

    /**
     * Error state - contains error message.
     */
    data class Error(
        val message: String,
    ) : HomeUiState
}
