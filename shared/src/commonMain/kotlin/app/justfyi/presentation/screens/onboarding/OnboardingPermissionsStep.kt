package app.justfyi.presentation.screens.onboarding

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.platform.isBluetoothPermissionGranted
import app.justfyi.platform.isNotificationPermissionGranted
import app.justfyi.platform.rememberBluetoothPermissionLauncher
import app.justfyi.platform.rememberNotificationPermissionLauncher
import app.justfyi.presentation.components.JustFyiButton
import app.justfyi.presentation.components.JustFyiButtonVariant
import app.justfyi.presentation.components.JustFyiCard
import app.justfyi.presentation.components.JustFyiDialog
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Step 3: Permissions
 * Request Bluetooth and notification permissions.
 */
@Composable
fun PermissionsStep(
    bluetoothGranted: Boolean,
    notificationGranted: Boolean,
    onBluetoothResult: (Boolean) -> Unit,
    onNotificationResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSkipWarning by remember { mutableStateOf(false) }

    // Check if permissions are already granted when step loads
    val currentBluetoothGranted = isBluetoothPermissionGranted()
    val currentNotificationGranted = isNotificationPermissionGranted()

    // Update ViewModel with actual permission state on first composition
    LaunchedEffect(Unit) {
        if (currentBluetoothGranted && !bluetoothGranted) {
            onBluetoothResult(true)
        }
        if (currentNotificationGranted && !notificationGranted) {
            onNotificationResult(true)
        }
    }

    // Bluetooth permission launcher
    val bluetoothPermissionLauncher =
        rememberBluetoothPermissionLauncher { granted ->
            onBluetoothResult(granted)
        }

    // Notification permission launcher
    val notificationPermissionLauncher =
        rememberNotificationPermissionLauncher { granted ->
            onNotificationResult(granted)
        }

    // Skip warning dialog
    if (showSkipWarning) {
        JustFyiDialog(
            title = stringResource(Res.string.onboarding_limited_functionality_title),
            message = stringResource(Res.string.onboarding_limited_functionality_message),
            confirmText = stringResource(Res.string.onboarding_continue_anyway),
            dismissText = stringResource(Res.string.onboarding_grant_permissions),
            onConfirm = { showSkipWarning = false },
            onDismiss = { showSkipWarning = false },
        )
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(Res.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_permissions_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bluetooth Permission Card
        PermissionCard(
            title = stringResource(Res.string.onboarding_permission_bluetooth),
            description = stringResource(Res.string.onboarding_permission_bluetooth_description),
            isGranted = bluetoothGranted,
            onRequestPermission = {
                bluetoothPermissionLauncher.launch()
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Notification Permission Card
        PermissionCard(
            title = stringResource(Res.string.onboarding_permission_notifications),
            description = stringResource(Res.string.onboarding_permission_notifications_description),
            isGranted = notificationGranted,
            onRequestPermission = {
                notificationPermissionLauncher.launch()
            },
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Info card about skipping
        if (!bluetoothGranted || !notificationGranted) {
            PermissionsInfoCard()
        }
    }
}

@Composable
internal fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JustFyiCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGranted) "OK" else "!",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isGranted) {
                    Spacer(modifier = Modifier.height(12.dp))
                    JustFyiButton(
                        text = stringResource(Res.string.permission_grant),
                        onClick = onRequestPermission,
                        variant = JustFyiButtonVariant.PRIMARY,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_permissions_skip_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun PermissionCardGrantedPreview() {
    MaterialTheme {
        PermissionCard(
            title = "Bluetooth",
            description = "Required for discovering nearby users",
            isGranted = true,
            onRequestPermission = {},
        )
    }
}

@Preview
@Composable
private fun PermissionCardNotGrantedPreview() {
    MaterialTheme {
        PermissionCard(
            title = "Notifications",
            description = "Required for receiving exposure alerts",
            isGranted = false,
            onRequestPermission = {},
        )
    }
}
