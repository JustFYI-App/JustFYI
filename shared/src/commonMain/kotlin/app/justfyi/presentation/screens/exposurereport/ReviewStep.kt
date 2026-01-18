package app.justfyi.presentation.screens.exposurereport

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.domain.model.PrivacyOptions
import app.justfyi.domain.model.STI
import app.justfyi.domain.usecase.ExposureReportState
import app.justfyi.domain.usecase.ExposureWindow
import app.justfyi.presentation.components.LoadingIndicator
import justfyi.shared.generated.resources.*
import justfyi.shared.generated.resources.Res
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Step 4: Review
 * User reviews and confirms all report details before submission.
 *
 * Simplified for the 4-step wizard flow:
 * - Displays STI types, test date, and privacy options
 * - Does NOT display contact count or exposure window (removed)
 * - Shows automatic notification confirmation text
 *
 * The backend now automatically determines contacts using unidirectional
 * graph traversal, so users don't need to select contacts manually.
 */
@Composable
fun ReviewStep(
    reportState: ExposureReportState,
    isSubmitting: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(Res.string.report_step6_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.report_step6_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isSubmitting) {
            LoadingIndicator(message = stringResource(Res.string.report_submitting))
        } else {
            // Pre-calculate STI display names outside joinToString lambda
            val stiDisplayNames = reportState.selectedSTIs.map { getSTIDisplayName(it) }

            // STI Types
            ReviewSection(title = stringResource(Res.string.report_review_sti_types)) {
                Text(
                    text = stiDisplayNames.joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Test Date
            ReviewSection(title = stringResource(Res.string.report_review_test_date)) {
                Text(
                    text =
                        reportState.testDate?.let { formatLocalDate(it) } ?: stringResource(Res.string.report_not_set),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy Options
            ReviewSection(title = stringResource(Res.string.report_review_privacy)) {
                Column {
                    Text(
                        text =
                            stringResource(
                                Res.string.report_sti_type_label,
                                if (reportState.privacyOptions.discloseSTIType) {
                                    stringResource(
                                        Res.string.report_review_disclosed,
                                    )
                                } else {
                                    stringResource(Res.string.report_review_hidden)
                                },
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            stringResource(
                                Res.string.report_exposure_date_label,
                                if (reportState.privacyOptions.discloseExposureDate) {
                                    stringResource(
                                        Res.string.report_review_disclosed,
                                    )
                                } else {
                                    stringResource(Res.string.report_review_hidden)
                                },
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Automatic Notification Confirmation
            AutoNotificationSection()
        }
    }
}

/**
 * Section displaying automatic notification confirmation.
 * Explains that contacts will be notified automatically based on interaction history.
 */
@Composable
private fun AutoNotificationSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.report_review_auto_notify),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.report_review_auto_notify_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ReviewSection(
    title: String,
    content: @Composable () -> Unit,
) {
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
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun ReviewStepPreview() {
    MaterialTheme {
        ReviewStep(
            reportState =
                ExposureReportState(
                    selectedSTIs = listOf(STI.CHLAMYDIA, STI.GONORRHEA),
                    testDate = LocalDate(2025, 12, 20),
                    exposureWindow =
                        ExposureWindow(
                            startDate = LocalDate(2025, 12, 1),
                            endDate = LocalDate(2025, 12, 20),
                            daysInWindow = 19,
                        ),
                    privacyOptions =
                        PrivacyOptions(
                            discloseSTIType = true,
                            discloseExposureDate = false,
                        ),
                ),
            isSubmitting = false,
        )
    }
}

@Preview
@Composable
private fun ReviewStepSubmittingPreview() {
    MaterialTheme {
        ReviewStep(
            reportState =
                ExposureReportState(
                    selectedSTIs = listOf(STI.HIV),
                    testDate = LocalDate(2025, 12, 15),
                    privacyOptions = PrivacyOptions(),
                ),
            isSubmitting = true,
        )
    }
}

@Preview
@Composable
private fun ReviewSectionPreview() {
    MaterialTheme {
        ReviewSection(title = "STI Types") {
            Text("Chlamydia, Gonorrhea")
        }
    }
}

@Preview
@Composable
private fun AutoNotificationSectionPreview() {
    MaterialTheme {
        AutoNotificationSection()
    }
}
