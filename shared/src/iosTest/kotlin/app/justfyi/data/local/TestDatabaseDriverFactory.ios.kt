package app.justfyi.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver

/**
 * iOS implementation of test database factory.
 * Uses in-memory database for fast, isolated tests.
 */
actual fun createTestDatabase(): JustFyiDatabase {
    val driver: SqlDriver = inMemoryDriver(JustFyiDatabase.Schema)
    return JustFyiDatabase(driver)
}
