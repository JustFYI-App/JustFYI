package app.justfyi.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Manages Tink keysets for encryption operations.
 *
 * Uses Android Keystore for master key protection and SharedPreferences
 * for keyset storage (encrypted by master key).
 *
 * Security characteristics:
 * - Master key stored in Android Keystore (hardware-backed on supported devices)
 * - Keyset encrypted with master key before storing in SharedPreferences
 * - Uses AES256-GCM for encryption operations
 */
class TinkKeysetManager(
    private val context: Context,
) {
    init {
        AeadConfig.register()
    }

    /**
     * Gets or creates an Aead primitive for database key encryption.
     * The keyset is stored in SharedPreferences, encrypted by Android Keystore.
     */
    fun getDatabaseKeyAead(): Aead =
        AndroidKeysetManager
            .Builder()
            .withSharedPref(
                context,
                KEYSET_NAME,
                PREF_FILE_NAME,
            ).withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)

    companion object {
        private const val KEYSET_NAME = "justfyi_db_keyset"
        private const val PREF_FILE_NAME = "justfyi_tink_keysets"
        private const val MASTER_KEY_URI = "android-keystore://justfyi_master_key"
    }
}
