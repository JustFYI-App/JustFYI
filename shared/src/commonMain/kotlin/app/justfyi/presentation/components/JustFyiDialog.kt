package app.justfyi.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.justfyi.domain.usecase.UsernameConstants
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * A styled dialog component for the Just FYI app.
 * Provides consistent dialog styling throughout the app.
 *
 * @param title The dialog title
 * @param message The dialog message/body text
 * @param confirmText Text for the confirm button
 * @param onConfirm Callback when confirm button is clicked
 * @param onDismiss Callback when dialog is dismissed (back button or outside click)
 * @param modifier Modifier for the dialog
 * @param dismissText Optional text for dismiss button (if null, no dismiss button shown)
 * @param isDestructive Whether the confirm action is destructive (shows in error color)
 */
@Composable
fun JustFyiDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    isDestructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors =
                    if (isDestructive) {
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
            ) {
                Text(confirmText)
            }
        },
        dismissButton =
            if (dismissText != null) {
                {
                    TextButton(onClick = onDismiss) {
                        Text(dismissText)
                    }
                }
            } else {
                null
            },
    )
}

/**
 * Just FYI-styled input dialog for text entry.
 *
 * @param title Dialog title
 * @param message Optional message/description
 * @param placeholder Placeholder text for input field
 * @param initialValue Initial value for input
 * @param confirmText Text for confirm button
 * @param dismissText Text for dismiss button
 * @param onConfirm Callback with entered text when confirmed
 * @param onDismiss Callback when dialog is dismissed
 * @param validation Optional validation function returning error message or null
 * @param filterInput Whether to filter input to ASCII only and enforce max length (default: true for username dialogs)
 */
@Composable
fun JustFyiInputDialog(
    title: String,
    message: String? = null,
    placeholder: String = "",
    initialValue: String = "",
    confirmText: String,
    dismissText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    validation: ((String) -> String?)? = null,
    filterInput: Boolean = true,
) {
    var inputValue by remember { mutableStateOf(initialValue) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { newValue ->
                        // Filter to ASCII only and enforce max length if filterInput is enabled
                        val filteredValue = if (filterInput) {
                            UsernameConstants.filterUsernameInput(newValue)
                        } else {
                            newValue
                        }
                        inputValue = filteredValue
                        errorMessage = validation?.invoke(filteredValue)
                    },
                    placeholder = { Text(placeholder) },
                    isError = errorMessage != null,
                    supportingText =
                        errorMessage?.let { error ->
                            { Text(error, color = MaterialTheme.colorScheme.error) }
                        },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val error = validation?.invoke(inputValue)
                    if (error == null) {
                        onConfirm(inputValue)
                    } else {
                        errorMessage = error
                    }
                },
                enabled = errorMessage == null && inputValue.isNotEmpty(),
            ) {
                Text(
                    text = confirmText,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/**
 * Just FYI-styled confirmation dialog for ID backup prompt.
 *
 * @param anonymousId The ID to display for backup
 * @param onConfirmBackup Callback when user confirms backup
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun JustFyiIdBackupDialog(
    anonymousId: String,
    onConfirmBackup: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.backup_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.backup_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.backup_your_id),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = anonymousId,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmBackup) {
                Text(
                    text = stringResource(Res.string.backup_confirm),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.backup_remind_later),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
