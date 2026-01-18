package app.justfyi.presentation.screens.exposurereport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.domain.model.PrivacyOptions
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_privacy_disabled
import justfyi.shared.generated.resources.cd_privacy_enabled
import justfyi.shared.generated.resources.cd_privacy_toggle_date
import justfyi.shared.generated.resources.cd_privacy_toggle_sti
import justfyi.shared.generated.resources.report_disclose_date
import justfyi.shared.generated.resources.report_disclose_date_description
import justfyi.shared.generated.resources.report_disclose_sti
import justfyi.shared.generated.resources.report_disclose_sti_description
import justfyi.shared.generated.resources.report_privacy_note
import justfyi.shared.generated.resources.report_privacy_note_description
import justfyi.shared.generated.resources.report_step5_description
import justfyi.shared.generated.resources.report_step5_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Step 3 (previously Step 5): Privacy Options
 * User configures what information to disclose in notifications.
 *
 * Accessibility: Step title uses heading semantics for screen reader navigation.
 * Privacy toggles have content descriptions including current state.
 */
@Composable
fun PrivacyOptionsStep(
    privacyOptions: PrivacyOptions,
    onToggleSTIDisclosure: (Boolean) -> Unit,
    onToggleDateDisclosure: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabledText = stringResource(Res.string.cd_privacy_enabled)
    val disabledText = stringResource(Res.string.cd_privacy_disabled)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(Res.string.report_step5_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.report_step5_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        // STI Type Disclosure Toggle
        val stiToggleState = if (privacyOptions.discloseSTIType) enabledText else disabledText
        val stiToggleDescription = stringResource(Res.string.cd_privacy_toggle_sti, stiToggleState)
        PrivacyOptionItem(
            title = stringResource(Res.string.report_disclose_sti),
            description = stringResource(Res.string.report_disclose_sti_description),
            isEnabled = privacyOptions.discloseSTIType,
            onToggle = onToggleSTIDisclosure,
            contentDescription = stiToggleDescription,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Date Disclosure Toggle
        val dateToggleState = if (privacyOptions.discloseExposureDate) enabledText else disabledText
        val dateToggleDescription = stringResource(Res.string.cd_privacy_toggle_date, dateToggleState)
        PrivacyOptionItem(
            title = stringResource(Res.string.report_disclose_date),
            description = stringResource(Res.string.report_disclose_date_description),
            isEnabled = privacyOptions.discloseExposureDate,
            onToggle = onToggleDateDisclosure,
            contentDescription = dateToggleDescription,
        )

        Spacer(modifier = Modifier.height(24.dp))

        PrivacyNoteCard()
    }
}

/**
 * Privacy option item with accessibility support.
 *
 * Accessibility: Content description announces the toggle purpose and current state.
 */
@Composable
private fun PrivacyOptionItem(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    contentDescription: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    this.contentDescription = contentDescription
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun PrivacyNoteCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.report_privacy_note),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.report_privacy_note_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun PrivacyOptionsStepDefaultPreview() {
    MaterialTheme {
        PrivacyOptionsStep(
            privacyOptions = PrivacyOptions(),
            onToggleSTIDisclosure = {},
            onToggleDateDisclosure = {},
        )
    }
}

@Preview
@Composable
private fun PrivacyOptionsStepAllEnabledPreview() {
    MaterialTheme {
        PrivacyOptionsStep(
            privacyOptions =
                PrivacyOptions(
                    discloseSTIType = true,
                    discloseExposureDate = true,
                ),
            onToggleSTIDisclosure = {},
            onToggleDateDisclosure = {},
        )
    }
}

@Preview
@Composable
private fun PrivacyOptionItemEnabledPreview() {
    MaterialTheme {
        PrivacyOptionItem(
            title = "Disclose STI Type",
            description = "Include the specific STI type in notifications",
            isEnabled = true,
            onToggle = {},
            contentDescription = "Disclose STI type in notification, currently enabled",
        )
    }
}

@Preview
@Composable
private fun PrivacyOptionItemDisabledPreview() {
    MaterialTheme {
        PrivacyOptionItem(
            title = "Disclose Exposure Date",
            description = "Include the approximate exposure date",
            isEnabled = false,
            onToggle = {},
            contentDescription = "Disclose exposure date in notification, currently disabled",
        )
    }
}

@Preview
@Composable
private fun PrivacyNoteCardPreview() {
    MaterialTheme {
        PrivacyNoteCard()
    }
}
