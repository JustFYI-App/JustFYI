package app.justfyi.di

import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.data.local.ExposureReportQueries
import app.justfyi.data.local.InteractionQueries
import app.justfyi.data.local.NotificationQueries
import app.justfyi.data.local.SettingsQueries
import app.justfyi.data.local.UserQueries
import app.justfyi.data.repository.ExposureReportRepositoryImpl
import app.justfyi.data.repository.InteractionRepositoryImpl
import app.justfyi.data.repository.NotificationRepositoryImpl
import app.justfyi.data.repository.SettingsRepositoryImpl
import app.justfyi.data.repository.UserRepositoryImpl
import app.justfyi.domain.repository.ExposureReportRepository
import app.justfyi.domain.repository.InteractionRepository
import app.justfyi.domain.repository.NotificationRepository
import app.justfyi.domain.repository.SettingsRepository
import app.justfyi.domain.repository.UserRepository
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing repository instances for iOS.
 * All data repositories are scoped to DataScope for proper data layer isolation.
 *
 * This module binds repository interfaces to their implementations:
 * - UserRepository: Local-first with Firebase sync (GitLive SDK)
 * - InteractionRepository: Local-first write, background sync (GitLive SDK)
 * - NotificationRepository: Listens to Firestore, local cache (GitLive SDK)
 * - ExposureReportRepository: Triggers Cloud Function on submit (GitLive SDK)
 * - SettingsRepository: Pure local storage
 *
 * All Firebase-based repositories use the FirebaseProvider abstraction
 * which is backed by the GitLive SDK for multiplatform support.
 * The implementations are shared with Android via commonMain.
 *
 * Scope: DataScope
 * - All data repositories work with database and/or Firebase services
 * - Scoped together with IosDatabaseModule and IosFirebaseModule for consistent data access
 *
 * Note: BleRepository is NOT in this module. It is provided by IosBleModule and scoped to BleScope
 * because it coordinates BLE hardware operations rather than data persistence.
 */
@ContributesTo(DataScope::class)
interface IosRepositoryModule {
    companion object {
        /**
         * Provides UserRepository implementation.
         * Local-first with Firebase sync for user data.
         * Uses GitLive SDK via FirebaseProvider.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideUserRepository(
            userQueries: UserQueries,
            firebaseProvider: FirebaseProvider,
            dispatchers: AppCoroutineDispatchers,
        ): UserRepository = UserRepositoryImpl(userQueries, firebaseProvider, dispatchers)

        /**
         * Provides InteractionRepository implementation.
         * Local-first write, background sync to Firestore.
         * Uses GitLive SDK via FirebaseProvider.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideInteractionRepository(
            interactionQueries: InteractionQueries,
            firebaseProvider: FirebaseProvider,
            dispatchers: AppCoroutineDispatchers,
        ): InteractionRepository = InteractionRepositoryImpl(interactionQueries, firebaseProvider, dispatchers)

        /**
         * Provides NotificationRepository implementation.
         * Listens to Firestore for real-time updates, local cache for offline.
         * Uses GitLive SDK via FirebaseProvider.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideNotificationRepository(
            notificationQueries: NotificationQueries,
            firebaseProvider: FirebaseProvider,
            dispatchers: AppCoroutineDispatchers,
        ): NotificationRepository = NotificationRepositoryImpl(notificationQueries, firebaseProvider, dispatchers)

        /**
         * Provides ExposureReportRepository implementation.
         * Triggers Cloud Function on submit for notification processing.
         * Uses GitLive SDK via FirebaseProvider.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideExposureReportRepository(
            exposureReportQueries: ExposureReportQueries,
            firebaseProvider: FirebaseProvider,
            dispatchers: AppCoroutineDispatchers,
        ): ExposureReportRepository =
            ExposureReportRepositoryImpl(
                exposureReportQueries,
                firebaseProvider,
                dispatchers,
            )

        /**
         * Provides SettingsRepository implementation.
         * Pure local storage, no cloud sync.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideSettingsRepository(
            settingsQueries: SettingsQueries,
            dispatchers: AppCoroutineDispatchers,
        ): SettingsRepository = SettingsRepositoryImpl(settingsQueries, dispatchers)
    }
}
