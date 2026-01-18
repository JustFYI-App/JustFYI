package app.justfyi.data.local

/**
 * Creates a JustFyiDatabase instance for testing.
 * Uses an in-memory database that is isolated per test.
 *
 * This function is implemented differently per platform:
 * - Android/JVM: Uses JdbcSqliteDriver with in-memory database
 * - iOS: Uses NativeSqliteDriver with in-memory database
 */
expect fun createTestDatabase(): JustFyiDatabase
