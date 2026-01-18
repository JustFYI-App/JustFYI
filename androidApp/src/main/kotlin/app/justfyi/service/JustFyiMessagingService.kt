package app.justfyi.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import app.justfyi.JustFyiApplication
import app.justfyi.R
import app.justfyi.data.model.FirestoreCollections.NotificationTypes
import app.justfyi.util.Constants.NotificationChannels
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.net.toUri

/**
 * Firebase Cloud Messaging service for handling push notifications.
 *
 * Handles:
 * - Token refresh and Firestore update
 * - Displaying notifications when app is in background
 * - Deep linking to notification detail when tapped
 */
class JustFyiMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "JustFyiMessagingService"

        // Deep link scheme for notification navigation
        private const val DEEP_LINK_SCHEME = "justfyi"
        private const val DEEP_LINK_HOST = "notification"

        // Threshold for expanding notification body text
        private const val NOTIFICATION_BODY_EXPAND_THRESHOLD = 40

        /**
         * Creates notification channels.
         * Should be called during app initialization.
         */
        fun createNotificationChannels(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Exposure notifications channel - high importance
            val exposureChannel =
                NotificationChannel(
                    NotificationChannels.EXPOSURE_NOTIFICATIONS,
                    context.getString(R.string.channel_exposure_notifications),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_exposure_notifications_description)
                    enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                }

            // Updates channel - default importance
            val updatesChannel =
                NotificationChannel(
                    NotificationChannels.UPDATES,
                    context.getString(R.string.channel_updates),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_updates_description)
                    enableVibration(false)
                    setShowBadge(true)
                }

            notificationManager.createNotificationChannel(exposureChannel)
            notificationManager.createNotificationChannel(updatesChannel)

            Log.d(TAG, "Notification channels created")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
    }

    /**
     * Called when FCM token is refreshed.
     * Updates the token in Firestore for the current user.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")

        serviceScope.launch {
            try {
                updateTokenInFirestore(token)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token in Firestore", e)
            }
        }
    }

    /**
     * Called when a message is received.
     * Handles both notification and data payloads.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        // Get data payload
        val data = remoteMessage.data
        Log.d(TAG, "FCM data payload: $data")
        Log.d(
            TAG,
            "FCM notification payload: title=${remoteMessage.notification?.title}, " +
                "body=${remoteMessage.notification?.body}",
        )

        val notificationId = data["notificationId"] ?: generateNotificationId()
        val notificationType = data["type"] ?: NotificationTypes.EXPOSURE
        Log.d(TAG, "Parsed: notificationId=$notificationId, type=$notificationType")

        // For localized notifications, remoteMessage.notification will have title/body resolved from loc keys
        // If not available, fall back to our defaults
        val title = remoteMessage.notification?.title ?: data["title"] ?: getDefaultTitle(notificationType)
        val body = remoteMessage.notification?.body ?: data["body"] ?: getDefaultBody(notificationType)

        // Display notification if app is in background
        displayNotification(notificationId, notificationType, title, body)
    }

    /**
     * Updates the FCM token in Firestore for the current user.
     */
    private suspend fun updateTokenInFirestore(token: String) {
        // This would be done through the UserRepository in a real implementation
        // For now, we'll just log the token update
        // The actual update happens when the user is authenticated
        Log.d(TAG, "FCM token ready for Firestore update")

        // Token will be retrieved and stored when user authenticates via AuthUseCase
        // Store it in DataStore temporarily for pickup by the repository
        try {
            val app = application as? JustFyiApplication
            app?.fcmTokenDataStore?.setPendingToken(token)
                ?: Log.w(TAG, "Application not available, cannot store FCM token")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store FCM token", e)
        }
    }

    /**
     * Displays a notification to the user.
     */
    private fun displayNotification(
        notificationId: String,
        type: String,
        title: String,
        body: String,
    ) {
        val channelId =
            when (type) {
                NotificationTypes.EXPOSURE -> NotificationChannels.EXPOSURE_NOTIFICATIONS
                else -> NotificationChannels.UPDATES
            }

        // Create deep link URI for notification navigation
        val deepLinkUri = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/$notificationId".toUri()

        val intent =
            Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                notificationId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notificationBuilder =
            NotificationCompat
                .Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(
                    if (type == NotificationTypes.EXPOSURE) {
                        NotificationCompat.PRIORITY_HIGH
                    } else {
                        NotificationCompat.PRIORITY_DEFAULT
                    },
                )

        // Add expanded style for longer messages
        if (body.length > NOTIFICATION_BODY_EXPAND_THRESHOLD) {
            notificationBuilder.setStyle(
                NotificationCompat.BigTextStyle().bigText(body),
            )
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId.hashCode(), notificationBuilder.build())

        Log.d(TAG, "Notification displayed: $notificationId")
    }

    private fun getDefaultTitle(type: String): String =
        when (type) {
            NotificationTypes.EXPOSURE -> getString(R.string.notification_exposure_title)
            NotificationTypes.UPDATE -> getString(R.string.notification_update_title)
            NotificationTypes.REPORT_DELETED -> getString(R.string.notification_report_deleted_title)
            else -> getString(R.string.notifications_title)
        }

    private fun getDefaultBody(type: String): String =
        when (type) {
            NotificationTypes.EXPOSURE -> getString(R.string.notification_exposure_body)
            NotificationTypes.UPDATE -> getString(R.string.notification_update_body)
            NotificationTypes.REPORT_DELETED -> getString(R.string.notification_report_deleted_body)
            else -> getString(R.string.notification_generic_body)
        }

    private fun generateNotificationId(): String = "notif_${System.currentTimeMillis()}"

    override fun onDestroy() {
        super.onDestroy()
    }
}
