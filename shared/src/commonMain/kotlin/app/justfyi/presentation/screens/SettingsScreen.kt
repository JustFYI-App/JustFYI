package app.justfyi.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.platform.LocaleService
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiDialog
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.components.JustFyiTopAppBar
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.presentation.feature.settings.SettingsUiState
import app.justfyi.presentation.feature.settings.SettingsViewModel
import app.justfyi.presentation.navigation.NavigationActions
import app.justfyi.presentation.util.minimumTouchTargetHeight
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Settings screen composable.
 * Displays app settings including language selection, theme selection, data export,
 * and GDPR account deletion.
 *
 * @param viewModel The SettingsViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param onAccountDeleted Callback when account is deleted (navigate to onboarding)
 * @param modifier Modifier for the screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    navigationActions: NavigationActions,
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showLanguageDialog by remember { mutableStateOf(false) }

    // Get localized messages
    val languageChangedMessage = stringResource(Res.string.settings_language_changed)
    val exportErrorMessage = stringResource(Res.string.settings_export_error)
    val retryText = stringResource(Res.string.common_retry)

    // Handle UI state side effects
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SettingsUiState.Success -> {
                // Show error message
                state.error?.let { error ->
                    snackbarHostState.showSnackbar(
                        message = error,
                        duration = SnackbarDuration.Long,
                    )
                    viewModel.clearError()
                }

                // Show language changed message
                if (state.languageChanged) {
                    snackbarHostState.showSnackbar(
                        message = languageChangedMessage,
                        duration = SnackbarDuration.Short,
                    )
                    viewModel.clearLanguageChanged()
                }

                // Show export error with retry action
                state.exportError?.let { error ->
                    val result =
                        snackbarHostState.showSnackbar(
                            message = exportErrorMessage,
                            actionLabel = retryText,
                            duration = SnackbarDuration.Long,
                        )
                    when (result) {
                        SnackbarResult.ActionPerformed -> viewModel.retryExport()
                        SnackbarResult.Dismissed -> viewModel.clearExportError()
                    }
                }

                // Handle account deletion
                if (state.isDeleted) {
                    onAccountDeleted()
                }
            }
            is SettingsUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long,
                )
            }
            else -> {}
        }
    }

    // Delete Confirmation Dialog
    val successState = uiState as? SettingsUiState.Success
    if (successState?.showDeleteConfirmation == true) {
        JustFyiDialog(
            title = stringResource(Res.string.delete_title),
            message = stringResource(Res.string.delete_message),
            confirmText = stringResource(Res.string.delete_confirm),
            dismissText = stringResource(Res.string.common_cancel),
            onConfirm = { viewModel.deleteAccount() },
            onDismiss = { viewModel.hideDeleteConfirmation() },
            isDestructive = true,
        )
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        val currentLanguage =
            (uiState as? SettingsUiState.Success)?.currentLanguage ?: LocaleService.Languages.SYSTEM_DEFAULT
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onLanguageSelected = { languageCode ->
                viewModel.setLanguage(languageCode)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    // Theme Selection Dialog
    if (successState?.showThemeDialog == true) {
        val currentTheme = successState.currentTheme
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { theme ->
                viewModel.setTheme(theme)
                viewModel.hideThemeDialog()
            },
            onDismiss = { viewModel.hideThemeDialog() },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            JustFyiTopAppBar(
                title = stringResource(Res.string.settings_title),
                showNavigationIcon = true,
                onNavigationClick = { navigationActions.navigateBack() },
            )
        },
        snackbarHost = { JustFyiSnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is SettingsUiState.Loading -> {
                    LoadingIndicator(message = stringResource(Res.string.settings_loading))
                }

                is SettingsUiState.Error -> {
                    ErrorMessage(message = state.message)
                }

                is SettingsUiState.Success -> {
                    when {
                        state.isDeleting -> {
                            LoadingIndicator(message = stringResource(Res.string.settings_deleting))
                        }
                        state.isExporting -> {
                            LoadingIndicator(message = stringResource(Res.string.settings_exporting))
                        }
                        else -> {
                            SettingsContent(
                                modifier = Modifier,
                                currentLanguage = state.currentLanguage,
                                currentTheme = state.currentTheme,
                                appVersion = state.appVersion,
                                supportsLanguageChange = state.supportsLanguageChange,
                                isExporting = state.isExporting,
                                onLanguageClick = { showLanguageDialog = true },
                                onThemeClick = { viewModel.showThemeDialog() },
                                onPrivacyPolicyClick = { viewModel.openPrivacyPolicy() },
                                onTermsClick = { viewModel.openTermsOfService() },
                                onLicensesClick = { navigationActions.navigateToLicensesList() },
                                onExportDataClick = { viewModel.exportData() },
                                onDeleteAccountClick = { viewModel.showDeleteConfirmation() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    currentLanguage: String,
    currentTheme: String,
    appVersion: String,
    supportsLanguageChange: Boolean,
    isExporting: Boolean,
    onLanguageClick: () -> Unit,
    onThemeClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onLicensesClick: () -> Unit,
    onExportDataClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Preferences Section (Language & Theme)
        SettingsSection(title = stringResource(Res.string.settings_preferences)) {
            // Only show language option on platforms that support runtime locale changes
            if (supportsLanguageChange) {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(Res.string.settings_language),
                    subtitle = getLanguageDisplayName(currentLanguage),
                    onClick = onLanguageClick,
                    enabled = !isExporting,
                    contentDescription =
                        stringResource(
                            Res.string.settings_language_current,
                            getLanguageDisplayName(currentLanguage),
                        ),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            SettingsItem(
                icon = Icons.Default.BrightnessAuto,
                title = stringResource(Res.string.settings_theme),
                subtitle = getThemeDisplayName(currentTheme),
                onClick = onThemeClick,
                enabled = !isExporting,
                contentDescription =
                    stringResource(
                        Res.string.settings_theme_current,
                        getThemeDisplayName(currentTheme),
                    ),
            )
        }

        // Legal Section
        SettingsSection(title = stringResource(Res.string.settings_legal)) {
            SettingsItem(
                icon = Icons.Default.Policy,
                title = stringResource(Res.string.settings_privacy_policy),
                onClick = onPrivacyPolicyClick,
                enabled = !isExporting,
                contentDescription = stringResource(Res.string.settings_open_privacy_policy),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsItem(
                icon = Icons.Default.Description,
                title = stringResource(Res.string.settings_terms_of_service),
                onClick = onTermsClick,
                enabled = !isExporting,
                contentDescription = stringResource(Res.string.settings_open_terms),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(Res.string.settings_open_source_licenses),
                onClick = onLicensesClick,
                enabled = !isExporting,
                contentDescription = stringResource(Res.string.settings_view_licenses),
            )
        }

        // Account Section
        SettingsSection(title = stringResource(Res.string.settings_account)) {
            // Export My Data - positioned BEFORE Delete Account
            SettingsItem(
                icon = Icons.Default.Download,
                title = stringResource(Res.string.settings_export_data),
                subtitle = stringResource(Res.string.settings_export_data_subtitle),
                onClick = onExportDataClick,
                enabled = !isExporting,
                isDestructive = false,
                contentDescription = stringResource(Res.string.settings_export_data_description),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            // Delete Account
            SettingsItem(
                icon = Icons.Default.Delete,
                title = stringResource(Res.string.settings_delete_account),
                subtitle = stringResource(Res.string.settings_delete_account_subtitle),
                onClick = onDeleteAccountClick,
                enabled = !isExporting,
                isDestructive = true,
                contentDescription = stringResource(Res.string.settings_delete_permanently),
            )
        }

        // App Info - Use outline color for de-emphasized text (WCAG AA compliant)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.settings_version, appVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/**
 * Settings section with a heading title.
 *
 * Accessibility: The section title uses heading() semantics for screen reader navigation.
 * This allows TalkBack/VoiceOver users to navigate between sections using heading navigation.
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .semantics { heading() },
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Column {
                content()
            }
        }
    }
}

/**
 * Settings item row component.
 *
 * Touch target compliance: This item meets WCAG 2.1 AA minimum 48dp touch target
 * through its full-width layout and minimum height enforcement.
 *
 * Accessibility: Uses Role.Button semantics for custom clickable Row elements
 * to ensure screen readers announce this as an interactive button.
 * Focus management: Uses focusable() modifier to enable keyboard navigation
 * via Tab key on hardware keyboards and Android TV.
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    contentDescription: String = title,
) {
    val alpha = if (enabled) 1f else 0.5f

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .minimumTouchTargetHeight()
                .focusable(enabled = enabled)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(16.dp)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint =
                if (isDestructive) {
                    MaterialTheme.colorScheme.error.copy(alpha = alpha)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                },
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (isDestructive) {
                        MaterialTheme.colorScheme.error.copy(alpha = alpha)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    },
            )
            if (subtitle != null) {
                // Use outline color for secondary text to ensure WCAG AA compliance
                // instead of reduced-alpha which may fail contrast requirements
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (isDestructive) {
                            MaterialTheme.colorScheme.error.copy(alpha = alpha)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = alpha)
                        },
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
        )
    }
}

@Composable
private fun LanguageSelectionDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val languages = SettingsViewModel.SUPPORTED_LANGUAGES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.settings_language_select),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                languages.forEach { language ->
                    val selectLanguageDescription =
                        stringResource(Res.string.settings_select_language, language.displayName)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .minimumTouchTargetHeight()
                                .focusable()
                                .clickable { onLanguageSelected(language.code) }
                                .padding(vertical = 12.dp)
                                .semantics {
                                    contentDescription = selectLanguageDescription
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = language.code == currentLanguage,
                            onClick = { onLanguageSelected(language.code) },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = language.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

/**
 * Theme selection dialog composable.
 * Displays a list of theme options with radio buttons.
 *
 * @param currentTheme The currently selected theme code
 * @param onThemeSelected Callback when a theme is selected
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
private fun ThemeSelectionDialog(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val themes = SettingsViewModel.THEME_OPTIONS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.settings_theme_select),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                themes.forEach { theme ->
                    val themeDisplayName = getThemeDisplayName(theme.code)
                    val selectThemeDescription = stringResource(Res.string.settings_select_theme, themeDisplayName)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .minimumTouchTargetHeight()
                                .focusable()
                                .clickable { onThemeSelected(theme.code) }
                                .padding(vertical = 12.dp)
                                .semantics {
                                    contentDescription = selectThemeDescription
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = theme.code == currentTheme,
                            onClick = { onThemeSelected(theme.code) },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = themeDisplayName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

@Composable
private fun getLanguageDisplayName(code: String): String =
    when (code) {
        LocaleService.Languages.SYSTEM_DEFAULT -> stringResource(Res.string.settings_language_system_default)
        LocaleService.Languages.ENGLISH -> stringResource(Res.string.settings_language_english)
        LocaleService.Languages.GERMAN -> stringResource(Res.string.settings_language_german)
        else ->
            SettingsViewModel.SUPPORTED_LANGUAGES
                .find { it.code == code }
                ?.displayName ?: stringResource(Res.string.settings_language_system_default)
    }

/**
 * Returns the localized display name for a theme code.
 *
 * @param code The theme code ("system", "light", or "dark")
 * @return The localized display name
 */
@Composable
private fun getThemeDisplayName(code: String): String =
    when (code) {
        SettingsViewModel.THEME_SYSTEM -> stringResource(Res.string.settings_theme_system_default)
        SettingsViewModel.THEME_LIGHT -> stringResource(Res.string.settings_theme_light)
        SettingsViewModel.THEME_DARK -> stringResource(Res.string.settings_theme_dark)
        else -> stringResource(Res.string.settings_theme_system_default)
    }

// ============== Previews ==============

@Preview
@Composable
private fun SettingsContentPreview() {
    MaterialTheme {
        SettingsContent(
            currentLanguage = LocaleService.Languages.ENGLISH,
            currentTheme = SettingsViewModel.THEME_SYSTEM,
            appVersion = "1.0.0",
            supportsLanguageChange = true,
            isExporting = false,
            onLanguageClick = {},
            onThemeClick = {},
            onPrivacyPolicyClick = {},
            onTermsClick = {},
            onLicensesClick = {},
            onExportDataClick = {},
            onDeleteAccountClick = {},
        )
    }
}

@Preview
@Composable
private fun SettingsItemPreview() {
    MaterialTheme {
        SettingsItem(
            icon = Icons.Default.Language,
            title = "Language",
            subtitle = "English",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun SettingsItemDestructivePreview() {
    MaterialTheme {
        SettingsItem(
            icon = Icons.Default.Delete,
            title = "Delete Account",
            subtitle = "Permanently delete all your data",
            onClick = {},
            isDestructive = true,
        )
    }
}

@Preview
@Composable
private fun SettingsItemExportPreview() {
    MaterialTheme {
        SettingsItem(
            icon = Icons.Default.Download,
            title = "Export My Data",
            subtitle = "Download a copy of your data",
            onClick = {},
            isDestructive = false,
        )
    }
}
