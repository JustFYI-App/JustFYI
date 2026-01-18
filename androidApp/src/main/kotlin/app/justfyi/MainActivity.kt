package app.justfyi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

/**
 * Main entry point for the Just FYI Android application.
 * Handles:
 * - App initialization and edge-to-edge display
 * - Deep linking from notification taps (justfyi://notification/{id})
 *
 * Note: Notification permissions are requested during onboarding (Step 3)
 * to provide proper context and user control.
 */
class MainActivity : ComponentActivity() {
    companion object {
        private const val DEEP_LINK_SCHEME = "justfyi"
        private const val DEEP_LINK_HOST = "notification"

        // Firebase data payload key (used when notification is shown by system tray)
        private const val EXTRA_NOTIFICATION_ID = "notificationId"
    }

    // Compose state for pending navigation from notification deep link
    // Using mutableStateOf so Compose recomposes when value changes (e.g., from onNewIntent)
    private val pendingNotificationId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            App(
                initialNotificationId = pendingNotificationId.value,
                onNotificationConsumed = { pendingNotificationId.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Extracts notification ID from intent.
     *
     * Two cases:
     * 1. Foreground: Our service creates notification with deep link URI (justfyi://notification/{id})
     * 2. Background: Firebase shows notification, data payload goes to intent extras (notificationId key)
     */
    private fun handleIntent(intent: Intent?) {
        val notificationId =
            parseNotificationDeepLink(intent?.data)
                ?: intent?.getStringExtra(EXTRA_NOTIFICATION_ID)

        if (notificationId != null) {
            pendingNotificationId.value = notificationId
        }
    }

    private fun parseNotificationDeepLink(uri: Uri?): String? =
        uri
            ?.takeIf {
                it.scheme == DEEP_LINK_SCHEME && it.host == DEEP_LINK_HOST
            }?.lastPathSegment
}
