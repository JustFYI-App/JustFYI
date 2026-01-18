package app.justfyi.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.data.model.FirestoreCollections
import app.justfyi.domain.model.ChainVisualization
import app.justfyi.domain.model.Notification
import app.justfyi.presentation.components.ChainVisualizationView
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiButton
import app.justfyi.presentation.components.JustFyiButtonVariant
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.components.JustFyiTopAppBar
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.presentation.components.StiTypeChips
import app.justfyi.presentation.feature.notifications.NotificationDetailUiState
import app.justfyi.presentation.feature.notifications.NotificationDetailViewModel
import app.justfyi.presentation.navigation.NavigationActions
import app.justfyi.util.DateTimeFormatter
import app.justfyi.util.Logger
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_notification_retracted_banner
import justfyi.shared.generated.resources.cd_notification_status_exposure
import justfyi.shared.generated.resources.cd_notification_status_update
import justfyi.shared.generated.resources.cd_section_heading
import justfyi.shared.generated.resources.notification_action_1
import justfyi.shared.generated.resources.notification_action_2
import justfyi.shared.generated.resources.notification_action_3
import justfyi.shared.generated.resources.notification_action_4
import justfyi.shared.generated.resources.notification_chain_status_update
import justfyi.shared.generated.resources.notification_detail_title
import justfyi.shared.generated.resources.notification_exposure_date
import justfyi.shared.generated.resources.notification_loading
import justfyi.shared.generated.resources.notification_mark_as_tested
import justfyi.shared.generated.resources.notification_marked_as_tested
import justfyi.shared.generated.resources.notification_marked_success
import justfyi.shared.generated.resources.notification_received
import justfyi.shared.generated.resources.notification_recommended_actions
import justfyi.shared.generated.resources.notification_report_deleted
import justfyi.shared.generated.resources.notification_report_deleted_description
import justfyi.shared.generated.resources.notification_report_retracted_explanation
import justfyi.shared.generated.resources.notification_report_retracted_header
import justfyi.shared.generated.resources.notification_report_retracted_recommendation
import justfyi.shared.generated.resources.notification_sti_exposure
import justfyi.shared.generated.resources.notification_sti_type
import justfyi.shared.generated.resources.notification_test_status_shared
import justfyi.shared.generated.resources.test_result_cancel
import justfyi.shared.generated.resources.test_result_description
import justfyi.shared.generated.resources.test_result_negative
import justfyi.shared.generated.resources.test_result_positive
import justfyi.shared.generated.resources.test_result_title
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.Instant

/**
 * Notification Detail screen composable.
 * Displays full notification details with chain visualization.
 *
 * @param viewModel The NotificationDetailViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param modifier Modifier for the screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(
    viewModel: NotificationDetailViewModel,
    navigationActions: NavigationActions,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // State for the test result bottom sheet
    var showTestResultSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Get localized success message
    val markedSuccessMessage = stringResource(Res.string.notification_marked_success)

    // Show error message from Success state (non-fatal errors)
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is NotificationDetailUiState.Success -> {
                state.error?.let { error ->
                    snackbarHostState.showSnackbar(
                        message = error,
                        duration = SnackbarDuration.Long,
                    )
                    viewModel.clearError()
                }
            }
            is NotificationDetailUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long,
                )
            }
            else -> {}
        }
    }

    // Show success message when marked as tested
    LaunchedEffect(uiState) {
        val successState = uiState as? NotificationDetailUiState.Success
        if (successState?.markedAsTested == true) {
            snackbarHostState.showSnackbar(
                message = markedSuccessMessage,
                duration = SnackbarDuration.Long,
            )
        }
    }

    // Test Result Bottom Sheet
    if (showTestResultSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTestResultSheet = false },
            sheetState = sheetState,
        ) {
            TestResultBottomSheet(
                onPositive = {
                    showTestResultSheet = false
                    // Navigate to full exposure report flow for positive result
                    navigationActions.navigateToExposureReport()
                },
                onNegative = {
                    showTestResultSheet = false
                    // Report negative test for this notification's STI
                    viewModel.submitNegativeResult()
                },
                onCancel = { showTestResultSheet = false },
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            JustFyiTopAppBar(
                title = stringResource(Res.string.notification_detail_title),
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
                is NotificationDetailUiState.Loading -> {
                    LoadingIndicator(message = stringResource(Res.string.notification_loading))
                }

                is NotificationDetailUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        onRetry = { viewModel.refresh() },
                    )
                }

                is NotificationDetailUiState.Success -> {
                    val isRetracted = state.notification.deletedAt != null
                    // Debug logging
                    LaunchedEffect(state.notification.id, state.notification.deletedAt) {
                        Logger.d(
                            "NotificationDetailScreen",
                            "Success: id=${state.notification.id}, deletedAt=${state.notification.deletedAt}, isRetracted=$isRetracted, type=${state.notification.type}",
                        )
                    }
                    when {
                        // Show retracted content for REPORT_DELETED type OR notifications with deletedAt
                        state.notification.type == FirestoreCollections.NotificationTypes.REPORT_DELETED -> {
                            ReportDeletedDetailContent(
                                notification = state.notification,
                            )
                        }
                        else -> {
                            NotificationDetailContent(
                                notification = state.notification,
                                chainVisualization = state.chainVisualization,
                                isMarkingTested = state.isMarkingTested,
                                markedAsTested = state.markedAsTested,
                                onMarkAsTested = { showTestResultSheet = true },
                                isRetracted = isRetracted,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Content for REPORT_DELETED notification type.
 * Displays information about a retracted exposure report.
 *
 * Accessibility: Section headers use heading semantics for screen reader navigation.
 */
@Composable
private fun ReportDeletedDetailContent(notification: Notification) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Report Retracted Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.notification_report_retracted_header),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics { heading() },
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.notification_received, formatDateTime(notification.receivedAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }

        // STI Information (if available)
        notification.stiType?.let { stiType ->
            SectionCard(title = stringResource(Res.string.notification_sti_type)) {
                StiTypeChips(stiTypeJson = stiType)
            }
        }

        // Exposure Date (if available)
        notification.exposureDate?.let { date ->
            SectionCard(title = stringResource(Res.string.notification_exposure_date)) {
                Text(
                    text = formatDate(date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Explanation
        SectionCard(title = stringResource(Res.string.notification_report_deleted)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.notification_report_retracted_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(Res.string.notification_report_retracted_recommendation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Notification detail content with accessibility support.
 *
 * Accessibility: Section headers use heading semantics for screen reader navigation.
 * Status indicators (exposure type, retracted banner) have appropriate content descriptions.
 */
@Composable
private fun NotificationDetailContent(
    notification: Notification,
    chainVisualization: ChainVisualization?,
    isMarkingTested: Boolean,
    markedAsTested: Boolean,
    onMarkAsTested: () -> Unit,
    isRetracted: Boolean = false,
) {
    val exposureStatusDescription = stringResource(Res.string.cd_notification_status_exposure)
    val updateStatusDescription = stringResource(Res.string.cd_notification_status_update)
    val retractedBannerDescription = stringResource(Res.string.cd_notification_retracted_banner)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Retracted banner (shown when report was deleted by reporter)
        if (isRetracted) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = retractedBannerDescription
                        },
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.notification_report_deleted),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.notification_report_deleted_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // Notification type header with accessibility content description
        val isExposureNotification = notification.type == FirestoreCollections.NotificationTypes.EXPOSURE
        val headerContentDescription =
            if (isExposureNotification) {
                exposureStatusDescription
            } else {
                updateStatusDescription
            }

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = headerContentDescription
                    },
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (isExposureNotification) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text =
                        if (isExposureNotification) {
                            stringResource(Res.string.notification_sti_exposure)
                        } else {
                            stringResource(Res.string.notification_chain_status_update)
                        },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (isExposureNotification) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    modifier = Modifier.semantics { heading() },
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.notification_received, formatDateTime(notification.receivedAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (isExposureNotification) {
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        },
                )
            }
        }

        // STI Information
        notification.stiType?.let { stiType ->
            SectionCard(title = stringResource(Res.string.notification_sti_type)) {
                StiTypeChips(stiTypeJson = stiType)
            }
        }

        // Exposure Date
        notification.exposureDate?.let { date ->
            SectionCard(title = stringResource(Res.string.notification_exposure_date)) {
                Text(
                    text = formatDate(date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Chain Visualization
        chainVisualization?.let { chain ->
            ChainVisualizationView(
                chainVisualization = chain,
                stiType = notification.stiType,
            )
        }

        // Recommended Actions
        SectionCard(title = stringResource(Res.string.notification_recommended_actions)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecommendedAction(stringResource(Res.string.notification_action_1))
                RecommendedAction(stringResource(Res.string.notification_action_2))
                RecommendedAction(stringResource(Res.string.notification_action_3))
                RecommendedAction(stringResource(Res.string.notification_action_4))
            }
        }

        // Mark as Tested Button
        JustFyiButton(
            text =
                if (markedAsTested) {
                    stringResource(
                        Res.string.notification_marked_as_tested,
                    )
                } else {
                    stringResource(Res.string.notification_mark_as_tested)
                },
            onClick = onMarkAsTested,
            variant = JustFyiButtonVariant.PRIMARY,
            fullWidth = true,
            enabled = !isMarkingTested && !markedAsTested,
            isLoading = isMarkingTested,
            icon = if (markedAsTested) Icons.Default.Check else null,
        )

        if (markedAsTested) {
            Text(
                text = stringResource(Res.string.notification_test_status_shared),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Section card with heading semantics for screen reader navigation.
 *
 * Accessibility: The title uses heading semantics, allowing screen reader users
 * to navigate between sections using heading navigation commands.
 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val sectionDescription = stringResource(Res.string.cd_section_heading, title)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun RecommendedAction(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatDateTime(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return DateTimeFormatter.formatDateTimeWithTime(localDateTime)
}

private fun formatDate(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return DateTimeFormatter.formatDateOnly(localDateTime.date)
}

/**
 * Simple bottom sheet for reporting test result.
 * User selects Positive or Negative.
 * - Positive: Navigates to full exposure report flow
 * - Negative: Updates the chain status for this notification
 *
 * @param onPositive Callback when user tested positive
 * @param onNegative Callback when user tested negative
 * @param onCancel Callback when cancelled
 */
@Composable
private fun TestResultBottomSheet(
    onPositive: () -> Unit,
    onNegative: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
    ) {
        // Title
        Text(
            text = stringResource(Res.string.test_result_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Description
        Text(
            text = stringResource(Res.string.test_result_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Positive result button
        JustFyiButton(
            text = stringResource(Res.string.test_result_positive),
            onClick = onPositive,
            variant = JustFyiButtonVariant.PRIMARY,
            fullWidth = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Negative result button
        JustFyiButton(
            text = stringResource(Res.string.test_result_negative),
            onClick = onNegative,
            variant = JustFyiButtonVariant.SECONDARY,
            fullWidth = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Cancel button
        JustFyiButton(
            text = stringResource(Res.string.test_result_cancel),
            onClick = onCancel,
            variant = JustFyiButtonVariant.TEXT,
            fullWidth = true,
        )
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun NotificationDetailContentExposurePreview() {
    val now =
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
    MaterialTheme {
        NotificationDetailContent(
            notification =
                Notification(
                    id = "1",
                    type = "EXPOSURE",
                    stiType = "Chlamydia",
                    exposureDate = now - 86400_000L * 7,
                    chainData = "chain123",
                    isRead = true,
                    receivedAt = now - 3600_000L,
                    updatedAt = now,
                ),
            chainVisualization = null,
            isMarkingTested = false,
            markedAsTested = false,
            onMarkAsTested = {},
        )
    }
}

@Preview
@Composable
private fun ReportDeletedDetailContentPreview() {
    val now =
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
    MaterialTheme {
        ReportDeletedDetailContent(
            notification =
                Notification(
                    id = "3",
                    type = "REPORT_DELETED",
                    stiType = "Chlamydia",
                    exposureDate = now - 86400_000L * 14,
                    chainData = "",
                    isRead = true,
                    receivedAt = now - 7200_000L,
                    updatedAt = now,
                ),
        )
    }
}

@Preview
@Composable
private fun SectionCardPreview() {
    MaterialTheme {
        SectionCard(title = "Section Title") {
            Text("Section content goes here")
        }
    }
}
