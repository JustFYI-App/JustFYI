package app.justfyi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Platform-agnostic interface for accessibility preference detection.
 * Implemented in platform-specific source sets.
 *
 * Android: Uses AccessibilityManager (API 33+) or Settings.Global.ANIMATOR_DURATION_SCALE (older APIs)
 * iOS: Uses UIAccessibility.isReduceMotionEnabled
 */
interface AccessibilityService {
    /**
     * Whether the user has enabled the "reduce motion" accessibility preference.
     * When true, animations should be disabled or reduced.
     *
     * Android: Checks AccessibilityManager.isReduceMotionEnabled (API 33+) or
     *          Settings.Global.ANIMATOR_DURATION_SCALE == 0f (older APIs)
     * iOS: Checks UIAccessibility.isReduceMotionEnabled
     *
     * @return true if reduce motion is enabled, false otherwise
     */
    val isReduceMotionEnabled: Boolean
}

/**
 * Composable function to remember the reduce motion preference as observable state.
 * This allows UI components to reactively update when the system preference changes.
 *
 * Usage:
 * ```
 * val reduceMotion = rememberReduceMotionPreference()
 * if (reduceMotion.value) {
 *     // Show static content
 *     StaticIcon()
 * } else {
 *     // Show animated content
 *     AnimatedIcon()
 * }
 * ```
 *
 * @return State<Boolean> that reflects the current reduce motion preference
 */
@Composable
expect fun rememberReduceMotionPreference(): State<Boolean>
