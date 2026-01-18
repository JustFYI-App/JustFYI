package app.justfyi.util

import java.util.UUID

/**
 * Android implementation of currentTimeMillis.
 */
actual fun currentTimeMillis(): Long = System.currentTimeMillis()

/**
 * Android implementation of UUID generation.
 */
actual fun generateUuid(): String = UUID.randomUUID().toString()

/**
 * Debug flag configuration for the shared library.
 * Must be set by the app at startup.
 */
object DebugConfig {
    @Volatile
    var isDebugBuild: Boolean = false
}

/**
 * Android implementation of isDebug.
 * Uses configuration set by the app since BuildConfig is not available
 * in KMP libraries with the new com.android.kotlin.multiplatform.library plugin.
 */
actual val isDebug: Boolean
    get() = DebugConfig.isDebugBuild
