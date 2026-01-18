package app.justfyi.data.local

/**
 * Interface for providing the database encryption key.
 * Implemented platform-specifically to use secure key storage:
 * - Android: Android Keystore + EncryptedSharedPreferences
 * - iOS: iOS Keychain
 *
 * The key is generated once on first launch and stored securely.
 * If the key is lost (app reinstall with backup exclusion), data is lost.
 * This is acceptable for privacy-sensitive health data.
 */
interface DatabaseKeyProvider {
    /**
     * Gets or generates the database encryption key.
     * The key is a passphrase string used by SQLCipher.
     *
     * @return The encryption key as a string
     */
    fun getOrCreateKey(): String
}

/**
 * Database name constant for encrypted database.
 * Using a different name than the old unencrypted database
 * to avoid migration complexity.
 */
const val ENCRYPTED_DATABASE_NAME = "justfyi_encrypted.db"
