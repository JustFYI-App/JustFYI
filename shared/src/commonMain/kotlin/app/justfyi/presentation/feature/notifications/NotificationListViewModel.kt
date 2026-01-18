package app.justfyi.presentation.feature.notifications

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justfyi.domain.model.Notification
import app.justfyi.domain.repository.NotificationRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Notification List screen.
 * Manages the list of exposure notifications and read status.
 *
 * Dependencies (injected via Metro DI):
 * - NotificationRepository: Provides notification data and read/unread management
 */
@Inject
class NotificationListViewModel(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * Combined UI state using sealed interface pattern.
     * Uses stateIn with WhileSubscribed(5000) for proper lifecycle handling.
     */
    val uiState: StateFlow<NotificationListUiState> =
        combine(
            _notifications,
            _isLoading,
            _error,
        ) { notifications, isLoading, error ->
            when {
                error != null -> NotificationListUiState.Error(error)
                isLoading -> NotificationListUiState.Loading
                else -> NotificationListUiState.Success(notifications = notifications)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NotificationListUiState.Loading,
        )

    init {
        syncAndObserveNotifications()
    }

    private fun syncAndObserveNotifications() {
        viewModelScope.launch {
            _isLoading.value = true

            // First, sync from cloud to ensure we have latest notifications
            try {
                notificationRepository.syncFromCloudForCurrentUser()
            } catch (e: Exception) {
                // Sync failed, continue with local data
            }

            // Start real-time sync for future updates
            notificationRepository.startRealtimeSyncForCurrentUser()

            // Then observe local database for all notifications
            notificationRepository
                .getNotifications()
                .catch { e ->
                    _isLoading.value = false
                    _error.value = e.message ?: "Failed to load notifications"
                }.collect { notificationList ->
                    // Sort by received date (newest first)
                    _notifications.value = notificationList.sortedByDescending { it.receivedAt }
                    _isLoading.value = false
                    _error.value = null
                }
        }
    }

    /**
     * Marks a notification as read.
     *
     * @param notificationId The ID of the notification to mark as read
     */
    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                notificationRepository.markAsRead(notificationId)
            } catch (e: Exception) {
                _error.value = "Failed to mark notification as read"
            }
        }
    }

    /**
     * Marks all notifications as read.
     */
    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                notificationRepository.markAllAsRead()
            } catch (e: Exception) {
                _error.value = "Failed to mark all notifications as read"
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
     * Refreshes the notification list from the server.
     */
    fun refresh() {
        syncAndObserveNotifications()
    }

    override fun onCleared() {
        super.onCleared()
        // Stop real-time sync when ViewModel is cleared
        notificationRepository.stopRealtimeSync()
    }
}

/**
 * UI state for the Notification List screen using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface NotificationListUiState {
    /**
     * Loading state - shown while loading notifications.
     */
    data object Loading : NotificationListUiState

    /**
     * Success state - contains notification list.
     */
    data class Success(
        val notifications: List<Notification>,
    ) : NotificationListUiState

    /**
     * Error state - contains error message.
     */
    data class Error(
        val message: String,
    ) : NotificationListUiState
}
