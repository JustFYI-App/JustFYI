package app.justfyi.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justfyi.domain.usecase.UsernameValidationResult
import app.justfyi.presentation.components.AnonymousIdCard
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiButton
import app.justfyi.presentation.components.JustFyiButtonVariant
import app.justfyi.presentation.components.JustFyiCard
import app.justfyi.presentation.components.JustFyiIdBackupDialog
import app.justfyi.presentation.components.JustFyiInputDialog
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.components.JustFyiTopAppBar
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.presentation.feature.profile.ProfileUiState
import app.justfyi.presentation.feature.profile.ProfileViewModel
import app.justfyi.presentation.navigation.NavigationActions
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_account_recovery_section
import justfyi.shared.generated.resources.cd_edit_username
import justfyi.shared.generated.resources.cd_open_settings
import justfyi.shared.generated.resources.cd_view_interaction_history
import justfyi.shared.generated.resources.cd_view_submitted_reports
import justfyi.shared.generated.resources.common_cancel
import justfyi.shared.generated.resources.common_save
import justfyi.shared.generated.resources.profile_account_recovered
import justfyi.shared.generated.resources.profile_account_recovery
import justfyi.shared.generated.resources.profile_account_recovery_description
import justfyi.shared.generated.resources.profile_edit_username
import justfyi.shared.generated.resources.profile_edit_username_description
import justfyi.shared.generated.resources.profile_id_copied
import justfyi.shared.generated.resources.profile_interaction_history
import justfyi.shared.generated.resources.profile_loading
import justfyi.shared.generated.resources.profile_recover_account
import justfyi.shared.generated.resources.profile_recover_button
import justfyi.shared.generated.resources.profile_recover_enter_id
import justfyi.shared.generated.resources.profile_recover_placeholder
import justfyi.shared.generated.resources.profile_save_id_warning
import justfyi.shared.generated.resources.profile_submitted_reports
import justfyi.shared.generated.resources.profile_title
import justfyi.shared.generated.resources.profile_username
import justfyi.shared.generated.resources.profile_username_not_set
import justfyi.shared.generated.resources.profile_username_updated
import justfyi.shared.generated.resources.settings_title
import justfyi.shared.generated.resources.validation_username_empty
import justfyi.shared.generated.resources.validation_username_invalid
import justfyi.shared.generated.resources.validation_username_non_ascii
import justfyi.shared.generated.resources.validation_username_too_long
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Reusable top bar for the Profile screen.
 * Uses stringResource() internally for proper localization.
 */
@Composable
fun ProfileTopBar(onNavigationClick: () -> Unit = {}) {
    JustFyiTopAppBar(
        title = stringResource(Res.string.profile_title),
        showNavigationIcon = true,
        onNavigationClick = onNavigationClick,
    )
}

/**
 * Profile screen composable.
 * Displays user ID, username, and account management options.
 *
 * @param viewModel The ProfileViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param modifier Modifier for the screen
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    navigationActions: NavigationActions,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val showBackupPrompt by viewModel.showBackupPrompt.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }

    // Get localized messages for snackbar
    val idCopiedMessage = stringResource(Res.string.profile_id_copied)
    val usernameUpdatedMessage = stringResource(Res.string.profile_username_updated)
    val accountRecoveredMessage = stringResource(Res.string.profile_account_recovered)

    // Handle UI state side effects
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ProfileUiState.Success -> {
                // Show copied message
                if (state.showCopiedMessage) {
                    snackbarHostState.showSnackbar(
                        message = idCopiedMessage,
                        duration = SnackbarDuration.Short,
                    )
                    viewModel.clearCopiedMessage()
                }

                // Show username updated message
                if (state.showUsernameUpdated) {
                    snackbarHostState.showSnackbar(
                        message = usernameUpdatedMessage,
                        duration = SnackbarDuration.Short,
                    )
                    viewModel.clearUsernameUpdatedMessage()
                }

                // Show recovery success message
                if (state.showRecoverySuccess) {
                    snackbarHostState.showSnackbar(
                        message = accountRecoveredMessage,
                        duration = SnackbarDuration.Short,
                    )
                    viewModel.clearRecoverySuccessMessage()
                }
            }
            is ProfileUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long,
                )
                viewModel.clearError()
            }
            else -> {}
        }
    }

    // Get values from Success state for UI elements that need them regardless of state
    val successState = uiState as? ProfileUiState.Success
    val username = successState?.username ?: ""
    val formattedId = successState?.formattedId ?: ""

    // Localized strings for dialogs
    val editUsernameTitle = stringResource(Res.string.profile_edit_username)
    val editUsernameDescription = stringResource(Res.string.profile_edit_username_description)
    val usernameLabel = stringResource(Res.string.profile_username)
    val saveText = stringResource(Res.string.common_save)
    val cancelText = stringResource(Res.string.common_cancel)
    val recoverAccountTitle = stringResource(Res.string.profile_recover_account)
    val recoverEnterIdMessage = stringResource(Res.string.profile_recover_enter_id)
    val recoverPlaceholder = stringResource(Res.string.profile_recover_placeholder)
    val recoverButtonText = stringResource(Res.string.profile_recover_button)
    val validationEmpty = stringResource(Res.string.validation_username_empty)
    val validationTooLong = stringResource(Res.string.validation_username_too_long, 30)
    val validationNonAscii = stringResource(Res.string.validation_username_non_ascii)
    val validationInvalid = stringResource(Res.string.validation_username_invalid)

    // ID Backup Dialog
    if (showBackupPrompt && formattedId.isNotEmpty()) {
        JustFyiIdBackupDialog(
            anonymousId = formattedId,
            onConfirmBackup = { viewModel.confirmBackup() },
            onDismiss = { viewModel.dismissBackupPrompt() },
        )
    }

    // Username Edit Dialog
    if (showUsernameDialog) {
        JustFyiInputDialog(
            title = editUsernameTitle,
            message = editUsernameDescription,
            placeholder = usernameLabel,
            initialValue = username,
            confirmText = saveText,
            dismissText = cancelText,
            onConfirm = { newUsername ->
                viewModel.updateUsername(newUsername)
                showUsernameDialog = false
            },
            onDismiss = { showUsernameDialog = false },
            validation = { name ->
                when (viewModel.validateUsername(name)) {
                    is UsernameValidationResult.Valid -> null
                    is UsernameValidationResult.Empty -> validationEmpty
                    is UsernameValidationResult.TooLong -> validationTooLong
                    is UsernameValidationResult.NonAsciiCharacters -> validationNonAscii
                    is UsernameValidationResult.NonPrintableCharacters -> validationInvalid
                }
            },
        )
    }

    // Recovery Dialog
    if (showRecoveryDialog) {
        JustFyiInputDialog(
            title = recoverAccountTitle,
            message = recoverEnterIdMessage,
            placeholder = recoverPlaceholder,
            confirmText = recoverButtonText,
            dismissText = cancelText,
            onConfirm = { savedId ->
                viewModel.recoverAccount(savedId)
                showRecoveryDialog = false
            },
            onDismiss = { showRecoveryDialog = false },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ProfileTopBar(onNavigationClick = { navigationActions.navigateBack() }) },
        snackbarHost = { JustFyiSnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is ProfileUiState.Loading -> {
                    LoadingIndicator(message = stringResource(Res.string.profile_loading))
                }

                is ProfileUiState.Error -> {
                    ErrorMessage(message = state.message)
                }

                is ProfileUiState.Success -> {
                    ProfileContent(
                        state = state,
                        warningText = stringResource(Res.string.profile_save_id_warning),
                        onEditUsernameClick = { showUsernameDialog = true },
                        onToggleIdReveal = { viewModel.toggleIdReveal() },
                        onCopyIdClick = { viewModel.copyIdToClipboard() },
                        onHistoryClick = { navigationActions.navigateToInteractionHistory() },
                        onSubmittedReportsClick = { navigationActions.navigateToSubmittedReports() },
                        onSettingsClick = { navigationActions.navigateToSettings() },
                        onRecoverClick = { showRecoveryDialog = true },
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileContent(
    state: ProfileUiState.Success,
    warningText: String,
    onEditUsernameClick: () -> Unit,
    onToggleIdReveal: () -> Unit,
    onCopyIdClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSubmittedReportsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRecoverClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Username Section
        UsernameSection(
            username = state.username,
            onEditClick = onEditUsernameClick,
        )

        // Anonymous ID Section
        AnonymousIdCard(
            formattedId = state.formattedId,
            isRevealed = state.isIdRevealed,
            onToggleReveal = onToggleIdReveal,
            onCopyClick = onCopyIdClick,
            warningText = warningText,
        )

        // Quick Actions
        QuickActionsSection(
            onHistoryClick = onHistoryClick,
            onSubmittedReportsClick = onSubmittedReportsClick,
            onSettingsClick = onSettingsClick,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Account Recovery Section
        AccountRecoverySection(
            isRecovering = state.isRecovering,
            onRecoverClick = onRecoverClick,
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun UsernameSection(
    username: String,
    onEditClick: () -> Unit,
) {
    JustFyiCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.profile_username),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = username.ifEmpty { stringResource(Res.string.profile_username_not_set) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(Res.string.cd_edit_username),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Quick actions section with accessibility descriptions for each action.
 *
 * Accessibility: Each action card has a content description describing its purpose.
 */
@Composable
private fun QuickActionsSection(
    onHistoryClick: () -> Unit,
    onSubmittedReportsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val historyDescription = stringResource(Res.string.cd_view_interaction_history)
    val reportsDescription = stringResource(Res.string.cd_view_submitted_reports)
    val settingsDescription = stringResource(Res.string.cd_open_settings)

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JustFyiCard(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = historyDescription
                    },
            onClick = onHistoryClick,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null, // Description at card level
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.profile_interaction_history),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        JustFyiCard(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = reportsDescription
                    },
            onClick = onSubmittedReportsClick,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null, // Description at card level
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.profile_submitted_reports),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        JustFyiCard(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = settingsDescription
                    },
            onClick = onSettingsClick,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null, // Description at card level
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Account recovery section with heading semantics for screen reader navigation.
 *
 * Accessibility: The section title uses heading semantics, and the entire section
 * has a content description for screen reader users.
 */
@Composable
private fun AccountRecoverySection(
    isRecovering: Boolean,
    onRecoverClick: () -> Unit,
) {
    val sectionDescription = stringResource(Res.string.cd_account_recovery_section)

    Column(
        modifier =
            Modifier.semantics {
                contentDescription = sectionDescription
            },
    ) {
        Text(
            text = stringResource(Res.string.profile_account_recovery),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.profile_account_recovery_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        JustFyiButton(
            text = stringResource(Res.string.profile_recover_account),
            onClick = onRecoverClick,
            variant = JustFyiButtonVariant.SECONDARY,
            isLoading = isRecovering,
            fullWidth = true,
        )
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun ProfileContentPreview() {
    MaterialTheme {
        ProfileContent(
            state =
                ProfileUiState.Success(
                    username = "JohnDoe",
                    anonymousId = "ABC123DEF456GHI789JKL012",
                    formattedId = "ABC1-23DE-F456-GHI7-89JK-L012",
                    isIdRevealed = false,
                    isUpdatingUsername = false,
                    showCopiedMessage = false,
                    showUsernameUpdated = false,
                    isRecovering = false,
                    showRecoverySuccess = false,
                ),
            warningText = "Save this ID to recover your account if you reinstall the app.",
            onEditUsernameClick = {},
            onToggleIdReveal = {},
            onCopyIdClick = {},
            onHistoryClick = {},
            onSubmittedReportsClick = {},
            onSettingsClick = {},
            onRecoverClick = {},
        )
    }
}

@Preview
@Composable
private fun ProfileContentIdRevealedPreview() {
    MaterialTheme {
        ProfileContent(
            state =
                ProfileUiState.Success(
                    username = "JohnDoe",
                    anonymousId = "ABC123DEF456GHI789JKL012",
                    formattedId = "ABC1-23DE-F456-GHI7-89JK-L012",
                    isIdRevealed = true,
                    isUpdatingUsername = false,
                    showCopiedMessage = false,
                    showUsernameUpdated = false,
                    isRecovering = false,
                    showRecoverySuccess = false,
                ),
            warningText = "Save this ID to recover your account if you reinstall the app.",
            onEditUsernameClick = {},
            onToggleIdReveal = {},
            onCopyIdClick = {},
            onHistoryClick = {},
            onSubmittedReportsClick = {},
            onSettingsClick = {},
            onRecoverClick = {},
        )
    }
}
