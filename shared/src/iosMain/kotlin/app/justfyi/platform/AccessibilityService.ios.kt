package app.justfyi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * iOS implementation of AccessibilityService.
 * Uses UIAccessibility framework to detect the reduce motion preference.
 *
 * The reduce motion setting is found in iOS Settings > Accessibility > Motion > Reduce Motion.
 * When enabled, iOS reduces the motion of the user interface, including the parallax effect
 * on icons and alerts, and some apps may also reduce their animations.
 */
class IosAccessibilityService : AccessibilityService {
    override val isReduceMotionEnabled: Boolean
        get() = UIAccessibilityIsReduceMotionEnabled()
}

/**
 * iOS implementation of rememberReduceMotionPreference composable.
 * Provides a State that reflects the current reduce motion preference.
 *
 * Note: This implementation creates a snapshot of the current preference.
 * For real-time updates in a production app, consider observing
 * UIAccessibilityReduceMotionStatusDidChangeNotification.
 */
@Composable
actual fun rememberReduceMotionPreference(): State<Boolean> {
    val service = remember { IosAccessibilityService() }

    // Create a state that reflects the current preference
    // In a production app, you might want to observe
    // UIAccessibilityReduceMotionStatusDidChangeNotification for real-time updates
    return remember { mutableStateOf(service.isReduceMotionEnabled) }
}
