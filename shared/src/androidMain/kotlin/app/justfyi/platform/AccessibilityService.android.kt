package app.justfyi.platform

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android implementation of AccessibilityService.
 * Detects the system's reduce motion preference using platform APIs.
 *
 * On API 33+: Uses AccessibilityManager.isReduceMotionEnabled via reflection (officially supported)
 * On older APIs: Falls back to checking Settings.Global.ANIMATOR_DURATION_SCALE
 *                A value of 0f indicates animations are disabled
 */
class AndroidAccessibilityService(
    private val context: Context,
) : AccessibilityService {
    private val accessibilityManager: AccessibilityManager? by lazy {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }

    override val isReduceMotionEnabled: Boolean
        get() = checkReduceMotion()

    private fun checkReduceMotion(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+ has direct isReduceMotionEnabled property
            // Use reflection to avoid compile-time dependency on API 33
            checkReduceMotionApi33()
        } else {
            // Fallback for older APIs: check if animator duration scale is 0
            // This is set when user disables animations in developer options
            // or through accessibility settings on some devices
            checkAnimatorDurationScale()
        }

    @Suppress("PrivateApi")
    private fun checkReduceMotionApi33(): Boolean {
        return try {
            // Use reflection to call isReduceMotionEnabled() which is available on API 33+
            val manager = accessibilityManager ?: return false
            val method = AccessibilityManager::class.java.getMethod("isReduceMotionEnabled")
            method.invoke(manager) as? Boolean ?: false
        } catch (_: Exception) {
            // Fall back to animator duration scale if reflection fails
            checkAnimatorDurationScale()
        }
    }

    private fun checkAnimatorDurationScale(): Boolean =
        try {
            val animatorDurationScale =
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f,
                )
            animatorDurationScale == 0f
        } catch (_: Exception) {
            // Default to false (animations enabled) if unable to read setting
            false
        }
}

/**
 * Android implementation of rememberReduceMotionPreference composable.
 * Provides a State that reflects the current reduce motion preference.
 *
 * Note: This implementation creates a snapshot of the current preference.
 * For real-time updates, consider using a ContentObserver or
 * registering for AccessibilityManager state changes in a production app.
 */
@Composable
actual fun rememberReduceMotionPreference(): State<Boolean> {
    val context = LocalContext.current
    val service = remember { AndroidAccessibilityService(context) }

    // Create a state that reflects the current preference
    // In a production app, you might want to observe Settings changes
    // using a ContentObserver for real-time updates
    return remember { mutableStateOf(service.isReduceMotionEnabled) }
}
