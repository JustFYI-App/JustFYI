package app.justfyi.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.justfyi.domain.model.STI
import justfyi.shared.generated.resources.Res
import justfyi.shared.generated.resources.sti_chlamydia
import justfyi.shared.generated.resources.sti_gonorrhea
import justfyi.shared.generated.resources.sti_herpes
import justfyi.shared.generated.resources.sti_hiv
import justfyi.shared.generated.resources.sti_hpv
import justfyi.shared.generated.resources.sti_other
import justfyi.shared.generated.resources.sti_syphilis
import org.jetbrains.compose.resources.stringResource

/**
 * Displays STI types from a JSON array string as formatted chips.
 * Parses the JSON array and shows each STI as a localized chip.
 *
 * @param stiTypeJson The JSON array string (e.g., "[\"HIV\", \"SYPHILIS\"]")
 * @param modifier Modifier for the container
 * @param compact Whether to use compact styling (smaller chips)
 * @param chipColor Optional background color for the chips (defaults to error color)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StiTypeChips(
    stiTypeJson: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    chipColor: Color? = null,
) {
    if (stiTypeJson.isNullOrBlank()) return

    val stiList = STI.fromJsonArray(stiTypeJson)
    if (stiList.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        stiList.forEach { sti ->
            StiChip(
                sti = sti,
                compact = compact,
                chipColor = chipColor,
            )
        }
    }
}

/**
 * A single STI chip displaying the localized name.
 */
@Composable
private fun StiChip(
    sti: STI,
    compact: Boolean = false,
    chipColor: Color? = null,
) {
    val stiName =
        when (sti) {
            STI.HIV -> stringResource(Res.string.sti_hiv)
            STI.SYPHILIS -> stringResource(Res.string.sti_syphilis)
            STI.GONORRHEA -> stringResource(Res.string.sti_gonorrhea)
            STI.CHLAMYDIA -> stringResource(Res.string.sti_chlamydia)
            STI.HPV -> stringResource(Res.string.sti_hpv)
            STI.HERPES -> stringResource(Res.string.sti_herpes)
            STI.OTHER -> stringResource(Res.string.sti_other)
        }

    val backgroundColor = chipColor ?: MaterialTheme.colorScheme.error

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .padding(
                    horizontal = if (compact) 8.dp else 12.dp,
                    vertical = if (compact) 4.dp else 6.dp,
                ),
    ) {
        Text(
            text = stiName,
            style =
                if (compact) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
            color = MaterialTheme.colorScheme.onError,
        )
    }
}
