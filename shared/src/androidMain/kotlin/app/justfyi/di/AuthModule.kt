package app.justfyi.di

import app.justfyi.data.firebase.FirebaseProvider
import app.justfyi.domain.repository.UserRepository
import app.justfyi.domain.usecase.AuthUseCase
import app.justfyi.domain.usecase.AuthUseCaseImpl
import app.justfyi.domain.usecase.IdBackupUseCase
import app.justfyi.domain.usecase.IdBackupUseCaseImpl
import app.justfyi.domain.usecase.UsernameUseCase
import app.justfyi.domain.usecase.UsernameUseCaseImpl
import app.justfyi.util.AppCoroutineDispatchers
import com.google.firebase.firestore.FirebaseFirestore
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing authentication and user identity use cases.
 * All use cases are scoped to DataScope as they depend on data layer services.
 *
 * This module provides:
 * - AuthUseCase: Anonymous authentication and account recovery
 * - UsernameUseCase: Username validation and duplicate handling
 * - IdBackupUseCase: Mullvad-style ID backup and formatting
 *
 * Scope: DataScope
 * - Authentication use cases depend on FirebaseAuth and UserRepository
 * - Username validation requires Firestore for duplicate checking
 * - Scoped with data layer for consistent dependency resolution
 */
@ContributesTo(DataScope::class)
interface AuthModule {
    companion object {
        /**
         * Provides AuthUseCase implementation.
         * Handles anonymous sign-in, account recovery, and session management.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideAuthUseCase(
            firebaseProvider: FirebaseProvider,
            userRepository: UserRepository,
            dispatchers: AppCoroutineDispatchers,
        ): AuthUseCase = AuthUseCaseImpl(firebaseProvider, userRepository, dispatchers)

        /**
         * Provides UsernameUseCase implementation.
         * Handles username validation, duplicate detection, and emoji suffix generation.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideUsernameUseCase(
            userRepository: UserRepository,
            firestore: FirebaseFirestore,
            dispatchers: AppCoroutineDispatchers,
        ): UsernameUseCase = UsernameUseCaseImpl(userRepository, firestore, dispatchers)

        /**
         * Provides IdBackupUseCase implementation.
         * Handles ID formatting for display and parsing user input.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideIdBackupUseCase(): IdBackupUseCase = IdBackupUseCaseImpl()
    }
}
