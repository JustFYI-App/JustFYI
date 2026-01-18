package app.justfyi.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.justfyi.JustFyiSuccess
import app.justfyi.data.model.FirestoreCollections
import app.justfyi.domain.model.ChainVisualization
import app.justfyi.domain.model.Notification
import app.justfyi.platform.rememberReduceMotionPreference
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.components.JustFyiTopAppBar
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.presentation.components.StiTypeChips
import app.justfyi.presentation.feature.notifications.NotificationListUiState
import app.justfyi.presentation.feature.notifications.NotificationListViewModel
import app.justfyi.presentation.navigation.NavigationActions
import app.justfyi.presentation.util.minimumTouchTargetHeight
import app.justfyi.presentation.util.reducedEnterTransition
import app.justfyi.presentation.util.reducedFloatOffset
import app.justfyi.util.DateTimeFormatter
import app.justfyi.util.Logger
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_empty_notifications
import justfyi.shared.generated.resources.cd_notification_card
import justfyi.shared.generated.resources.cd_notification_type_exposure
import justfyi.shared.generated.resources.cd_notification_type_report_deleted
import justfyi.shared.generated.resources.cd_notification_type_update
import justfyi.shared.generated.resources.cd_notification_unread
import justfyi.shared.generated.resources.notification_chain_update
import justfyi.shared.generated.resources.notification_days_ago
import justfyi.shared.generated.resources.notification_exposure_description
import justfyi.shared.generated.resources.notification_exposure_description_generic
import justfyi.shared.generated.resources.notification_generic
import justfyi.shared.generated.resources.notification_hours_ago
import justfyi.shared.generated.resources.notification_just_now
import justfyi.shared.generated.resources.notification_minutes_ago
import justfyi.shared.generated.resources.notification_potential_exposure
import justfyi.shared.generated.resources.notification_report_deleted
import justfyi.shared.generated.resources.notification_report_deleted_description
import justfyi.shared.generated.resources.notification_tap_details
import justfyi.shared.generated.resources.notification_tested_negative
import justfyi.shared.generated.resources.notification_tested_positive
import justfyi.shared.generated.resources.notification_update_description
import justfyi.shared.generated.resources.notifications_empty
import justfyi.shared.generated.resources.notifications_loading
import justfyi.shared.generated.resources.notifications_title
import justfyi.shared.generated.resources.notifications_unread_count
import justfyi.shared.generated.resources.notifications_will_receive
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Reusable top bar for the Notification List screen.
 * Uses stringResource() internally for proper localization.
 */
@Composable
fun NotificationListTopBar(onNavigationClick: () -> Unit = {}) {
    JustFyiTopAppBar(
        title = stringResource(Res.string.notifications_title),
        showNavigationIcon = true,
        onNavigationClick = onNavigationClick,
    )
}

/**
 * Notification List screen composable.
 * Displays a list of received notifications.
 *
 * @param viewModel The NotificationListViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param modifier Modifier for the screen
 */
@Composable
fun NotificationListScreen(
    viewModel: NotificationListViewModel,
    navigationActions: NavigationActions,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle errors
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is NotificationListUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long,
                )
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { NotificationListTopBar(onNavigationClick = { navigationActions.navigateBack() }) },
        snackbarHost = { JustFyiSnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is NotificationListUiState.Loading -> {
                    LoadingIndicator(message = stringResource(Res.string.notifications_loading))
                }

                is NotificationListUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        onRetry = { viewModel.refresh() },
                    )
                }

                is NotificationListUiState.Success -> {
                    if (state.notifications.isEmpty()) {
                        EmptyNotificationsContent()
                    } else {
                        NotificationListContent(
                            notifications = state.notifications,
                            onNotificationClick = { notification ->
                                viewModel.markAsRead(notification.id)
                                navigationActions.navigateToNotificationDetail(notification.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationListContent(
    notifications: List<Notification>,
    onNotificationClick: (Notification) -> Unit,
) {
    val unreadCount = notifications.count { !it.isRead }
    // Check reduce motion preference for list animations
    val reduceMotion = rememberReduceMotionPreference()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header with unread count
        if (unreadCount > 0) {
            item {
                UnreadCountHeader(unreadCount = unreadCount)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        items(
            items = notifications,
            key = { it.id },
        ) { notification ->
            AnimatedVisibility(
                visible = true,
                enter = reducedEnterTransition(reduceMotion.value),
            ) {
                NotificationCard(
                    notification = notification,
                    onClick = { onNotificationClick(notification) },
                )
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UnreadCountHeader(unreadCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.notifications_unread_count, unreadCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private const val TAG = "NotificationListScreen"

/**
 * Card component for displaying a single notification.
 *
 * Touch target compliance: This card meets WCAG 2.1 AA minimum 48dp touch target
 * through its full-width layout and minimum height enforcement.
 *
 * Accessibility: Provides comprehensive content description including notification type,
 * read/unread status, and timestamp for screen reader users.
 * Focus management: Uses focusable() modifier to enable keyboard navigation
 * via Tab key on hardware keyboards and Android TV.
 *
 * @param notification The notification data to display
 * @param onClick Callback when the card is clicked
 */
@Composable
fun NotificationCard(
    notification: Notification,
    onClick: () -> Unit,
) {
    val isExposure = notification.type == FirestoreCollections.NotificationTypes.EXPOSURE
    val isReportDeleted = notification.type == FirestoreCollections.NotificationTypes.REPORT_DELETED
    val isRetracted = notification.deletedAt != null

    // Check if current user tested negative or positive by parsing chain data
    val chainVisualization =
        remember(notification.chainData) {
            ChainVisualization.fromJson(notification.chainData)
        }
    val userTestedNegative = chainVisualization.currentUserTestedNegative()
    val userTestedPositive = chainVisualization.currentUserTestedPositive()

    // Debug logging
    LaunchedEffect(notification.id, notification.deletedAt) {
        Logger.d(
            TAG,
            "NotificationCard: id=${notification.id}, deletedAt=${notification.deletedAt}, isRetracted=$isRetracted",
        )
    }

    // Build accessibility content description
    val notificationTypeDescription = getNotificationTypeDescription(notification.type)
    val readStatusDescription =
        if (!notification.isRead && !isRetracted) {
            stringResource(Res.string.cd_notification_unread)
        } else {
            ""
        }
    val timestampDescription = formatTimestamp(notification.receivedAt)
    val cardContentDescription =
        stringResource(
            Res.string.cd_notification_card,
            notificationTypeDescription,
            readStatusDescription,
            timestampDescription,
        )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .minimumTouchTargetHeight()
                .clip(MaterialTheme.shapes.medium)
                .focusable()
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = cardContentDescription
                },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        userTestedNegative -> MaterialTheme.colorScheme.surface
                        userTestedPositive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        !notification.isRead && !isRetracted -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    },
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation =
                    if (!notification.isRead &&
                        !userTestedNegative &&
                        !userTestedPositive &&
                        !isRetracted
                    ) {
                        2.dp
                    } else {
                        0.dp
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Type icon - green checkmark if negative, red warning if positive
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                userTestedNegative -> JustFyiSuccess.copy(alpha = 0.2f) // Light green
                                userTestedPositive -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f) // Light red
                                isExposure || isRetracted -> MaterialTheme.colorScheme.errorContainer
                                isReportDeleted -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        when {
                            userTestedNegative -> Icons.Default.CheckCircle
                            userTestedPositive -> Icons.Default.Warning
                            else -> getNotificationIcon(notification.type)
                        },
                    contentDescription = null, // Description provided at card level
                    tint =
                        when {
                            userTestedNegative -> JustFyiSuccess // Green
                            userTestedPositive -> MaterialTheme.colorScheme.error // Red
                            isExposure || isRetracted -> MaterialTheme.colorScheme.onErrorContainer
                            isReportDeleted -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        // Show badge based on status
                        when {
                            userTestedPositive -> {
                                Text(
                                    text = stringResource(Res.string.notification_tested_positive),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            userTestedNegative -> {
                                Text(
                                    text = stringResource(Res.string.notification_tested_negative),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = JustFyiSuccess, // Green
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            isRetracted -> {
                                Text(
                                    text = stringResource(Res.string.notification_report_deleted),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                        Text(
                            text = getNotificationTitle(notification.type),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight =
                                if (!notification.isRead &&
                                    !userTestedNegative &&
                                    !userTestedPositive &&
                                    !isRetracted
                                ) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                },
                            color =
                                when {
                                    userTestedPositive -> MaterialTheme.colorScheme.error // Red
                                    userTestedNegative -> JustFyiSuccess // Green
                                    isExposure || isRetracted -> MaterialTheme.colorScheme.error
                                    isReportDeleted -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            textDecoration = if (isRetracted) TextDecoration.LineThrough else null,
                        )
                    }

                    // Unread indicator (don't show for retracted reports)
                    // Note: This is a visual-only indicator; the read status is announced
                    // in the card's content description for screen readers
                    if (!notification.isRead && !isRetracted) {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // STI type chips if exposure or report deleted (when STI info is available)
                notification.stiType?.let { stiType ->
                    StiTypeChips(stiTypeJson = stiType, compact = true)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Use outline color for timestamp to ensure WCAG AA compliance
                Text(
                    text = formatTimestamp(notification.receivedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * Empty state content with floating animation icon.
 * Respects reduce motion preference - shows static icon when enabled.
 *
 * Accessibility: Provides comprehensive content description for screen readers
 * explaining the empty state and what to expect.
 */
@Composable
private fun EmptyNotificationsContent() {
    // Check reduce motion preference
    val reduceMotion = rememberReduceMotionPreference()
    val emptyStateDescription = stringResource(Res.string.cd_empty_notifications)

    // Floating animation for the icon - only when reduce motion is disabled
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val animatedOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "floatOffset",
    )

    // Use static offset when reduce motion is enabled
    val offsetY = reducedFloatOffset(reduceMotion.value, animatedOffsetY)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp)
                .semantics {
                    contentDescription = emptyStateDescription
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null, // Description provided at column level
            modifier =
                Modifier
                    .size(80.dp)
                    .graphicsLayer { translationY = offsetY },
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.notifications_empty),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.notifications_will_receive),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// Helper functions

private fun getNotificationIcon(type: String): ImageVector =
    when (type) {
        FirestoreCollections.NotificationTypes.EXPOSURE -> Icons.Default.Warning
        FirestoreCollections.NotificationTypes.UPDATE -> Icons.Default.Info
        FirestoreCollections.NotificationTypes.REPORT_DELETED -> Icons.Default.Cancel
        else -> Icons.Default.Notifications
    }

@Composable
private fun getNotificationTitle(type: String): String =
    when (type) {
        FirestoreCollections.NotificationTypes.EXPOSURE -> stringResource(Res.string.notification_potential_exposure)
        FirestoreCollections.NotificationTypes.UPDATE -> stringResource(Res.string.notification_chain_update)
        FirestoreCollections.NotificationTypes.REPORT_DELETED -> stringResource(Res.string.notification_report_deleted)
        else -> stringResource(Res.string.notification_generic)
    }

/**
 * Returns an accessibility-friendly description for the notification type.
 */
@Composable
private fun getNotificationTypeDescription(type: String): String =
    when (type) {
        FirestoreCollections.NotificationTypes.EXPOSURE -> stringResource(Res.string.cd_notification_type_exposure)
        FirestoreCollections.NotificationTypes.UPDATE -> stringResource(Res.string.cd_notification_type_update)
        FirestoreCollections.NotificationTypes.REPORT_DELETED ->
            stringResource(
                Res.string.cd_notification_type_report_deleted,
            )
        else -> stringResource(Res.string.notification_generic)
    }

@Composable
private fun getNotificationDescription(notification: Notification): String =
    when (notification.type) {
        FirestoreCollections.NotificationTypes.EXPOSURE ->
            notification.stiType?.let { sti ->
                stringResource(Res.string.notification_exposure_description, sti)
            } ?: stringResource(Res.string.notification_exposure_description_generic)
        FirestoreCollections.NotificationTypes.UPDATE -> stringResource(Res.string.notification_update_description)
        FirestoreCollections.NotificationTypes.REPORT_DELETED ->
            stringResource(
                Res.string.notification_report_deleted_description,
            )
        else -> stringResource(Res.string.notification_tap_details)
    }

@Composable
private fun formatTimestamp(millis: Long): String {
    val now = Clock.System.now()
    val diff = now.toEpochMilliseconds() - millis

    return when {
        diff < 60_000L -> stringResource(Res.string.notification_just_now)
        diff < 3600_000L -> stringResource(Res.string.notification_minutes_ago, (diff / 60_000L).toInt())
        diff < 86400_000L -> stringResource(Res.string.notification_hours_ago, (diff / 3600_000L).toInt())
        diff < 604800_000L -> stringResource(Res.string.notification_days_ago, (diff / 86400_000L).toInt())
        else -> {
            val instant = Instant.fromEpochMilliseconds(millis)
            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            DateTimeFormatter.formatDateShort(localDateTime.date)
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun EmptyNotificationsContentPreview() {
    MaterialTheme {
        EmptyNotificationsContent()
    }
}

@Preview
@Composable
private fun UnreadCountHeaderPreview() {
    MaterialTheme {
        UnreadCountHeader(unreadCount = 3)
    }
}

@Preview
@Composable
private fun NotificationCardExposurePreview() {
    MaterialTheme {
        val now = Clock.System.now().toEpochMilliseconds()
        NotificationCard(
            notification =
                Notification(
                    id = "1",
                    type = "EXPOSURE",
                    stiType = "Chlamydia",
                    exposureDate = now - 86400_000L,
                    chainData = "chain123",
                    isRead = false,
                    receivedAt = now - 3600_000L,
                    updatedAt = now,
                ),
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun NotificationCardReadPreview() {
    MaterialTheme {
        val now = Clock.System.now().toEpochMilliseconds()
        NotificationCard(
            notification =
                Notification(
                    id = "2",
                    type = "UPDATE",
                    chainData = "chain456",
                    isRead = true,
                    receivedAt = now - 172800_000L,
                    updatedAt = now,
                ),
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun NotificationCardReportDeletedPreview() {
    MaterialTheme {
        val now = Clock.System.now().toEpochMilliseconds()
        NotificationCard(
            notification =
                Notification(
                    id = "3",
                    type = "REPORT_DELETED",
                    stiType = "Chlamydia",
                    chainData = "",
                    isRead = false,
                    receivedAt = now - 7200_000L,
                    updatedAt = now,
                ),
            onClick = {},
        )
    }
}
