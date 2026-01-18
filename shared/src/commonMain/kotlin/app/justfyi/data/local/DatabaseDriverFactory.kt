package app.justfyi.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * Factory interface for creating platform-specific SQLite drivers.
 * This is implemented differently on each platform (Android, iOS).
 */
interface DatabaseDriverFactory {
    /**
     * Creates a new SqlDriver instance for the JustFyiDatabase.
     * The driver should be closed when no longer needed.
     */
    fun createDriver(): SqlDriver
}

/**
 * Database name constant used across all platforms.
 */
const val DATABASE_NAME = "justfyi.db"
