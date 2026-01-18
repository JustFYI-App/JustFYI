package app.justfyi.di

import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.SingleIn

/**
 * Root dependency graph for the Just FYI iOS application.
 *
 * This graph provides application-scoped dependencies that live for the entire app lifecycle.
 * It uses a multi-scope configuration with three distinct scopes:
 * - AppScope: Application-level singletons (dispatchers, logger, platform services)
 * - DataScope: Database (SQLDelight) and Firebase services (GitLive SDK)
 * - BleScope: BLE-specific components (Advertiser, Scanner, GattHandler, Repository)
 *
 * The additionalScopes parameter allows Metro to include dependencies from DataScope
 * and BleScope modules when generating the graph implementation.
 *
 * IosAppGraph extends:
 * - IosProviders: For iOS-specific platform dependency @Provides functions
 * - CoreProviders: For core dependency @Provides functions
 * - AppGraphCore: For core dependency properties
 * - NavigationGraph: For ViewModel access in JustFyiNavHost
 *
 * Metro generates the implementation for all ViewModel properties via @Inject constructors.
 * ViewModels are created fresh on each access - Compose's viewModel() function handles
 * the lifecycle caching.
 *
 * This mirrors the Android AppGraph structure for consistency across platforms.
 */
@SingleIn(AppScope::class)
@DependencyGraph(
    scope = AppScope::class,
    additionalScopes = [DataScope::class, BleScope::class],
)
interface IosAppGraph :
    IosProviders,
    CoreProviders,
    AppGraphCore,
    NavigationGraph {
    /**
     * Provides the coroutine dispatchers for the application.
     */
    override val dispatchers: AppCoroutineDispatchers

    companion object {
        /**
         * Singleton instance of the iOS app graph.
         * Initialized in MainViewController before rendering Compose UI.
         */
        private var instance: IosAppGraph? = null

        /**
         * Gets the singleton instance of the iOS app graph.
         * Throws if the graph hasn't been initialized.
         */
        fun getInstance(): IosAppGraph =
            instance ?: throw IllegalStateException(
                "IosAppGraph has not been initialized. Call initialize() first in MainViewController.",
            )

        /**
         * Initializes the iOS app graph.
         * Must be called before any DI operations, typically in MainViewController.
         *
         * @param graph The Metro-generated graph implementation
         */
        fun initialize(graph: IosAppGraph) {
            instance = graph
        }

        /**
         * Returns whether the graph has been initialized.
         */
        fun isInitialized(): Boolean = instance != null

        /**
         * Clears the graph instance. Used for testing.
         */
        fun reset() {
            instance = null
        }
    }
}
