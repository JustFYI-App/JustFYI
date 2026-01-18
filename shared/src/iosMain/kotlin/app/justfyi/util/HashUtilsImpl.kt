package app.justfyi.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

/**
 * iOS implementation of SHA-256 hashing using CommonCrypto/CC_SHA256.
 *
 * @param input The string to hash (UTF-8 encoded)
 * @return The SHA-256 hash as lowercase hex string
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256Impl(input: String): String {
    val inputBytes = input.encodeToByteArray()
    val digestLength = CC_SHA256_DIGEST_LENGTH
    val hash = UByteArray(digestLength)

    inputBytes.usePinned { inputPinned ->
        hash.usePinned { hashPinned ->
            CC_SHA256(
                inputPinned.addressOf(0),
                inputBytes.size.convert(),
                hashPinned.addressOf(0),
            )
        }
    }

    return hash.joinToString("") { byte ->
        byte.toString(16).padStart(2, '0')
    }
}
