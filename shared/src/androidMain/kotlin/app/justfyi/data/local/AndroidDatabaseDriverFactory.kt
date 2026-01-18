package app.justfyi.data.local

import android.content.Context
import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Android implementation of DatabaseDriverFactory.
 * Uses SQLCipher for encrypted database storage.
 *
 * Security characteristics:
 * - Database is encrypted with AES-256
 * - Encryption key is stored securely via DatabaseKeyProvider
 * - Uses SQLCipher's SupportFactory for seamless integration
 * - Automatically recovers from corrupted state (e.g., after reinstall)
 *
 * @param context Application context used to create the database
 * @param keyProvider Provider for the database encryption key
 */
class AndroidDatabaseDriverFactory(
    private val context: Context,
    private val keyProvider: AndroidDatabaseKeyProvider,
) : DatabaseDriverFactory {
    /**
     * Creates an encrypted Android SQLite driver for the JustFyiDatabase.
     * The database file is stored in the app's private data directory.
     * All data is encrypted at rest using SQLCipher.
     *
     * If decryption fails (e.g., after app reinstall with stale keys),
     * automatically clears the corrupted data and creates a fresh database.
     */
    override fun createDriver(): SqlDriver =
        try {
            val driver = createEncryptedDriver()
            // Force immediate database validation to catch encryption key mismatches
            // AndroidSqliteDriver is lazy - without this, errors occur on first query
            validateDatabase(driver)
            driver
        } catch (e: Exception) {
            Log.w(TAG, "Database decryption failed, performing recovery: ${e.message}")
            // Delete corrupted database and keys, then retry with fresh state
            deleteDatabaseFile()
            keyProvider.clearAllKeys()
            createEncryptedDriver()
        }

    private fun createEncryptedDriver(): SqlDriver {
        val passphrase = keyProvider.getOrCreateKey().toByteArray()
        val factory = SupportOpenHelperFactory(passphrase)

        return AndroidSqliteDriver(
            schema = JustFyiDatabase.Schema,
            context = context,
            name = ENCRYPTED_DATABASE_NAME,
            factory = factory,
        )
    }

    /**
     * Forces the database to open and validates it can be read.
     * This ensures we catch encryption key mismatches immediately
     * rather than on the first user query.
     */
    private fun validateDatabase(driver: SqlDriver) {
        // Execute a simple query to force database open and validate encryption
        driver.executeQuery(
            identifier = null,
            sql = "SELECT 1",
            mapper = { cursor ->
                cursor.next()
                app.cash.sqldelight.db.QueryResult
                    .Value(Unit)
            },
            parameters = 0,
        )
    }

    private fun deleteDatabaseFile() {
        try {
            if (context.deleteDatabase(ENCRYPTED_DATABASE_NAME)) {
                Log.d(TAG, "Deleted corrupted database: $ENCRYPTED_DATABASE_NAME")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete database: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AndroidDbDriverFactory"
    }
}
