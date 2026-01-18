package app.justfyi.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.Inject

/**
 * Android implementation of NotificationPermissionHandler.
 * Checks POST_NOTIFICATIONS permission for Android 13+ (TIRAMISU).
 * For earlier versions, notification permission is always granted.
 */
@Inject
class AndroidNotificationPermissionHandler(
    private val context: Context,
) : NotificationPermissionHandler {
    /**
     * Checks if notification permission is granted.
     * On Android 13+, checks POST_NOTIFICATIONS permission.
     * On earlier versions, returns true (no runtime permission needed).
     */
    override fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // No runtime permission needed before Android 13
            true
        }
}
