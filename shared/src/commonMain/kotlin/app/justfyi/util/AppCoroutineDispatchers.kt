package app.justfyi.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Wrapper for coroutine dispatchers used throughout the application.
 * This abstraction allows for easier testing by enabling dispatcher injection.
 */
data class AppCoroutineDispatchers(
    val io: CoroutineDispatcher,
    val main: CoroutineDispatcher,
    val default: CoroutineDispatcher,
)
