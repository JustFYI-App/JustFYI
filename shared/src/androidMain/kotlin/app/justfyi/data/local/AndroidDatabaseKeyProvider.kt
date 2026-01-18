package app.justfyi.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.justfyi.security.EncryptedPreferencesSerializer
import app.justfyi.security.TinkKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64

/**
 * Android implementation of DatabaseKeyProvider.
 * Uses encrypted DataStore with Tink for secure key storage.
 *
 * Security characteristics:
 * - Uses Tink's AES256-GCM encryption
 * - Master key is stored in Android Keystore (hardware-backed on supported devices)
 * - Keyset is stored encrypted in SharedPreferences
 * - Database key is encrypted with authenticated encryption (AEAD)
 * - Key is generated using SecureRandom with 256 bits of entropy
 */
class AndroidDatabaseKeyProvider(
    private val context: Context,
) : DatabaseKeyProvider {
    private val tinkKeysetManager = TinkKeysetManager(context)

    private val dataStore: DataStore<Preferences> by lazy {
        createDataStoreWithRecovery()
    }

    private val passphraseKey = stringPreferencesKey(KEY_DATABASE_PASSPHRASE)

    /**
     * Creates the encrypted DataStore with error recovery.
     * If the encryption keyset becomes invalid (e.g., after data wipe or key rotation),
     * clears corrupted data and recreates the DataStore.
     */
    private fun createDataStoreWithRecovery(): DataStore<Preferences> =
        try {
            createEncryptedDataStore()
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted DataStore corrupted, performing recovery: ${e.message}")
            clearCorruptedData()
            createEncryptedDataStore()
        }

    private fun createEncryptedDataStore(): DataStore<Preferences> =
        DataStoreFactory.create(
            serializer = EncryptedPreferencesSerializer(tinkKeysetManager.getDatabaseKeyAead()),
            produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) },
        )

    /**
     * Clears all encryption keys and related data.
     * Used for recovery when database decryption fails (e.g., after app reinstall
     * where keys persisted in Keystore but database state is inconsistent).
     */
    fun clearAllKeys() {
        clearCorruptedData()
    }

    /**
     * Clears corrupted DataStore and keyset data.
     */
    private fun clearCorruptedData() {
        try {
            // Delete the DataStore file
            val dataStoreFile = context.preferencesDataStoreFile(DATASTORE_NAME)
            if (dataStoreFile.exists()) {
                dataStoreFile.delete()
                Log.d(TAG, "Deleted corrupted DataStore file: ${dataStoreFile.name}")
            }

            // Delete the Tink keyset SharedPreferences
            val keysetPrefsName = "justfyi_tink_keysets"
            try {
                context
                    .getSharedPreferences(keysetPrefsName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
                context.deleteSharedPreferences(keysetPrefsName)
                Log.d(TAG, "Deleted keyset prefs: $keysetPrefsName")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete keyset prefs: ${e.message}")
            }

            // Delete the Tink master key from Android Keystore
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val masterKeyAlias = "justfyi_master_key"
                if (keyStore.containsAlias(masterKeyAlias)) {
                    keyStore.deleteEntry(masterKeyAlias)
                    Log.d(TAG, "Deleted MasterKey from Keystore: $masterKeyAlias")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete MasterKey: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear corrupted data: ${e.message}")
        }
    }

    override fun getOrCreateKey(): String =
        runBlocking {
            val prefs = dataStore.data.first()
            val existingKey = prefs[passphraseKey]

            if (existingKey != null) {
                return@runBlocking existingKey
            }

            generateAndStoreKey()
        }

    private suspend fun generateAndStoreKey(): String {
        val keyBytes = ByteArray(KEY_SIZE_BYTES)
        SecureRandom().nextBytes(keyBytes)
        val newKey = Base64.getEncoder().encodeToString(keyBytes)

        dataStore.edit { prefs ->
            prefs[passphraseKey] = newKey
        }

        return newKey
    }

    companion object {
        private const val TAG = "AndroidDbKeyProvider"
        private const val DATASTORE_NAME = "justfyi_db_keys_tink"
        private const val KEY_DATABASE_PASSPHRASE = "db_passphrase"
        private const val KEY_SIZE_BYTES = 32 // 256 bits
    }
}
