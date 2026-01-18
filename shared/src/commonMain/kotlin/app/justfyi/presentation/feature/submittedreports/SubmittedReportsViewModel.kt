package app.justfyi.presentation.feature.submittedreports

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justfyi.domain.model.ExposureReport
import app.justfyi.domain.repository.ExposureReportRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Submitted Reports screen.
 * Manages the list of user's submitted exposure reports and deletion actions.
 *
 * Dependencies (injected via Metro DI):
 * - ExposureReportRepository: Provides report data and deletion functionality
 */
@Inject
class SubmittedReportsViewModel(
    private val exposureReportRepository: ExposureReportRepository,
) : ViewModel() {
    private val _reports = MutableStateFlow<List<ExposureReport>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * Tracks which reports are currently being deleted.
     * Key: reportId, Value: true if deletion is in progress.
     */
    private val _deletingReports = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /**
     * Combined UI state using sealed interface pattern.
     * Uses stateIn with WhileSubscribed(5000) for proper lifecycle handling.
     */
    val uiState: StateFlow<SubmittedReportsUiState> =
        combine(
            _reports,
            _isLoading,
            _error,
            _deletingReports,
        ) { reports, isLoading, error, deletingReports ->
            when {
                error != null -> SubmittedReportsUiState.Error(error)
                isLoading -> SubmittedReportsUiState.Loading
                reports.isEmpty() -> SubmittedReportsUiState.Empty
                else ->
                    SubmittedReportsUiState.Success(
                        reports = reports,
                        deletingReports = deletingReports,
                    )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SubmittedReportsUiState.Loading,
        )

    init {
        loadReports()
    }

    /**
     * Loads all submitted reports from the repository.
     * First syncs from cloud to ensure we have the latest data,
     * then loads from local database.
     * Sorts by reportedAt descending (newest first).
     */
    private fun loadReports() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Sync from cloud first to ensure we have the latest data
                exposureReportRepository.syncReportsFromCloud()

                val reportList = exposureReportRepository.getAllReports()
                // Sort by reportedAt descending (newest first)
                _reports.value = reportList.sortedByDescending { it.reportedAt }
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to load reports"
            }
        }
    }

    /**
     * Deletes a specific report.
     * Sets deletion-in-progress state, calls repository, and updates UI accordingly.
     *
     * @param reportId The unique identifier of the report to delete
     */
    fun deleteReport(reportId: String) {
        viewModelScope.launch {
            // Set deletion-in-progress state for this report
            _deletingReports.value = _deletingReports.value + (reportId to true)
            _error.value = null

            val result = exposureReportRepository.deleteReport(reportId)

            result.onSuccess {
                // Remove the report from the list
                _reports.value = _reports.value.filter { it.id != reportId }
                // Clear deletion state for this report
                _deletingReports.value = _deletingReports.value - reportId
            }

            result.onFailure { e ->
                // Clear deletion state for this report
                _deletingReports.value = _deletingReports.value - reportId
                // Show error
                _error.value = e.message ?: "Failed to delete report"
            }
        }
    }

    /**
     * Refreshes the report list.
     * Follows pattern from InteractionHistoryViewModel.refresh().
     */
    fun refresh() {
        loadReports()
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }
}

/**
 * UI state for the Submitted Reports screen using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface SubmittedReportsUiState {
    /**
     * Loading state - shown while loading reports.
     */
    data object Loading : SubmittedReportsUiState

    /**
     * Success state - contains report list and deletion states.
     *
     * @property reports List of submitted exposure reports sorted by date (newest first)
     * @property deletingReports Map of reportId to deletion-in-progress state
     */
    data class Success(
        val reports: List<ExposureReport>,
        val deletingReports: Map<String, Boolean>,
    ) : SubmittedReportsUiState

    /**
     * Empty state - no reports to display.
     */
    data object Empty : SubmittedReportsUiState

    /**
     * Error state - contains error message.
     */
    data class Error(
        val message: String,
    ) : SubmittedReportsUiState
}
