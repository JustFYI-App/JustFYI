package app.justfyi.util

import java.security.MessageDigest

/**
 * Android implementation of SHA-256 hashing using java.security.MessageDigest.
 *
 * @param input The string to hash (UTF-8 encoded)
 * @return The SHA-256 hash as lowercase hex string
 */
internal actual fun sha256Impl(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}
