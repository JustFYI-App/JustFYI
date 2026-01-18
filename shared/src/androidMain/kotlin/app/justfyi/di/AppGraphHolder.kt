package app.justfyi.di

/**
 * Singleton holder for the AppGraph instance.
 * This is initialized by JustFyiApplication in the androidApp module
 * and accessed by App composable in the shared module.
 *
 * This resolves the circular dependency between shared and androidApp modules:
 * - shared module defines AppGraph and this holder
 * - androidApp module creates AppGraph and initializes this holder
 * - shared module's App composable accesses the graph via this holder
 */
object AppGraphHolder {
    private var _graph: AppGraph? = null

    val graph: AppGraph
        get() =
            _graph ?: throw IllegalStateException(
                "AppGraph not initialized. JustFyiApplication.onCreate() must be called first.",
            )

    fun initialize(appGraph: AppGraph) {
        _graph = appGraph
    }

    fun getNavigationGraph(): NavigationGraph = graph

    val isInitialized: Boolean
        get() = _graph != null
}
