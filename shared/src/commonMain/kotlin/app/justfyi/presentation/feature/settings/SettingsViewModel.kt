package app.justfyi.presentation.feature.settings

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.justfyi.domain.repository.BleRepository
import app.justfyi.domain.repository.ExposureReportRepository
import app.justfyi.domain.repository.InteractionRepository
import app.justfyi.domain.repository.NotificationRepository
import app.justfyi.domain.repository.SettingsRepository
import app.justfyi.domain.repository.UserRepository
import app.justfyi.domain.usecase.AuthUseCase
import app.justfyi.domain.usecase.DataExportUseCase
import app.justfyi.platform.LocaleService
import app.justfyi.platform.PlatformContext
import app.justfyi.platform.ShareService
import app.justfyi.platform.ZipService
import app.justfyi.util.Logger
import app.justfyi.util.currentTimeMillis
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 * Manages app settings, language selection, theme selection, GDPR account deletion,
 * and user data export functionality.
 *
 * Dependencies (injected via Metro DI):
 * - SettingsRepository: Persists language, theme, and other settings
 * - AuthUseCase: Authentication operations for sign out
 * - UserRepository: User data for GDPR deletion
 * - InteractionRepository: Interaction data for GDPR deletion
 * - NotificationRepository: Notification data for GDPR deletion
 * - ExposureReportRepository: Exposure report data for GDPR deletion
 * - PlatformContext: Platform-agnostic context operations (intents, package info)
 * - LocaleService: Platform-agnostic locale operations
 * - DataExportUseCase: Exports user data from Firestore via Cloud Function
 * - ZipService: Creates ZIP files from export data
 * - ShareService: Opens native share sheet for file sharing
 */
@Inject
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val authUseCase: AuthUseCase,
    private val userRepository: UserRepository,
    private val interactionRepository: InteractionRepository,
    private val notificationRepository: NotificationRepository,
    private val exposureReportRepository: ExposureReportRepository,
    private val bleRepository: BleRepository,
    private val platformContext: PlatformContext,
    private val localeService: LocaleService,
    private val dataExportUseCase: DataExportUseCase,
    private val zipService: ZipService,
    private val shareService: ShareService,
) : ViewModel() {
    // Internal state flows - Group 1: Core settings
    private val _currentLanguage = MutableStateFlow(LocaleService.Languages.SYSTEM_DEFAULT)
    private val _currentTheme = MutableStateFlow(THEME_SYSTEM)
    private val _appVersion = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)

    /**
     * Whether the platform supports runtime language switching.
     * Android: true, iOS: false
     */
    val supportsLanguageChange: Boolean = localeService.supportsRuntimeLocaleChange

    // Internal state flows - Group 2: Dialog and action states
    private val _showDeleteConfirmation = MutableStateFlow(false)
    private val _showThemeDialog = MutableStateFlow(false)
    private val _isDeleting = MutableStateFlow(false)
    private val _isDeleted = MutableStateFlow(false)
    private val _languageChanged = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    // Internal state flows - Group 3: Export state
    private val _isExporting = MutableStateFlow(false)
    private val _exportError = MutableStateFlow<String?>(null)

    /**
     * Combined UI state using sealed interface pattern.
     * Uses stateIn with WhileSubscribed(5000) for proper lifecycle handling.
     */
    val uiState: StateFlow<SettingsUiState> =
        combine(
            combine(
                _currentLanguage,
                _currentTheme,
                _appVersion,
                _isLoading,
            ) { currentLanguage, currentTheme, appVersion, isLoading ->
                SettingsCoreState(currentLanguage, currentTheme, appVersion, isLoading)
            },
            combine(
                _showDeleteConfirmation,
                _showThemeDialog,
                _isDeleting,
            ) { showDeleteConfirmation, showThemeDialog, isDeleting ->
                Triple(showDeleteConfirmation, showThemeDialog, isDeleting)
            },
            combine(_isDeleted, _languageChanged, _error) { isDeleted, languageChanged, error ->
                Triple(isDeleted, languageChanged, error)
            },
            combine(_isExporting, _exportError) { isExporting, exportError ->
                Pair(isExporting, exportError)
            },
        ) { coreState, dialogState, statusState, exportState ->
            val actionState =
                SettingsActionState(
                    showDeleteConfirmation = dialogState.first,
                    showThemeDialog = dialogState.second,
                    isDeleting = dialogState.third,
                    isDeleted = statusState.first,
                    languageChanged = statusState.second,
                    error = statusState.third,
                    isExporting = exportState.first,
                    exportError = exportState.second,
                )
            when {
                actionState.error != null && coreState.isLoading -> SettingsUiState.Error(actionState.error)
                coreState.isLoading -> SettingsUiState.Loading
                else ->
                    SettingsUiState.Success(
                        currentLanguage = coreState.currentLanguage,
                        currentTheme = coreState.currentTheme,
                        appVersion = coreState.appVersion,
                        showDeleteConfirmation = actionState.showDeleteConfirmation,
                        showThemeDialog = actionState.showThemeDialog,
                        isDeleting = actionState.isDeleting,
                        isDeleted = actionState.isDeleted,
                        languageChanged = actionState.languageChanged,
                        supportsLanguageChange = supportsLanguageChange,
                        error = actionState.error,
                        isExporting = actionState.isExporting,
                        exportError = actionState.exportError,
                    )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState.Loading,
        )

    init {
        loadSettings()
        loadAppVersion()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load language preference
                val language = settingsRepository.getLanguage()
                _currentLanguage.value = language ?: LocaleService.Languages.SYSTEM_DEFAULT

                // Apply the locale setting
                localeService.setAppLocale(language)

                // Load theme preference
                val theme = settingsRepository.getTheme()
                _currentTheme.value = theme

                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = "Failed to load settings"
            }
        }
    }

    private fun loadAppVersion() {
        _appVersion.value = platformContext.getAppVersionName()
    }

    /**
     * Sets the app language.
     * Language is applied immediately without app restart on Android 13+.
     *
     * @param languageCode The language code (e.g., "en", "de", or "system" for device default)
     */
    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            try {
                // Persist the setting
                if (languageCode == LocaleService.Languages.SYSTEM_DEFAULT) {
                    // Clear the override to use system default
                    settingsRepository.setSetting(SettingsRepository.KEY_LANGUAGE_OVERRIDE, "")
                } else {
                    settingsRepository.setLanguage(languageCode)
                }

                // Apply the locale change
                localeService.setAppLocale(
                    if (languageCode == LocaleService.Languages.SYSTEM_DEFAULT) null else languageCode,
                )

                _currentLanguage.value = languageCode
                _languageChanged.value = true
            } catch (e: Exception) {
                _error.value = "Failed to change language"
            }
        }
    }

    /**
     * Sets the app theme preference.
     * Theme changes apply immediately without app restart.
     *
     * @param theme The theme preference ("system", "light", or "dark")
     */
    fun setTheme(theme: String) {
        viewModelScope.launch {
            try {
                // Persist the setting
                settingsRepository.setTheme(theme)

                // Update the state
                _currentTheme.value = theme
            } catch (e: Exception) {
                _error.value = "Failed to change theme"
            }
        }
    }

    /**
     * Shows the theme selection dialog.
     */
    fun showThemeDialog() {
        _showThemeDialog.value = true
    }

    /**
     * Hides the theme selection dialog.
     */
    fun hideThemeDialog() {
        _showThemeDialog.value = false
    }

    /**
     * Gets the display name for the current language.
     */
    fun getLanguageDisplayName(code: String): String =
        localeService.getLanguageDisplayName(
            if (code == LocaleService.Languages.SYSTEM_DEFAULT) null else code,
        )

    /**
     * Opens the privacy policy in a browser.
     */
    fun openPrivacyPolicy() {
        if (!platformContext.openUrl(PRIVACY_POLICY_URL)) {
            _error.value = "Could not open privacy policy"
        }
    }

    /**
     * Opens the terms of service in a browser.
     */
    fun openTermsOfService() {
        if (!platformContext.openUrl(TERMS_OF_SERVICE_URL)) {
            _error.value = "Could not open terms of service"
        }
    }

    /**
     * Opens the open source licenses screen.
     */
    fun openLicenses() {
        if (!platformContext.openUrl(LICENSES_URL)) {
            _error.value = "Could not open licenses"
        }
    }

    /**
     * Shows the delete account confirmation dialog.
     */
    fun showDeleteConfirmation() {
        _showDeleteConfirmation.value = true
    }

    /**
     * Hides the delete account confirmation dialog.
     */
    fun hideDeleteConfirmation() {
        _showDeleteConfirmation.value = false
    }

    /**
     * Deletes the user's account and all associated data.
     * GDPR compliant - removes all local and cloud data.
     */
    fun deleteAccount() {
        viewModelScope.launch {
            _showDeleteConfirmation.value = false
            _isDeleting.value = true

            try {
                // Get current user ID before deletion
                val userId = authUseCase.getCurrentUserId()

                if (userId != null) {
                    // 0. Stop BLE advertising/scanning FIRST to prevent broadcasting old ID
                    bleRepository.stopDiscovery()

                    // 1. Clear in-memory state (nearby users, notification sync)
                    bleRepository.clearNearbyUsers()
                    notificationRepository.stopRealtimeSync()

                    // 2. Delete all local SQLDelight data
                    interactionRepository.deleteAllInteractions()
                    notificationRepository.deleteAllNotifications()
                    exposureReportRepository.deleteAllReports()
                    settingsRepository.deleteAllSettings()

                    // 3. Delete Firestore user documents
                    userRepository.deleteUser(userId)

                    // 4. Sign out (this will also delete FCM token)
                    // Note: HomeViewModel observes user state and clears itself automatically
                    authUseCase.signOut()
                }

                _isDeleting.value = false
                _isDeleted.value = true
            } catch (e: Exception) {
                _isDeleting.value = false
                _error.value = "Failed to delete account: ${e.message}"
            }
        }
    }

    /**
     * Exports all user data from Firestore as a ZIP file and opens the share sheet.
     * GDPR compliant - allows users to download all their server-side data.
     *
     * Flow:
     * 1. Sets isExporting = true
     * 2. Calls DataExportUseCase.exportUserData() to fetch data from Cloud Function
     * 3. Calls ZipService.createZip() to create ZIP file with JSON contents
     * 4. Calls ShareService.shareFile() to open native share sheet
     * 5. Sets isExporting = false in finally block
     *
     * On failure, sets exportError with a user-friendly message.
     */
    fun exportData() {
        viewModelScope.launch {
            _isExporting.value = true
            _exportError.value = null

            try {
                // Step 1: Export user data from Firestore via Cloud Function
                val exportResult = dataExportUseCase.exportUserData()

                exportResult.fold(
                    onSuccess = { exportData ->
                        // Step 2: Create ZIP file with exported data
                        val timestamp = currentTimeMillis()
                        val fileName = "justfyi-export-$timestamp.zip"
                        val fileResult = zipService.createZip(exportData, fileName)

                        // Step 3: Open share sheet with ZIP file
                        val shareSuccess =
                            shareService.shareFile(
                                filePath = fileResult.filePath,
                                mimeType = fileResult.mimeType,
                                fileName = fileName,
                            )

                        if (!shareSuccess) {
                            Logger.e(TAG, "Failed to open share sheet")
                            _exportError.value = EXPORT_ERROR_MESSAGE
                        }
                    },
                    onFailure = { error ->
                        Logger.e(TAG, "Export failed: ${error.message}", error)
                        _exportError.value = EXPORT_ERROR_MESSAGE
                    },
                )
            } catch (e: Exception) {
                Logger.e(TAG, "Export failed with exception: ${e.message}", e)
                _exportError.value = EXPORT_ERROR_MESSAGE
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * Retries the data export after a failure.
     * Clears the export error and initiates a new export.
     */
    fun retryExport() {
        _exportError.value = null
        exportData()
    }

    /**
     * Clears the export error state.
     */
    fun clearExportError() {
        _exportError.value = null
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Clears the language changed flag.
     */
    fun clearLanguageChanged() {
        _languageChanged.value = false
    }

    companion object {
        private const val TAG = "SettingsViewModel"

        const val PRIVACY_POLICY_URL = "https://justfyi.app/privacy"
        const val TERMS_OF_SERVICE_URL = "https://justfyi.app/terms"
        const val LICENSES_URL = "https://justfyi.app/licenses"

        // Export error message - simple user-friendly message
        const val EXPORT_ERROR_MESSAGE = "Export failed. Please try again."

        // Theme constants
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        val SUPPORTED_LANGUAGES =
            listOf(
                LanguageOption(LocaleService.Languages.SYSTEM_DEFAULT, "System Default"),
                LanguageOption(LocaleService.Languages.ENGLISH, "English"),
                LanguageOption(LocaleService.Languages.GERMAN, "Deutsch"),
                LanguageOption(LocaleService.Languages.SPANISH, "Espanol"),
                LanguageOption(LocaleService.Languages.PORTUGUESE, "Portugues"),
                LanguageOption(LocaleService.Languages.FRENCH, "Francais"),
            )

        val THEME_OPTIONS =
            listOf(
                ThemeOption(THEME_SYSTEM, "System Default"),
                ThemeOption(THEME_LIGHT, "Light"),
                ThemeOption(THEME_DARK, "Dark"),
            )
    }
}

/**
 * Represents a language option.
 */
data class LanguageOption(
    val code: String,
    val displayName: String,
)

/**
 * Represents a theme option.
 */
data class ThemeOption(
    val code: String,
    val displayName: String,
)

// Internal data classes for combine operation
private data class SettingsCoreState(
    val currentLanguage: String,
    val currentTheme: String,
    val appVersion: String,
    val isLoading: Boolean,
)

private data class SettingsActionState(
    val showDeleteConfirmation: Boolean,
    val showThemeDialog: Boolean,
    val isDeleting: Boolean,
    val isDeleted: Boolean,
    val languageChanged: Boolean,
    val error: String?,
    val isExporting: Boolean,
    val exportError: String?,
)

/**
 * UI state for the Settings screen using sealed interface pattern.
 * Optimized for Compose recomposition with @Stable annotation.
 */
@Stable
sealed interface SettingsUiState {
    /**
     * Loading state - shown while loading settings.
     */
    data object Loading : SettingsUiState

    /**
     * Success state - contains settings data.
     */
    data class Success(
        val currentLanguage: String,
        val currentTheme: String,
        val appVersion: String,
        val showDeleteConfirmation: Boolean,
        val showThemeDialog: Boolean,
        val isDeleting: Boolean,
        val isDeleted: Boolean,
        val languageChanged: Boolean,
        val supportsLanguageChange: Boolean,
        val error: String? = null,
        val isExporting: Boolean = false,
        val exportError: String? = null,
    ) : SettingsUiState

    /**
     * Error state - contains error message.
     */
    data class Error(
        val message: String,
    ) : SettingsUiState
}
