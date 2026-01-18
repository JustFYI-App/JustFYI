package app.justfyi.presentation.screens.exposurereport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import app.justfyi.domain.usecase.DateValidationError
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_date_picker_field
import justfyi.shared.generated.resources.cd_date_picker_not_set
import justfyi.shared.generated.resources.common_cancel
import justfyi.shared.generated.resources.report_date_beyond_retention
import justfyi.shared.generated.resources.report_date_future
import justfyi.shared.generated.resources.report_select
import justfyi.shared.generated.resources.report_step2_description
import justfyi.shared.generated.resources.report_step2_title
import justfyi.shared.generated.resources.report_tap_to_select_date
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.Instant

/**
 * Step 2: Date Picker
 * User selects when they received their positive test result.
 *
 * Accessibility: Step title uses heading semantics for screen reader navigation.
 * Date picker field has a content description indicating current value or "not set".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerStep(
    testDate: LocalDate?,
    validationError: DateValidationError?,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    // Build accessible content description for date field
    val notSetText = stringResource(Res.string.cd_date_picker_not_set)
    val dateDisplayText = testDate?.let { formatLocalDate(it) } ?: notSetText
    val dateFieldDescription = stringResource(Res.string.cd_date_picker_field, dateDisplayText)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.report_step2_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.report_step2_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = dateFieldDescription
                    },
            onClick = { showDatePicker = true },
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text =
                        if (testDate != null) {
                            formatLocalDate(testDate)
                        } else {
                            stringResource(Res.string.report_tap_to_select_date)
                        },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color =
                        if (testDate != null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }
        }

        // Validation error message
        validationError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    when (error) {
                        DateValidationError.FUTURE_DATE -> stringResource(Res.string.report_date_future)
                        DateValidationError.BEYOND_RETENTION_PERIOD ->
                            stringResource(
                                Res.string.report_date_beyond_retention,
                            )
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val instant = Instant.fromEpochMilliseconds(millis)
                            val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
                            onDateSelected(localDate)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(Res.string.report_select))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun DatePickerStepNoDatePreview() {
    MaterialTheme {
        DatePickerStep(
            testDate = null,
            validationError = null,
            onDateSelected = {},
        )
    }
}

@Preview
@Composable
private fun DatePickerStepWithDatePreview() {
    MaterialTheme {
        DatePickerStep(
            testDate = LocalDate(2025, 12, 20),
            validationError = null,
            onDateSelected = {},
        )
    }
}

@Preview
@Composable
private fun DatePickerStepWithFutureDateErrorPreview() {
    MaterialTheme {
        DatePickerStep(
            testDate = LocalDate(2026, 1, 15),
            validationError = DateValidationError.FUTURE_DATE,
            onDateSelected = {},
        )
    }
}

@Preview
@Composable
private fun DatePickerStepWithRetentionErrorPreview() {
    MaterialTheme {
        DatePickerStep(
            testDate = LocalDate(2024, 6, 1),
            validationError = DateValidationError.BEYOND_RETENTION_PERIOD,
            onDateSelected = {},
        )
    }
}
