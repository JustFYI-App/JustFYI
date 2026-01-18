package app.justfyi.presentation.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.justfyi.domain.model.ExposureReport
import app.justfyi.domain.model.TestStatus
import app.justfyi.platform.rememberReduceMotionPreference
import app.justfyi.presentation.components.ErrorMessage
import app.justfyi.presentation.components.JustFyiDialog
import app.justfyi.presentation.components.JustFyiSnackbarHost
import app.justfyi.presentation.components.JustFyiTopAppBar
import app.justfyi.presentation.components.LoadingIndicator
import app.justfyi.JustFyiSuccess
import app.justfyi.presentation.components.StiTypeChips
import app.justfyi.presentation.feature.submittedreports.SubmittedReportsUiState
import app.justfyi.presentation.feature.submittedreports.SubmittedReportsViewModel
import app.justfyi.presentation.navigation.NavigationActions
import app.justfyi.presentation.util.reducedFloatOffset
import app.justfyi.util.DateTimeFormatter
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_delete_report
import justfyi.shared.generated.resources.common_cancel
import justfyi.shared.generated.resources.submitted_reports_delete_button
import justfyi.shared.generated.resources.submitted_reports_delete_confirm_message
import justfyi.shared.generated.resources.submitted_reports_delete_confirm_title
import justfyi.shared.generated.resources.submitted_reports_empty
import justfyi.shared.generated.resources.submitted_reports_empty_description
import justfyi.shared.generated.resources.submitted_reports_loading
import justfyi.shared.generated.resources.submitted_reports_privacy_anonymous
import justfyi.shared.generated.resources.submitted_reports_privacy_full
import justfyi.shared.generated.resources.submitted_reports_privacy_level
import justfyi.shared.generated.resources.submitted_reports_privacy_partial
import justfyi.shared.generated.resources.submitted_reports_submitted_on
import justfyi.shared.generated.resources.submitted_reports_test_date
import justfyi.shared.generated.resources.submitted_reports_test_negative
import justfyi.shared.generated.resources.submitted_reports_test_positive
import justfyi.shared.generated.resources.submitted_reports_title
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.Instant

/**
 * Submitted Reports screen composable.
 * Displays a chronological list of user's submitted exposure reports
 * with the ability to delete reports.
 *
 * @param viewModel The SubmittedReportsViewModel instance
 * @param navigationActions Navigation actions for screen transitions
 * @param modifier Modifier for the screen
 */
@Composable
fun SubmittedReportsScreen(
    viewModel: SubmittedReportsViewModel,
    navigationActions: NavigationActions,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // State for delete confirmation dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    var reportToDelete by remember { mutableStateOf<String?>(null) }

    // Handle UI state side effects
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SubmittedReportsUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long,
                )
                viewModel.clearError()
            }
            else -> {}
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && reportToDelete != null) {
        DeleteReportConfirmationDialog(
            onConfirm = {
                reportToDelete?.let { viewModel.deleteReport(it) }
                showDeleteDialog = false
                reportToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                reportToDelete = null
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            JustFyiTopAppBar(
                title = stringResource(Res.string.submitted_reports_title),
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
                is SubmittedReportsUiState.Loading -> {
                    LoadingIndicator(message = stringResource(Res.string.submitted_reports_loading))
                }

                is SubmittedReportsUiState.Error -> {
                    ErrorMessage(
                        message = state.message,
                        onRetry = { viewModel.refresh() },
                    )
                }

                is SubmittedReportsUiState.Empty -> {
                    EmptySubmittedReportsContent()
                }

                is SubmittedReportsUiState.Success -> {
                    SubmittedReportsContent(
                        reports = state.reports,
                        deletingReports = state.deletingReports,
                        onDeleteClick = { reportId ->
                            reportToDelete = reportId
                            showDeleteDialog = true
                        },
                    )
                }
            }
        }
    }
}

/**
 * Content composable displaying the list of submitted reports.
 */
@Composable
private fun SubmittedReportsContent(
    reports: List<ExposureReport>,
    deletingReports: Map<String, Boolean>,
    onDeleteClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = reports,
            key = { it.id },
        ) { report ->
            SubmittedReportCard(
                report = report,
                isDeleting = deletingReports[report.id] == true,
                onDeleteClick = { onDeleteClick(report.id) },
            )
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Card composable for displaying a single submitted report.
 */
@Composable
private fun SubmittedReportCard(
    report: ExposureReport,
    isDeleting: Boolean,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // STI Types
                Column(modifier = Modifier.weight(1f)) {
                    StiTypeChips(
                        stiTypeJson = report.stiTypes,
                        compact = true,
                        chipColor = if (report.testResult == TestStatus.NEGATIVE) {
                            JustFyiSuccess
                        } else {
                            null
                        },
                    )
                }

                // Delete button
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(Res.string.cd_delete_report),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Test result indicator
            Text(
                text = when (report.testResult) {
                    TestStatus.POSITIVE -> stringResource(Res.string.submitted_reports_test_positive)
                    TestStatus.NEGATIVE -> stringResource(Res.string.submitted_reports_test_negative)
                    else -> report.testResult.name
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (report.testResult) {
                    TestStatus.POSITIVE -> MaterialTheme.colorScheme.error
                    TestStatus.NEGATIVE -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Test date
            Text(
                text = stringResource(Res.string.submitted_reports_test_date, formatDate(report.testDate)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Privacy level badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        stringResource(
                            Res.string.submitted_reports_privacy_level,
                            getPrivacyLevelText(report.privacyLevel),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Submission date
            Text(
                text = stringResource(Res.string.submitted_reports_submitted_on, formatDateTime(report.reportedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Empty state content with floating animation icon.
 * Respects reduce motion preference - shows static icon when enabled.
 * Follows the EmptyHistoryContent pattern.
 */
@Composable
private fun EmptySubmittedReportsContent() {
    // Check reduce motion preference
    val reduceMotion = rememberReduceMotionPreference()

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
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Assignment,
            contentDescription = null,
            modifier =
                Modifier
                    .size(80.dp)
                    .graphicsLayer { translationY = offsetY },
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.submitted_reports_empty),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.submitted_reports_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Confirmation dialog for deleting a report.
 * Uses JustFyiDialog with isDestructive = true.
 */
@Composable
private fun DeleteReportConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    JustFyiDialog(
        title = stringResource(Res.string.submitted_reports_delete_confirm_title),
        message = stringResource(Res.string.submitted_reports_delete_confirm_message),
        confirmText = stringResource(Res.string.submitted_reports_delete_button),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissText = stringResource(Res.string.common_cancel),
        isDestructive = true,
    )
}

// ============== Helper Functions ==============

/**
 * Formats a timestamp for date-only display using locale-aware formatting.
 * Uses the platform-specific DateTimeFormatter for localized month names.
 *
 * @param timestamp The epoch milliseconds timestamp to format
 * @return A localized date string (e.g., "15 Jan 2024" in English, "15 janv. 2024" in French)
 */
private fun formatDate(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return DateTimeFormatter.formatDateShort(localDateTime.date)
}

/**
 * Formats a timestamp for date and time display using locale-aware formatting.
 * Uses the platform-specific DateTimeFormatter for localized month names and time format.
 *
 * @param timestamp The epoch milliseconds timestamp to format
 * @return A localized date-time string (e.g., "Jan 15, 2024, 3:30 PM" in English)
 */
private fun formatDateTime(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return DateTimeFormatter.formatDateTimeShort(localDateTime)
}

/**
 * Returns the localized privacy level text.
 * Note: Using @Composable annotation for stringResource access.
 */
@Composable
private fun getPrivacyLevelText(privacyLevel: String): String =
    when (privacyLevel.uppercase()) {
        "FULL" -> stringResource(Res.string.submitted_reports_privacy_full)
        "PARTIAL" -> stringResource(Res.string.submitted_reports_privacy_partial)
        "ANONYMOUS" -> stringResource(Res.string.submitted_reports_privacy_anonymous)
        else -> privacyLevel
    }

// ============== Previews ==============

@Preview
@Composable
private fun EmptySubmittedReportsContentPreview() {
    MaterialTheme {
        EmptySubmittedReportsContent()
    }
}

@Preview
@Composable
private fun SubmittedReportCardPositivePreview() {
    MaterialTheme {
        SubmittedReportCard(
            report =
                ExposureReport(
                    id = "1",
                    stiTypes = "[\"HIV\", \"SYPHILIS\"]",
                    testDate = 1735689600000, // Jan 1, 2025
                    privacyLevel = "FULL",
                    reportedAt = 1735776000000, // Jan 2, 2025
                    syncedToCloud = true,
                    testResult = TestStatus.POSITIVE,
                ),
            isDeleting = false,
            onDeleteClick = {},
        )
    }
}

@Preview
@Composable
private fun SubmittedReportCardNegativePreview() {
    MaterialTheme {
        SubmittedReportCard(
            report =
                ExposureReport(
                    id = "1",
                    stiTypes = "[\"CHLAMYDIA\"]",
                    testDate = 1735689600000,
                    privacyLevel = "ANONYMOUS",
                    reportedAt = 1735776000000,
                    syncedToCloud = true,
                    testResult = TestStatus.NEGATIVE,
                ),
            isDeleting = false,
            onDeleteClick = {},
        )
    }
}
