package app.justfyi.di

import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.data.firebase.GitLiveFirebaseProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.messaging.FirebaseMessaging
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing Firebase service instances.
 * All Firebase instances are scoped to DataScope for proper data layer isolation.
 *
 * Prerequisites:
 * - google-services.json must be properly configured in androidApp/ directory
 * - Firebase project must have Anonymous Auth, Firestore, Functions, and Messaging enabled
 * - FirebaseApp.initializeApp() must be called before accessing these instances
 *
 * This module provides two types of Firebase access:
 * 1. Native Android Firebase SDK instances (for Android-specific code)
 * 2. FirebaseProvider (GitLive SDK wrapper for multiplatform repositories)
 *
 * Configuration:
 * - Firestore uses default database
 * - Cloud Functions use EU region (europe-west1)
 *
 * Scope: DataScope
 * - Firebase services are part of the data layer (remote data sources)
 * - Scoped together with database services for consistent data access patterns
 */
@ContributesTo(DataScope::class)
interface FirebaseModule {
    companion object {
        /** Cloud Functions region (EU) */
        private const val FUNCTIONS_REGION = "europe-west1"

        /**
         * Provides the FirebaseProvider for multiplatform repository implementations.
         * Uses GitLive SDK which wraps the native Firebase SDKs.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideFirebaseProvider(): FirebaseProvider = GitLiveFirebaseProvider()

        /**
         * Provides the FirebaseAuth instance for anonymous authentication.
         * Used for user identity without requiring personal information.
         * Note: Prefer using FirebaseProvider for new code to ensure multiplatform compatibility.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        /**
         * Provides the FirebaseFirestore instance for cloud database operations.
         * Uses the default Firestore database.
         * Note: Prefer using FirebaseProvider for new code to ensure multiplatform compatibility.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideFirebaseFirestore(): FirebaseFirestore = Firebase.firestore

        /**
         * Provides the FirebaseFunctions instance for Cloud Functions invocation.
         * Uses EU region (europe-west1) for GDPR compliance.
         * Note: Prefer using FirebaseProvider for new code to ensure multiplatform compatibility.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideFirebaseFunctions(): FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION)

        /**
         * Provides the FirebaseMessaging instance for push notifications.
         * Used for receiving exposure notification alerts.
         * Note: Prefer using FirebaseProvider for new code to ensure multiplatform compatibility.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
    }
}
