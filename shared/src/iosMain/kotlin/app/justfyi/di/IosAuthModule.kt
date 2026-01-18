package app.justfyi.di

import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.domain.repository.UserRepository
import app.justfyi.domain.usecase.AuthUseCase
import app.justfyi.domain.usecase.AuthUseCaseImpl
import app.justfyi.domain.usecase.IdBackupUseCase
import app.justfyi.domain.usecase.IdBackupUseCaseImpl
import app.justfyi.domain.usecase.IosUsernameUseCaseImpl
import app.justfyi.domain.usecase.UsernameUseCase
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing authentication and user identity use cases for iOS.
 * All use cases are scoped to DataScope as they depend on data layer services.
 *
 * This module provides:
 * - AuthUseCase: Anonymous authentication and account recovery (via GitLive SDK)
 * - UsernameUseCase: Username validation and duplicate handling (via GitLive SDK)
 * - IdBackupUseCase: Mullvad-style ID backup and formatting
 *
 * Unlike the Android version, this uses FirebaseProvider instead of direct
 * FirebaseAuth/FirebaseFirestore instances.
 *
 * Scope: DataScope
 * - Authentication use cases depend on FirebaseProvider and UserRepository
 * - Username validation requires FirebaseProvider for duplicate checking
 * - Scoped with data layer for consistent dependency resolution
 */
@ContributesTo(DataScope::class)
interface IosAuthModule {
    companion object {
        /**
         * Provides AuthUseCase implementation for iOS.
         * Handles anonymous sign-in, account recovery, and session management.
         * Uses FirebaseProvider (GitLive SDK) for Firebase operations.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideAuthUseCase(
            firebaseProvider: FirebaseProvider,
            userRepository: UserRepository,
            dispatchers: AppCoroutineDispatchers,
        ): AuthUseCase = AuthUseCaseImpl(firebaseProvider, userRepository, dispatchers)

        /**
         * Provides UsernameUseCase implementation for iOS.
         * Handles username validation, duplicate detection, and emoji suffix generation.
         * Uses FirebaseProvider (GitLive SDK) for Firestore operations.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideUsernameUseCase(
            userRepository: UserRepository,
            firebaseProvider: FirebaseProvider,
            dispatchers: AppCoroutineDispatchers,
        ): UsernameUseCase = IosUsernameUseCaseImpl(userRepository, firebaseProvider, dispatchers)

        /**
         * Provides IdBackupUseCase implementation.
         * Handles ID formatting for display and parsing user input.
         * This is platform-agnostic - same implementation as Android.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideIdBackupUseCase(): IdBackupUseCase = IdBackupUseCaseImpl()
    }
}
