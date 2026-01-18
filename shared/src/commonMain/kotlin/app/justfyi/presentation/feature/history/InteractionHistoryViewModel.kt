package app.justfyi.presentation.feature.history

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justfyi.domain.model.Interaction
import app.justfyi.domain.usecase.InteractionHistoryUseCase
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * ViewModel for the Interaction History screen.
 * Manages the list of recorded interactions.
 *
 * Dependencies (injected via Metro DI):
 * - InteractionHistoryUseCase: Provides interaction history data and retention logic
 */
@Inject
class InteractionHistoryViewModel(
    private val interactionHistoryUseCase: InteractionHistoryUseCase,
) : ViewModel() {
    private val _interactions = MutableStateFlow<List<Interaction>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * Combined UI state using sealed interface pattern.
     * Uses stateIn with WhileSubscribed(5000) for proper lifecycle handling.
     */
    val uiState: StateFlow<InteractionHistoryUiState> =
        combine(
            _interactions,
            _isLoading,
            _error,
        ) { interactions, isLoading, error ->
            when {
                error != null -> InteractionHistoryUiState.Error(error)
                isLoading -> InteractionHistoryUiState.Loading
                else -> InteractionHistoryUiState.Success(interactions = interactions)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = InteractionHistoryUiState.Loading,
        )

    init {
        loadInteractions()
    }

    private fun loadInteractions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            interactionHistoryUseCase
                .getInteractionHistory()
                .catch { e ->
                    _isLoading.value = false
                    _error.value = e.message ?: "Failed to load interactions"
                }.collect { interactionList ->
                    // Sort by date descending (newest first)
                    _interactions.value = interactionList.sortedByDescending { it.recordedAt }
                    _isLoading.value = false
                    _error.value = null
                }
        }
    }

    /**
     * Gets interactions grouped by date for section headers.
     */
    fun getInteractionsGroupedByDate(): Map<String, List<Interaction>> =
        _interactions.value.groupBy { interaction ->
            formatDateHeader(interaction.recordedAt)
        }

    /**
     * Refreshes the interaction list.
     */
    fun refresh() {
        loadInteractions()
    }

    /**
     * Calculates days until interaction expires (120-day retention).
     *
     * @param recordedAt The timestamp when interaction was recorded
     * @return Days remaining until expiration
     */
    fun getDaysUntilExpiry(recordedAt: Long): Int {
        val now = Clock.System.now().toEpochMilliseconds()
        val retentionPeriodMs = InteractionHistoryUseCase.RETENTION_DAYS.toLong() * 24 * 60 * 60 * 1000
        val expiryTime = recordedAt + retentionPeriodMs
        val remainingMs = expiryTime - now
        return (remainingMs / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    }

    /**
     * Formats the interaction timestamp for display.
     *
     * @param timestamp The timestamp in milliseconds
     * @return Formatted date and time string
     */
    fun formatInteractionDateTime(timestamp: Long): InteractionDateTime {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        val date =
            buildString {
                append(localDateTime.day)
                append(" ")
                append(getMonthName(localDateTime.month.ordinal + 1))
                append(" ")
                append(localDateTime.year)
            }

        val time =
            buildString {
                append(localDateTime.hour.toString().padStart(2, '0'))
                append(":")
                append(localDateTime.minute.toString().padStart(2, '0'))
            }

        return InteractionDateTime(date, time)
    }

    /**
     * Formats the date for section headers.
     */
    private fun formatDateHeader(timestamp: Long): String {
        val now = Clock.System.now()
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val today =
            Instant
                .fromEpochMilliseconds(now.toEpochMilliseconds())
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        val interactionDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date

        return when {
            interactionDate == today -> "Today"
            interactionDate.toEpochDays() == today.toEpochDays() - 1 -> "Yesterday"
            else ->
                buildString {
                    append(interactionDate.day)
                    append(" ")
                    append(getMonthName(interactionDate.month.ordinal + 1))
                    append(" ")
                    append(interactionDate.year)
                }
        }
    }

    private fun getMonthName(month: Int): String =
        when (month) {
            1 -> "Jan"
            2 -> "Feb"
            3 -> "Mar"
            4 -> "Apr"
            5 -> "May"
            6 -> "Jun"
            7 -> "Jul"
            8 -> "Aug"
            9 -> "Sep"
            10 -> "Oct"
            11 -> "Nov"
            12 -> "Dec"
            else -> ""
        }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }
}

/**
 * UI state for the Interaction History screen using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface InteractionHistoryUiState {
    /**
     * Loading state - shown while loading interactions.
     */
    data object Loading : InteractionHistoryUiState

    /**
     * Success state - contains interaction list.
     */
    data class Success(
        val interactions: List<Interaction>,
    ) : InteractionHistoryUiState

    /**
     * Error state - contains error message.
     */
    data class Error(
        val message: String,
    ) : InteractionHistoryUiState
}

/**
 * Data class for formatted interaction date and time.
 */
data class InteractionDateTime(
    val date: String,
    val time: String,
)
