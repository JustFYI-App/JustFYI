package app.justfyi.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/**
 * Android/JVM implementation of test database factory.
 * Uses JdbcSqliteDriver with in-memory database.
 */
actual fun createTestDatabase(): JustFyiDatabase {
    val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    JustFyiDatabase.Schema.create(driver)
    return JustFyiDatabase(driver)
}
