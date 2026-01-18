package app.justfyi.presentation.util

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Accessibility-related modifier extensions for ensuring WCAG 2.1 AA compliance.
 *
 * These modifiers help enforce accessibility guidelines across the app,
 * particularly around touch target sizes, interactive element requirements,
 * and keyboard focus management.
 */
object AccessibilityModifiers {
    /**
     * WCAG 2.1 AA minimum touch target size in dp.
     * This is the recommended minimum size for all interactive elements.
     */
    val MinTouchTargetSize = 48.dp
}

/**
 * Ensures the composable has a minimum touch target height of 48dp.
 * Useful for horizontal elements like list items that are already full-width
 * but may not meet the minimum height requirement.
 *
 * Example usage:
 * ```kotlin
 * Row(
 *     modifier = Modifier
 *         .fillMaxWidth()
 *         .minimumTouchTargetHeight()
 *         .clickable { /* handle click */ }
 * ) {
 *     // Row content
 * }
 * ```
 *
 * @return Modifier with minimum 48dp height constraint
 */
fun Modifier.minimumTouchTargetHeight(): Modifier =
    this.sizeIn(
        minHeight = AccessibilityModifiers.MinTouchTargetSize,
    )
