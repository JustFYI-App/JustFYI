package app.justfyi.presentation.feature.licenses

import androidx.compose.runtime.Stable

/**
 * UI state for the License Detail screen using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface LicenseDetailUiState {
    /**
     * Loading state - shown while loading library details.
     */
    data object Loading : LicenseDetailUiState

    /**
     * Success state - contains the library details.
     *
     * @property name The library name
     * @property version The library version
     * @property author The author or organization name (if available)
     * @property licenseType The license type (e.g., "Apache-2.0", "MIT")
     * @property licenseText The full license text (if available)
     * @property website The library website URL (if available)
     */
    data class Success(
        val name: String,
        val version: String?,
        val author: String?,
        val licenseType: String?,
        val licenseText: String?,
        val website: String?,
    ) : LicenseDetailUiState

    /**
     * Error state - contains error message.
     *
     * @property message The error message to display
     */
    data class Error(
        val message: String,
    ) : LicenseDetailUiState
}
