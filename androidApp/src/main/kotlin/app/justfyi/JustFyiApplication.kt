package app.justfyi

import android.app.Application
import android.util.Log
import app.justfyi.data.FcmTokenDataStore
import app.justfyi.di.AndroidProviders
import app.justfyi.di.AppGraph
import app.justfyi.di.AppGraphHolder
import app.justfyi.service.JustFyiMessagingService
import app.justfyi.util.DebugConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for the Just FYI app.
 * Handles application-level initialization including Firebase setup, DI configuration,
 * and notification channel creation.
 *
 * Uses Metro DI for dependency injection. The AppGraph is created once during
 * application startup and stored in AppGraphHolder for access by the shared module.
 */
class JustFyiApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val fcmTokenDataStore by lazy { FcmTokenDataStore(this) }

    companion object {
        private const val TAG = "JustFyiApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // Configure debug mode for the shared library
        DebugConfig.isDebugBuild = BuildConfig.DEBUG

        // Load SQLCipher native library for encrypted database
        // This must be called before any SQLCipher operations
        System.loadLibrary("sqlcipher")

        // Initialize Android providers with application context for DI
        // This must be called before any DI graph creation
        AndroidProviders.initialize(this)

        // Create the Metro DI graph and store it in AppGraphHolder
        // This makes it accessible to the shared module's App composable
        val appGraph = createGraph<AppGraph>()
        AppGraphHolder.initialize(appGraph)
        Log.d(TAG, "Metro DI graph created successfully")

        // Create notification channels for Android 8.0+
        createNotificationChannels()

        // Initialize Firebase
        initializeFirebase()
    }

    /**
     * Creates notification channels for FCM notifications.
     * This must be called before any notifications are shown.
     */
    private fun createNotificationChannels() {
        try {
            JustFyiMessagingService.createNotificationChannels(this)
            Log.d(TAG, "Notification channels created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channels", e)
        }
    }

    /**
     * Initializes Firebase services.
     * Firebase is auto-initialized via google-services.json, but we explicitly
     * call this to ensure proper setup and to retrieve the FCM token.
     */
    private fun initializeFirebase() {
        try {
            // FirebaseApp is automatically initialized by the google-services plugin
            // This check verifies the initialization was successful
            val firebaseApp = FirebaseApp.getInstance()
            Log.d(TAG, "Firebase initialized successfully: ${firebaseApp.name}")

            // Retrieve the FCM token for push notifications
            retrieveFcmToken()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Firebase initialization failed: ${e.message}", e)
            // Firebase will be initialized when google-services.json is properly configured
        }
    }

    /**
     * Retrieves the Firebase Cloud Messaging token.
     * This token is used for sending push notifications to this device.
     */
    private fun retrieveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d(TAG, "FCM token retrieved successfully")
                // Store token in DataStore for later Firestore update
                applicationScope.launch {
                    try {
                        fcmTokenDataStore.setPendingToken(token)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to store FCM token", e)
                    }
                }
            } else {
                Log.w(TAG, "FCM token retrieval failed", task.exception)
            }
        }
    }
}
