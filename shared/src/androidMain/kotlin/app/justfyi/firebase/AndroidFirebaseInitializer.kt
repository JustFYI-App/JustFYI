package app.justfyi.firebase

import android.content.Context
import app.justfyi.util.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import dev.zacsweers.metro.Inject

/**
 * Android-specific Firebase initialization.
 * Handles Firebase setup using the google-services.json configuration.
 *
 * On Android, Firebase is typically initialized automatically by the
 * Firebase SDK when the app starts, using the google-services.json file.
 * This class provides explicit initialization for cases where manual
 * control is needed.
 */
@Inject
class AndroidFirebaseInitializer(
    private val context: Context,
) {
    private var initialized = false

    /**
     * Initializes Firebase for Android.
     * Uses the google-services.json configuration file.
     *
     * This method is idempotent - calling it multiple times has no effect
     * after the first successful initialization.
     */
    fun initialize() {
        if (initialized) {
            Logger.d(TAG, "Firebase already initialized")
            return
        }

        try {
            // GitLive Firebase SDK uses the same google-services.json as the native SDK
            // The Firebase.initialize() call is typically not needed on Android
            // as Firebase auto-initializes, but we call it for consistency
            Firebase.initialize(context)
            initialized = true
            Logger.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            // Firebase might already be initialized by the native SDK
            if (e.message?.contains("already initialized") == true ||
                e.message?.contains("FirebaseApp name [DEFAULT] already exists") == true
            ) {
                initialized = true
                Logger.d(TAG, "Firebase was already initialized by native SDK")
            } else {
                Logger.e(TAG, "Failed to initialize Firebase", e)
                throw e
            }
        }
    }

    /**
     * Returns whether Firebase has been initialized.
     */
    fun isInitialized(): Boolean = initialized

    companion object {
        private const val TAG = "AndroidFirebaseInit"
    }
}
