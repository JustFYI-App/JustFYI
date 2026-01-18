package app.justfyi.presentation.feature.licenses

import androidx.compose.runtime.Stable
import com.mikepenz.aboutlibraries.entity.Library

/**
 * UI state for the Licenses List screen using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface LicensesUiState {
    /**
     * Loading state - shown while loading library information.
     */
    data object Loading : LicensesUiState

    /**
     * Success state - contains the list of libraries.
     *
     * @property libraries The list of libraries sorted alphabetically by name
     */
    data class Success(
        val libraries: List<Library>,
    ) : LicensesUiState

    /**
     * Error state - contains error message.
     *
     * @property message The error message to display
     */
    data class Error(
        val message: String,
    ) : LicensesUiState
}
