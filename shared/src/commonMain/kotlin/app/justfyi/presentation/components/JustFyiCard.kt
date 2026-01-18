package app.justfyi.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.justfyi.presentation.util.minimumTouchTargetHeight

/**
 * A styled card component for the Just FYI app.
 * Provides consistent styling for content cards throughout the app.
 *
 * Touch target compliance: When onClick is provided, this card meets WCAG 2.1 AA
 * minimum 48dp touch target through its full-width layout and minimum height enforcement.
 *
 * Accessibility: This component provides:
 * - Optional mergeDescendants to group related content for screen readers
 * - Role.Button semantics when the card is clickable
 * - Proper reading order within merged groups
 * - Focusable modifier for keyboard navigation support when clickable
 *
 * @param modifier Modifier for the card
 * @param onClick Optional click handler for the card
 * @param mergeDescendants Whether to merge all content for single screen reader focus
 *                         (default: false - allows individual content focus)
 * @param content The content to display inside the card
 */
@Composable
fun JustFyiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    mergeDescendants: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    // Apply minimum touch target height only when clickable
                    if (onClick != null) Modifier.minimumTouchTargetHeight() else Modifier,
                ).clip(RoundedCornerShape(12.dp))
                .then(
                    // Apply focusable modifier for keyboard navigation when clickable
                    if (onClick != null) Modifier.focusable() else Modifier,
                ).then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
                ).then(
                    // Apply semantics with optional mergeDescendants and button role
                    Modifier.semantics(mergeDescendants = mergeDescendants) {
                        if (onClick != null) {
                            role = Role.Button
                        }
                    },
                ),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            content = content,
        )
    }
}
