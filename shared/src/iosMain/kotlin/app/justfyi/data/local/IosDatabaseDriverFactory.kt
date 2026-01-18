package app.justfyi.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration

/**
 * iOS implementation of DatabaseDriverFactory.
 * Uses NativeSqliteDriver with encryption support via SQLCipher.
 *
 * Security characteristics:
 * - Database is encrypted with SQLCipher (AES-256)
 * - Encryption key is stored securely in iOS Keychain
 * - Uses iOS file protection (NSFileProtectionComplete) as additional layer
 *
 * Note: SQLCipher must be linked in the iOS project for encryption to work.
 * Without SQLCipher, the database will be created without encryption.
 *
 * @param keyProvider Provider for the database encryption key
 */
class IosDatabaseDriverFactory(
    private val keyProvider: DatabaseKeyProvider,
) : DatabaseDriverFactory {
    /**
     * Creates an encrypted Native SQLite driver for the JustFyiDatabase.
     * The database file is stored in the app's documents directory.
     * All data is encrypted at rest using SQLCipher.
     */
    override fun createDriver(): SqlDriver {
        val encryptionKey = keyProvider.getOrCreateKey()

        return NativeSqliteDriver(
            schema = JustFyiDatabase.Schema,
            name = ENCRYPTED_DATABASE_NAME,
            onConfiguration = { config ->
                config.copy(
                    extendedConfig =
                        DatabaseConfiguration.Extended(
                            foreignKeyConstraints = true,
                        ),
                    encryptionConfig =
                        DatabaseConfiguration.Encryption(
                            key = encryptionKey,
                        ),
                )
            },
        )
    }
}
