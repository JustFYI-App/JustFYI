package app.justfyi.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justfyi.domain.model.NearbyUser
import app.justfyi.platform.isBluetoothPermissionGranted
import app.justfyi.platform.rememberBluetoothPermissionLauncher
import app.justfyi.platform.rememberReduceMotionPreference
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiButton
import app.justfyi.presentation.components.JustFyiButtonVariant
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.components.JustFyiTopAppBar
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.presentation.components.NearbyUserCard
import app.justfyi.presentation.feature.home.HomeUiState
import app.justfyi.presentation.feature.home.HomeViewModel
import app.justfyi.presentation.navigation.NavigationActions
import app.justfyi.presentation.util.reducedEnterTransition
import app.justfyi.util.Logger
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_empty_state_scanning
import justfyi.shared.generated.resources.cd_notifications
import justfyi.shared.generated.resources.cd_permission_required
import justfyi.shared.generated.resources.cd_profile
import justfyi.shared.generated.resources.cd_scanning_animation
import justfyi.shared.generated.resources.home_record_interactions_plural
import justfyi.shared.generated.resources.home_report_positive_test
import justfyi.shared.generated.resources.home_scanning
import justfyi.shared.generated.resources.home_scanning_description
import justfyi.shared.generated.resources.home_title
import justfyi.shared.generated.resources.permission_bluetooth_message
import justfyi.shared.generated.resources.permission_bluetooth_title
import justfyi.shared.generated.resources.permission_grant
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Home screen showing nearby users and allowing interaction recording.
 *
 * @param viewModel The HomeViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param modifier Modifier for the screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    navigationActions: NavigationActions,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Bluetooth permission handling
    val bluetoothPermissionGranted = isBluetoothPermissionGranted()
    val bluetoothPermissionLauncher =
        rememberBluetoothPermissionLauncher { granted ->
            Logger.d("HomeScreen", "Bluetooth permission result: $granted")
            if (granted) {
                viewModel.startDiscovery()
            }
        }

    // Start/stop discovery based on screen visibility
    DisposableEffect(Unit) {
        if (bluetoothPermissionGranted) {
            viewModel.startDiscovery()
        }
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    // Show error messages - keyed on specific error state
    val errorState = (uiState as? HomeUiState.Error)
    LaunchedEffect(errorState) {
        if (errorState != null) {
            snackbarHostState.showSnackbar(
                message = errorState.message,
                duration = SnackbarDuration.Long,
            )
            viewModel.clearError()
        }
    }

    // Show recording success - keyed on specific success flag
    val successState = uiState as? HomeUiState.Success
    val recordingSuccess = successState?.recordingSuccess == true
    val lastRecordedCount = successState?.lastRecordedCount ?: 0
    LaunchedEffect(recordingSuccess) {
        if (recordingSuccess && lastRecordedCount > 0) {
            snackbarHostState.showSnackbar(
                message = "Recorded $lastRecordedCount interaction(s)",
                duration = SnackbarDuration.Short,
            )
            viewModel.clearRecordingSuccess()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            JustFyiTopAppBar(
                title = stringResource(Res.string.home_title),
                actions = {
                    val unreadCount = (uiState as? HomeUiState.Success)?.unreadNotificationCount ?: 0
                    IconButton(onClick = { navigationActions.navigateToNotificationList() }) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge {
                                        Text(
                                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = stringResource(Res.string.cd_notifications),
                            )
                        }
                    }
                    IconButton(onClick = { navigationActions.navigateToProfile() }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(Res.string.cd_profile),
                        )
                    }
                },
            )
        },
        snackbarHost = { JustFyiSnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                LoadingIndicator(
                    modifier = Modifier.padding(paddingValues),
                    message = stringResource(Res.string.home_scanning),
                )
            }

            is HomeUiState.Error -> {
                ErrorMessage(
                    message = state.message,
                    modifier = Modifier.padding(paddingValues),
                    onRetry = { viewModel.startDiscovery() },
                )
            }

            is HomeUiState.Success -> {
                HomeContent(
                    state = state,
                    bluetoothPermissionGranted = bluetoothPermissionGranted,
                    modifier = Modifier.padding(paddingValues),
                    onRequestBluetoothPermission = { bluetoothPermissionLauncher.launch() },
                    onStartScan = { viewModel.startDiscovery() },
                    onUserClick = { userId -> viewModel.toggleUserSelection(userId) },
                    onRecordClick = { viewModel.recordSelectedInteractions() },
                    onReportPositiveTest = { navigationActions.navigateToExposureReport() },
                )
            }
        }
    }
}

/**
 * Main content composable for HomeScreen.
 * Separated from HomeScreen to enable preview without ViewModel dependency.
 * Public for screenshot testing from androidTest.
 */
@Composable
fun HomeContent(
    state: HomeUiState.Success,
    bluetoothPermissionGranted: Boolean,
    modifier: Modifier = Modifier,
    onRequestBluetoothPermission: () -> Unit,
    onStartScan: () -> Unit,
    onUserClick: (String) -> Unit,
    onRecordClick: () -> Unit,
    onReportPositiveTest: () -> Unit,
) {
    when {
        !bluetoothPermissionGranted -> {
            BluetoothPermissionRequest(
                modifier = modifier,
                onRequestPermission = onRequestBluetoothPermission,
            )
        }
        state.nearbyUsers.isEmpty() -> {
            EmptyNearbyUsers(
                modifier = modifier,
                onReportPositiveTest = onReportPositiveTest,
            )
        }
        else -> {
            NearbyUsersList(
                nearbyUsers = state.nearbyUsers,
                selectedUsers = state.selectedUsers,
                isRecording = state.isRecording,
                modifier = modifier,
                onUserClick = onUserClick,
                onRecordClick = onRecordClick,
                onReportPositiveTest = onReportPositiveTest,
            )
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun HomeContentWithUsersPreview() {
    val now =
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
    MaterialTheme {
        HomeContent(
            state =
                HomeUiState.Success(
                    nearbyUsers =
                        listOf(
                            NearbyUser(
                                anonymousIdHash = "hash1",
                                username = "Alice",
                                signalStrength = -45,
                                lastSeen = now,
                            ),
                            NearbyUser(
                                anonymousIdHash = "hash2",
                                username = "Bob",
                                signalStrength = -60,
                                lastSeen = now,
                            ),
                        ),
                    selectedUsers = setOf("hash1"),
                    unreadNotificationCount = 0,
                    isRecording = false,
                    recordingSuccess = false,
                    lastRecordedCount = 0,
                ),
            bluetoothPermissionGranted = true,
            onRequestBluetoothPermission = {},
            onStartScan = {},
            onUserClick = {},
            onRecordClick = {},
            onReportPositiveTest = {},
        )
    }
}

@Preview
@Composable
private fun HomeContentEmptyPreview() {
    MaterialTheme {
        HomeContent(
            state =
                HomeUiState.Success(
                    nearbyUsers = emptyList(),
                    selectedUsers = emptySet(),
                    unreadNotificationCount = 0,
                    isRecording = false,
                    recordingSuccess = false,
                    lastRecordedCount = 0,
                ),
            bluetoothPermissionGranted = true,
            onRequestBluetoothPermission = {},
            onStartScan = {},
            onUserClick = {},
            onRecordClick = {},
            onReportPositiveTest = {},
        )
    }
}

@Preview
@Composable
private fun HomeContentNoBluetoothPreview() {
    MaterialTheme {
        HomeContent(
            state =
                HomeUiState.Success(
                    nearbyUsers = emptyList(),
                    selectedUsers = emptySet(),
                    unreadNotificationCount = 0,
                    isRecording = false,
                    recordingSuccess = false,
                    lastRecordedCount = 0,
                ),
            bluetoothPermissionGranted = false,
            onRequestBluetoothPermission = {},
            onStartScan = {},
            onUserClick = {},
            onRecordClick = {},
            onReportPositiveTest = {},
        )
    }
}

@Preview
@Composable
private fun BluetoothPermissionRequestPreview() {
    MaterialTheme {
        BluetoothPermissionRequest(
            onRequestPermission = {},
        )
    }
}

@Preview
@Composable
private fun EmptyNearbyUsersPreview() {
    MaterialTheme {
        EmptyNearbyUsers()
    }
}

@Preview
@Composable
private fun NearbyUsersListPreview() {
    val now =
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
    MaterialTheme {
        NearbyUsersList(
            nearbyUsers =
                listOf(
                    NearbyUser(anonymousIdHash = "hash1", username = "Alice", signalStrength = -45, lastSeen = now),
                    NearbyUser(anonymousIdHash = "hash2", username = "Bob", signalStrength = -60, lastSeen = now),
                    NearbyUser(anonymousIdHash = "hash3", username = "Charlie", signalStrength = -75, lastSeen = now),
                ),
            selectedUsers = setOf("hash1"),
            isRecording = false,
            onUserClick = {},
            onRecordClick = {},
        )
    }
}

/**
 * Bluetooth permission request content with accessibility support.
 * Provides semantic content description for the permission request state.
 */
@Composable
private fun BluetoothPermissionRequest(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
) {
    val permissionDescription = stringResource(Res.string.cd_permission_required)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp)
                .semantics {
                    contentDescription = permissionDescription
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.permission_bluetooth_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.permission_bluetooth_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        JustFyiButton(
            text = stringResource(Res.string.permission_grant),
            onClick = onRequestPermission,
            variant = JustFyiButtonVariant.PRIMARY,
        )
    }
}

/**
 * Empty state content when no nearby users are found.
 * Shows scanning animation with accessibility support for screen readers.
 */
@Composable
private fun EmptyNearbyUsers(
    modifier: Modifier = Modifier,
    onReportPositiveTest: () -> Unit = {},
) {
    // Check reduce motion preference
    val reduceMotion = rememberReduceMotionPreference()
    val emptyStateDescription = stringResource(Res.string.cd_empty_state_scanning)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp)
                .semantics {
                    contentDescription = emptyStateDescription
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Show static icon when reduce motion is enabled, animated pulse otherwise
        if (reduceMotion.value) {
            StaticBluetoothIcon()
        } else {
            ScanningPulseAnimation()
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.home_scanning),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.home_scanning_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        JustFyiButton(
            text = stringResource(Res.string.home_report_positive_test),
            onClick = onReportPositiveTest,
            variant = JustFyiButtonVariant.SECONDARY,
        )
    }
}

@Composable
private fun NearbyUsersList(
    nearbyUsers: List<NearbyUser>,
    selectedUsers: Set<String>,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    onUserClick: (String) -> Unit,
    onRecordClick: () -> Unit,
    onReportPositiveTest: () -> Unit = {},
) {
    // Check reduce motion preference for list animations
    val reduceMotion = rememberReduceMotionPreference()

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(nearbyUsers, key = { it.anonymousIdHash }) { user ->
                AnimatedVisibility(
                    visible = true,
                    enter = reducedEnterTransition(reduceMotion.value),
                ) {
                    NearbyUserCard(
                        username = user.username,
                        signalStrength = user.signalStrength,
                        isSelected = selectedUsers.contains(user.anonymousIdHash),
                        onClick = { onUserClick(user.anonymousIdHash) },
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selectedUsers.isNotEmpty()) {
                JustFyiButton(
                    text =
                        pluralStringResource(
                            Res.plurals.home_record_interactions_plural,
                            selectedUsers.size,
                            selectedUsers.size,
                        ),
                    onClick = onRecordClick,
                    variant = JustFyiButtonVariant.PRIMARY,
                    fullWidth = true,
                    isLoading = isRecording,
                )
            }

            JustFyiButton(
                text = stringResource(Res.string.home_report_positive_test),
                onClick = onReportPositiveTest,
                variant = JustFyiButtonVariant.SECONDARY,
                fullWidth = true,
            )
        }
    }
}

/**
 * Static Bluetooth icon displayed when reduce motion preference is enabled.
 * Provides the same visual appearance as the animated version but without animation.
 *
 * Accessibility: Shows static icon with content description indicating scanning state.
 */
@Composable
private fun StaticBluetoothIcon(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val scanningDescription = stringResource(Res.string.cd_scanning_animation)

    Box(
        modifier =
            modifier
                .size(120.dp)
                .semantics {
                    contentDescription = scanningDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        // Static circle at mid-scale (0.75) with static alpha (0.15)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            drawCircle(
                color = primaryColor.copy(alpha = 0.15f),
                radius = size.minDimension / 2 * 0.75f,
                style = Stroke(width = strokeWidth),
            )
        }

        // Bluetooth icon in center - contentDescription is null since the Box has the description
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = primaryColor,
        )
    }
}

/**
 * Animated scanning pulse animation with expanding circles.
 * Displays three concentric circles that pulse outward with a Bluetooth icon in the center.
 *
 * Accessibility: The animation box has a content description indicating the scanning state.
 * The Bluetooth icon contentDescription is null since the Box provides the semantic description.
 * Note: This animation is replaced with StaticBluetoothIcon when reduce motion is enabled.
 */
@Composable
private fun ScanningPulseAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val primaryColor = MaterialTheme.colorScheme.primary
    val scanningDescription = stringResource(Res.string.cd_scanning_animation)

    // Three circles with staggered timing - starting wider and more transparent
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Restart,
            ),
        label = "scale1",
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Restart,
            ),
        label = "alpha1",
    )

    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, delayMillis = 500),
                repeatMode = RepeatMode.Restart,
            ),
        label = "scale2",
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, delayMillis = 500),
                repeatMode = RepeatMode.Restart,
            ),
        label = "alpha2",
    )

    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, delayMillis = 1000),
                repeatMode = RepeatMode.Restart,
            ),
        label = "scale3",
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, delayMillis = 1000),
                repeatMode = RepeatMode.Restart,
            ),
        label = "alpha3",
    )

    Box(
        modifier =
            modifier
                .size(120.dp)
                .semantics {
                    contentDescription = scanningDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()

            // Draw three expanding circles
            drawCircle(
                color = primaryColor.copy(alpha = alpha1),
                radius = size.minDimension / 2 * scale1,
                style = Stroke(width = strokeWidth),
            )
            drawCircle(
                color = primaryColor.copy(alpha = alpha2),
                radius = size.minDimension / 2 * scale2,
                style = Stroke(width = strokeWidth),
            )
            drawCircle(
                color = primaryColor.copy(alpha = alpha3),
                radius = size.minDimension / 2 * scale3,
                style = Stroke(width = strokeWidth),
            )
        }

        // Bluetooth icon in center - contentDescription is null since the Box has the description
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = primaryColor,
        )
    }
}
