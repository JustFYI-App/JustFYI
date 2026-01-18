package app.justfyi.di

import dev.zacsweers.metro.createGraph

/**
 * Factory function to create the IosAppGraph implementation.
 *
 * Uses Metro's createGraph<T>() function which generates the implementation
 * based on the @DependencyGraph annotation on IosAppGraph.
 *
 * @return The Metro-generated IosAppGraph implementation
 */
fun createIosAppGraph(): IosAppGraph = createGraph<IosAppGraph>()
