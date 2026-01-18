package app.justfyi.presentation.screens.exposurereport

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.justfyi.domain.model.STI
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.cd_sti_checkbox
import justfyi.shared.generated.resources.cd_sti_checkbox_not_selected
import justfyi.shared.generated.resources.cd_sti_checkbox_selected
import justfyi.shared.generated.resources.report_incubation
import justfyi.shared.generated.resources.report_step1_description
import justfyi.shared.generated.resources.report_step1_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Step 1: STI Selection
 * User selects which STI(s) they have tested positive for.
 *
 * Accessibility: Step title uses heading semantics for screen reader navigation.
 * Each STI checkbox has a content description including name, incubation period, and selection state.
 */
@Composable
fun STISelectionStep(
    selectedSTIs: List<STI>,
    onToggleSTI: (STI) -> Unit,
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
            text = stringResource(Res.string.report_step1_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.report_step1_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        STI.entries.forEach { sti ->
            STICheckboxItem(
                sti = sti,
                isSelected = selectedSTIs.contains(sti),
                onToggle = { onToggleSTI(sti) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * STI checkbox item with accessibility support.
 *
 * Accessibility: Content description includes STI name, incubation period, and
 * current selection state for screen reader users.
 */
@Composable
private fun STICheckboxItem(
    sti: STI,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val stiName = getSTIDisplayName(sti)
    val selectionState =
        if (isSelected) {
            stringResource(Res.string.cd_sti_checkbox_selected)
        } else {
            stringResource(Res.string.cd_sti_checkbox_not_selected)
        }
    val checkboxDescription =
        stringResource(
            Res.string.cd_sti_checkbox,
            stiName,
            sti.maxIncubationDays,
            selectionState,
        )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .semantics {
                    contentDescription = checkboxDescription
                },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stiName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(Res.string.report_incubation, sti.maxIncubationDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ============== Previews ==============

@Preview
@Composable
private fun STISelectionStepEmptyPreview() {
    MaterialTheme {
        STISelectionStep(
            selectedSTIs = emptyList(),
            onToggleSTI = {},
        )
    }
}

@Preview
@Composable
private fun STISelectionStepWithSelectionsPreview() {
    MaterialTheme {
        STISelectionStep(
            selectedSTIs = listOf(STI.CHLAMYDIA, STI.GONORRHEA),
            onToggleSTI = {},
        )
    }
}

@Preview
@Composable
private fun STICheckboxItemUnselectedPreview() {
    MaterialTheme {
        STICheckboxItem(
            sti = STI.CHLAMYDIA,
            isSelected = false,
            onToggle = {},
        )
    }
}

@Preview
@Composable
private fun STICheckboxItemSelectedPreview() {
    MaterialTheme {
        STICheckboxItem(
            sti = STI.HIV,
            isSelected = true,
            onToggle = {},
        )
    }
}
