package app.justfyi.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.zacsweers.metro.Inject

/**
 * Android implementation of PlatformContext.
 * Uses Android Context for platform-specific operations.
 */
@Inject
class AndroidPlatformContext(
    private val context: Context,
) : PlatformContext {
    override fun openUrl(url: String): Boolean =
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }

    override fun getAppVersionName(): String =
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
}
