package app.justfyi.presentation.feature.notifications

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justfyi.domain.model.ChainVisualization
import app.justfyi.domain.model.Notification
import app.justfyi.domain.model.TestStatus
import app.justfyi.domain.repository.NotificationRepository
import app.justfyi.util.Logger
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Notification Detail screen.
 * Manages a single notification's details and chain visualization.
 *
 * This ViewModel requires a runtime parameter (notificationId) that cannot be
 * injected via Metro DI. It uses the Factory pattern to combine DI dependencies
 * with runtime parameters.
 *
 * Dependencies (injected via Factory):
 * - NotificationRepository: Provides notification data operations
 *
 * Runtime Parameters (passed to Factory.create):
 * - notificationId: The ID of the notification to display
 */
class NotificationDetailViewModel(
    private val notificationRepository: NotificationRepository,
    private val notificationId: String,
) : ViewModel() {
    /**
     * Factory for creating NotificationDetailViewModel instances with runtime parameters.
     *
     * This factory is injected via Metro DI and provides a create() method that
     * takes the runtime notificationId parameter. This follows the pattern used
     * in RevenueCat's cat-paywalls-kmp for ViewModels with runtime dependencies.
     *
     * Usage in composables:
     * ```
     * val factory = appGraph.notificationDetailViewModelFactory
     * val viewModel = viewModel { factory.create(notificationId) }
     * ```
     */
    @Inject
    class Factory(
        private val notificationRepository: NotificationRepository,
    ) {
        /**
         * Creates a NotificationDetailViewModel with the given notification ID.
         *
         * @param notificationId The ID of the notification to display
         * @return A new NotificationDetailViewModel instance
         */
        fun create(notificationId: String): NotificationDetailViewModel =
            NotificationDetailViewModel(
                notificationRepository = notificationRepository,
                notificationId = notificationId,
            )
    }

    // Internal state flows
    private val _notification = MutableStateFlow<Notification?>(null)
    private val _chainVisualization = MutableStateFlow<ChainVisualization?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _isMarkingTested = MutableStateFlow(false)
    private val _markedAsTested = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * Combined UI state using sealed interface pattern.
     * Uses stateIn with WhileSubscribed(5000) for proper lifecycle handling.
     */
    val uiState: StateFlow<NotificationDetailUiState> =
        combine(
            _notification,
            _chainVisualization,
            _isLoading,
            _isMarkingTested,
            _markedAsTested,
            _error,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val notification = values[0] as Notification?
            val chainVisualization = values[1] as ChainVisualization?
            val isLoading = values[2] as Boolean
            val isMarkingTested = values[3] as Boolean
            val markedAsTested = values[4] as Boolean
            val error = values[5] as String?

            when {
                error != null && notification == null -> NotificationDetailUiState.Error(error)
                isLoading -> NotificationDetailUiState.Loading
                notification != null ->
                    NotificationDetailUiState.Success(
                        notification = notification,
                        chainVisualization = chainVisualization,
                        isMarkingTested = isMarkingTested,
                        markedAsTested = markedAsTested,
                        error = error,
                    )
                else -> NotificationDetailUiState.Error("Notification not found")
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NotificationDetailUiState.Loading,
        )

    init {
        loadNotification()
    }

    private fun loadNotification() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Try to load from local database first
                var notif = notificationRepository.getNotificationById(notificationId)

                // If not found locally, fetch directly from Firestore
                // This handles the case when opening from a notification tap before sync completes
                if (notif == null) {
                    Logger.d(TAG, "Notification not found locally, fetching from cloud...")
                    notif = notificationRepository.fetchNotificationFromCloud(notificationId)
                }

                if (notif != null) {
                    _notification.value = notif

                    // Parse chain data for visualization
                    val chain = ChainVisualization.fromJson(notif.chainData)
                    _chainVisualization.value = chain

                    // Mark as read when viewing
                    if (!notif.isRead) {
                        notificationRepository.markAsRead(notificationId)
                    }

                    _isLoading.value = false
                    _error.value = null
                } else {
                    _isLoading.value = false
                    _error.value = "Notification not found"
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to load notification"
            }
        }
    }

    /**
     * Submits a negative test result for this notification's STI.
     * Updates the chain data to show reduced risk for others in the chain.
     */
    fun submitNegativeResult() {
        viewModelScope.launch {
            _isMarkingTested.value = true

            try {
                val notification = _notification.value
                val stiType = notification?.stiType

                if (stiType == null) {
                    Logger.w(TAG, "No STI type found for notification")
                    _isMarkingTested.value = false
                    _error.value = "No STI type found for this notification"
                    return@launch
                }

                Logger.d(TAG, "Submitting negative result for STI: $stiType")

                // Submit negative result to repository
                val success =
                    notificationRepository.submitNegativeResult(
                        notificationId = notificationId,
                        stiType = stiType,
                    )

                if (success) {
                    // Update chain visualization to show current user tested negative
                    _chainVisualization.value?.let { currentChain ->
                        _chainVisualization.value = currentChain.withCurrentUserStatus(TestStatus.NEGATIVE)
                    }
                    _isMarkingTested.value = false
                    _markedAsTested.value = true
                    Logger.d(TAG, "Negative result submitted successfully")
                } else {
                    _isMarkingTested.value = false
                    _error.value = "Failed to submit test result"
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error submitting negative result: ${e.message}")
                _isMarkingTested.value = false
                _error.value = e.message ?: "Failed to submit test result"
            }
        }
    }

    companion object {
        private const val TAG = "NotificationDetailVM"
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Refreshes the notification details.
     */
    fun refresh() {
        loadNotification()
    }
}

/**
 * UI state for the Notification Detail screen using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface NotificationDetailUiState {
    /**
     * Loading state - shown while loading notification details.
     */
    data object Loading : NotificationDetailUiState

    /**
     * Success state - contains notification details.
     */
    data class Success(
        val notification: Notification,
        val chainVisualization: ChainVisualization?,
        val isMarkingTested: Boolean,
        val markedAsTested: Boolean,
        val error: String? = null,
    ) : NotificationDetailUiState

    /**
     * Error state - contains error message.
     */
    data class Error(
        val message: String,
    ) : NotificationDetailUiState
}
