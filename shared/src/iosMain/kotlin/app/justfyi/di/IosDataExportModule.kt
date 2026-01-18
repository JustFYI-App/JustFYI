package app.justfyi.di

import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.domain.usecase.DataExportUseCase
import app.justfyi.domain.usecase.DataExportUseCaseImpl
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing data export components for iOS.
 * Scoped to DataScope as it depends on FirebaseProvider from the data layer.
 *
 * This module provides:
 * - DataExportUseCase: Exports all user data from Firestore via Cloud Function
 *
 * Scope: DataScope
 * - Data export depends on FirebaseProvider for Cloud Function calls
 * - Consistent with other use cases that depend on Firebase services
 *
 * Dependencies:
 * - FirebaseProvider from IosFirebaseModule (DataScope)
 * - AppCoroutineDispatchers from IosProviders (AppScope)
 */
@ContributesTo(DataScope::class)
interface IosDataExportModule {
    companion object {
        /**
         * Provides DataExportUseCase implementation for iOS.
         * Handles GDPR-compliant data export functionality:
         * - Calls the "exportUserData" Cloud Function
         * - Parses response into ExportData domain model
         * - Returns all user data from 4 Firestore collections
         *
         * Integrates with:
         * - FirebaseProvider for Cloud Function invocation
         * - AppCoroutineDispatchers for async operations
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideDataExportUseCase(
            firebaseProvider: FirebaseProvider,
            dispatchers: AppCoroutineDispatchers,
        ): DataExportUseCase =
            DataExportUseCaseImpl(
                firebaseProvider = firebaseProvider,
                dispatchers = dispatchers,
            )
    }
}
