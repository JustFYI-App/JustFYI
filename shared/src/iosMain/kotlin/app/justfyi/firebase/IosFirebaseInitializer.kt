package app.justfyi.firebase

import app.justfyi.util.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import dev.zacsweers.metro.Inject

/**
 * iOS-specific Firebase initialization.
 * Handles Firebase setup using the GoogleService-Info.plist configuration.
 *
 * On iOS, Firebase requires explicit initialization before use.
 * The GoogleService-Info.plist file must be included in the Xcode project
 * and bundled with the app.
 *
 * Setup instructions:
 * 1. Download GoogleService-Info.plist from Firebase Console
 * 2. Add it to the Xcode project (iosApp target)
 * 3. Ensure it's included in the "Copy Bundle Resources" build phase
 * 4. Call initialize() before using any Firebase services
 */
class IosFirebaseInitializer
    @Inject
    constructor() {
        private var initialized = false

        /**
         * Initializes Firebase for iOS.
         * Uses the GoogleService-Info.plist configuration file.
         *
         * This method must be called before any Firebase services are used.
         * Typically called from MainViewController or AppDelegate.
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
                // GitLive Firebase SDK handles iOS initialization automatically
                // using the GoogleService-Info.plist file
                Firebase.initialize()
                initialized = true
                Logger.d(TAG, "Firebase initialized successfully")
            } catch (e: Exception) {
                // Firebase might already be initialized
                if (e.message?.contains("already configured") == true ||
                    e.message?.contains("default app has already been configured") == true
                ) {
                    initialized = true
                    Logger.d(TAG, "Firebase was already initialized")
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
            private const val TAG = "IosFirebaseInit"

            /**
             * List of required Info.plist entries for Firebase on iOS.
             * These should be documented for the Xcode project setup.
             */
            val REQUIRED_PLIST_ENTRIES =
                listOf(
                    "GoogleService-Info.plist - Firebase configuration file",
                    "CFBundleURLTypes - For Firebase dynamic links (optional)",
                    "FirebaseAppDelegateProxyEnabled - Set to NO if handling push notifications manually",
                )

            /**
             * Required capabilities in Xcode project:
             * - Push Notifications (for FCM)
             * - Background Modes > Remote notifications (for background push)
             */
            val REQUIRED_CAPABILITIES =
                listOf(
                    "Push Notifications",
                    "Background Modes > Remote notifications",
                )
        }
    }
