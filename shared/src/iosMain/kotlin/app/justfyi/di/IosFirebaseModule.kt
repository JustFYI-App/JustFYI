package app.justfyi.di

import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.data.firebase.GitLiveFirebaseProvider
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing Firebase service instances for iOS.
 * All Firebase instances are scoped to DataScope for proper data layer isolation.
 *
 * Prerequisites:
 * - GoogleService-Info.plist must be included in the Xcode project
 * - Firebase project must have Anonymous Auth, Firestore, Functions, and Messaging enabled
 * - Firebase.initialize() must be called before accessing these instances
 *
 * This module provides FirebaseProvider which uses GitLive SDK for multiplatform
 * Firebase operations. Unlike Android, iOS doesn't need separate native SDK instances
 * since all operations go through the GitLive SDK wrapper.
 *
 * Scope: DataScope
 * - Firebase services are part of the data layer (remote data sources)
 * - Scoped together with database services for consistent data access patterns
 */
@ContributesTo(DataScope::class)
interface IosFirebaseModule {
    companion object {
        /**
         * Provides the FirebaseProvider for multiplatform repository implementations.
         * Uses GitLive SDK which wraps the native Firebase SDKs.
         *
         * On iOS, this provider handles:
         * - Firebase Auth (anonymous authentication)
         * - Firestore (document/collection operations)
         * - Cloud Functions (exposure notification processing)
         * - Cloud Messaging (push notifications via APNS)
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideFirebaseProvider(): FirebaseProvider = GitLiveFirebaseProvider()
    }
}
