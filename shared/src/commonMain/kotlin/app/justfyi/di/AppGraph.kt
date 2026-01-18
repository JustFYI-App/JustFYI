package app.justfyi.di

import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers

/**
 * Common interface for core application dependencies.
 *
 * This interface defines the core dependencies that are provided by the
 * application-level dependency graph:
 * - AppCoroutineDispatchers for managing coroutine contexts
 * - Logger for application-wide logging
 *
 * The actual AppGraph with @DependencyGraph annotation is defined in the
 * platform-specific source sets (androidMain) where Metro generates the
 * implementation with all platform-specific module contributions.
 */
interface AppGraphCore {
    /**
     * Provides the coroutine dispatchers for the application.
     */
    val dispatchers: AppCoroutineDispatchers
}

/**
 * Provider module for core application dependencies.
 * Contains @Provides functions for dependencies that require custom instantiation.
 *
 * This interface contributes providers to AppScope and is extended by AppGraph.
 * Other modules can follow this pattern to contribute to their respective scopes:
 * - DatabaseModule, FirebaseModule -> DataScope
 * - BleModule -> BleScope
 */
@ContributesTo(AppScope::class)
interface CoreProviders {
    @Provides
    @SingleIn(AppScope::class)
    fun provideAppCoroutineDispatchers(): AppCoroutineDispatchers =
        AppCoroutineDispatchers(
            io = Dispatchers.Default,
            main = Dispatchers.Main,
            default = Dispatchers.Default,
        )
}
