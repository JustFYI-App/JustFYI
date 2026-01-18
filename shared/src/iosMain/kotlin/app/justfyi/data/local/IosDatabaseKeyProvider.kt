package app.justfyi.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.errSecDuplicateItem
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.arc4random_buf

/**
 * iOS implementation of DatabaseKeyProvider.
 * Uses iOS Keychain for secure key storage.
 *
 * Security characteristics:
 * - Key stored in iOS Keychain with kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
 * - Key is device-specific (not synced to iCloud Keychain)
 * - Key is generated using arc4random_buf for cryptographic randomness
 * - Key survives app updates but not device restore without backup
 */
@OptIn(ExperimentalForeignApi::class)
class IosDatabaseKeyProvider : DatabaseKeyProvider {
    override fun getOrCreateKey(): String {
        // Try to retrieve existing key
        val existingKey = retrieveKeyFromKeychain()
        if (existingKey != null) {
            return existingKey
        }

        // Generate new key
        val newKey = generateSecureKey()

        // Store in keychain
        storeKeyInKeychain(newKey)

        return newKey
    }

    private fun retrieveKeyFromKeychain(): String? {
        memScoped {
            val query =
                mapOf<Any?, Any?>(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to SERVICE_NAME,
                    kSecAttrAccount to ACCOUNT_NAME,
                    kSecReturnData to true,
                    kSecMatchLimit to kSecMatchLimitOne,
                )

            val result = alloc<CFTypeRefVar>()
            val status =
                SecItemCopyMatching(
                    query.toCFDictionary(),
                    result.ptr,
                )

            if (status == errSecSuccess) {
                val data = CFBridgingRelease(result.value) as? NSData
                return data?.toKotlinString()
            }

            return null
        }
    }

    private fun storeKeyInKeychain(key: String): Boolean {
        val keyData = (key as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false

        val attributes =
            mapOf<Any?, Any?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to SERVICE_NAME,
                kSecAttrAccount to ACCOUNT_NAME,
                kSecValueData to keyData,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )

        val status = SecItemAdd(attributes.toCFDictionary(), null)

        return status == errSecSuccess || status == errSecDuplicateItem
    }

    private fun generateSecureKey(): String {
        val keyBytes = ByteArray(KEY_SIZE_BYTES)
        keyBytes.usePinned { pinned ->
            arc4random_buf(pinned.addressOf(0), KEY_SIZE_BYTES.toULong())
        }
        // Convert to hex string
        return keyBytes.joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            HEX_CHARS[value shr 4].toString() + HEX_CHARS[value and 0x0F]
        }
    }

    private fun NSData.toKotlinString(): String? =
        NSString.create(data = this, encoding = NSUTF8StringEncoding) as? String

    @Suppress("UNCHECKED_CAST")
    private fun Map<Any?, Any?>.toCFDictionary(): CFDictionaryRef? =
        CFBridgingRetain(this as? Map<Any, Any>) as? CFDictionaryRef

    companion object {
        private const val SERVICE_NAME = "app.justfyi.database"
        private const val ACCOUNT_NAME = "db_passphrase"
        private const val KEY_SIZE_BYTES = 32 // 256 bits
        private const val HEX_CHARS = "0123456789abcdef"
    }
}
