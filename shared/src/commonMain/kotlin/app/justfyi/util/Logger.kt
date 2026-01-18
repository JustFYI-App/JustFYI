package app.justfyi.util

/**
 * Simple logging utility for the Just FYI application.
 * Provides static logging methods for use throughout the codebase.
 */
object Logger {
    /**
     * Truncates a user ID for safe logging.
     * Shows only first 8 characters to prevent full ID exposure in logs.
     */
    fun truncateId(id: String?): String = id?.let { "${it.take(8)}..." } ?: "null"

    /**
     * Debug log method. Only logs in debug builds.
     */
    fun d(
        tag: String,
        message: String,
    ) {
        if (isDebug) {
            println("D/$tag: $message")
        }
    }

    /**
     * Debug log method with lazy message evaluation. Only logs in debug builds.
     */
    fun d(
        tag: String,
        message: () -> String,
    ) {
        if (isDebug) {
            println("D/$tag: ${message()}")
        }
    }

    /**
     * Info log method.
     */
    fun i(
        tag: String,
        message: String,
    ) {
        println("I/$tag: $message")
    }

    /**
     * Info log method with lazy message evaluation.
     */
    fun i(
        tag: String,
        message: () -> String,
    ) {
        println("I/$tag: ${message()}")
    }

    /**
     * Warning log method.
     */
    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        println("W/$tag: $message")
        throwable?.printStackTrace()
    }

    /**
     * Warning log method with lazy message evaluation.
     */
    fun w(
        tag: String,
        message: () -> String,
    ) {
        println("W/$tag: ${message()}")
    }

    /**
     * Error log method.
     */
    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        println("E/$tag: $message")
        throwable?.printStackTrace()
    }

    /**
     * Error log method with lazy message evaluation.
     */
    fun e(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        println("E/$tag: ${message()}")
        throwable?.printStackTrace()
    }
}
