package app.justfyi.security

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.google.crypto.tink.Aead
import okio.buffer
import okio.sink
import okio.source
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

/**
 * A DataStore serializer that encrypts preferences using Tink's Aead primitive.
 *
 * Uses AES256-GCM encryption with associated data for authenticated encryption.
 * The associated data ensures the encrypted content cannot be moved between
 * different DataStore files without detection.
 */
class EncryptedPreferencesSerializer(
    private val aead: Aead,
) : Serializer<Preferences> {
    override val defaultValue: Preferences = PreferencesSerializer.defaultValue

    override suspend fun readFrom(input: InputStream): Preferences {
        val encryptedBytes = input.readBytes()

        if (encryptedBytes.isEmpty()) {
            return defaultValue
        }

        return try {
            val decryptedBytes = aead.decrypt(encryptedBytes, ASSOCIATED_DATA)
            PreferencesSerializer.readFrom(ByteArrayInputStream(decryptedBytes).source().buffer())
        } catch (e: GeneralSecurityException) {
            throw CorruptionException("Failed to decrypt preferences", e)
        }
    }

    override suspend fun writeTo(
        t: Preferences,
        output: OutputStream,
    ) {
        val byteStream = ByteArrayOutputStream()
        PreferencesSerializer.writeTo(t, byteStream.sink().buffer())

        val encryptedBytes = aead.encrypt(byteStream.toByteArray(), ASSOCIATED_DATA)
        output.write(encryptedBytes)
    }

    companion object {
        private val ASSOCIATED_DATA = "justfyi_encrypted_prefs".toByteArray(Charsets.UTF_8)
    }
}
