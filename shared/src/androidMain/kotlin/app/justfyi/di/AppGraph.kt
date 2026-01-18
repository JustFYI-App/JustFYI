package app.justfyi.di

import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn

/**
 * Root dependency graph for the Just FYI Android application.
 *
 * This graph provides application-scoped dependencies that live for the entire app lifecycle.
 * It uses a multi-scope configuration with three distinct scopes:
 * - AppScope: Application-level singletons (dispatchers, logger, system services)
 * - DataScope: Database (SQLDelight) and Firebase services
 * - BleScope: BLE-specific components (Advertiser, Scanner, GattServer, Repository)
 *
 * The additionalScopes parameter allows Metro to include dependencies from DataScope
 * and BleScope modules when generating the graph implementation.
 *
 * AppGraph extends:
 * - CoreProviders: For core dependency @Provides functions
 * - AppGraphCore: For core dependency properties
 * - NavigationGraph: For ViewModel access in JustFyiNavHost
 *
 * Metro generates the implementation for all ViewModel properties via @Inject constructors.
 * ViewModels are created fresh on each access - Compose's viewModel() function handles
 * the lifecycle caching.
 *
 * This follows the RevenueCat cat-paywalls-kmp pattern for multi-scope Metro DI configuration.
 */
@SingleIn(AppScope::class)
@DependencyGraph(
    scope = AppScope::class,
    additionalScopes = [DataScope::class, BleScope::class],
)
interface AppGraph :
    CoreProviders,
    AppGraphCore,
    NavigationGraph {
    /**
     * Provides the coroutine dispatchers for the application.
     */
    override val dispatchers: AppCoroutineDispatchers
}
