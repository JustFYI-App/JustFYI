package app.justfyi.util

import kotlin.time.Clock

/**
 * Helper function to get current time in milliseconds for tests.
 * Uses kotlin.time.Clock.System which is cross-platform compatible.
 */

fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
