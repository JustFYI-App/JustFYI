package app.justfyi.util

/**
 * Domain-separated hashing utilities for privacy protection.
 *
 * This object provides functions for hashing Firebase UIDs with domain-specific
 * salt prefixes to prevent cross-collection correlation attacks if the database
 * is breached.
 *
 * HASH SCHEME (must match backend TypeScript implementation exactly):
 * - hashForInteraction: SHA256(uid) - no salt
 * - hashForNotification: SHA256("notification:" + uid)
 * - hashForChain: SHA256("chain:" + uid)
 * - hashForReport: SHA256("report:" + uid)
 *
 * All hashes output as lowercase hex strings for consistency.
 */
object HashUtils {
    private const val NOTIFICATION_SALT = "notification:"
    private const val CHAIN_SALT = "chain:"
    private const val REPORT_SALT = "report:"

    /**
     * Hash a UID for interaction ownerId field.
     *
     * Uses NO salt prefix to maintain compatibility with BLE-exchanged partnerAnonymousId.
     * Formula: SHA256(uid)
     *
     * @param uid The raw Firebase UID
     * @return The SHA-256 hash as lowercase hex string
     */
    fun hashForInteraction(uid: String): String = sha256(uid)

    /**
     * Hash a UID for notification recipientId field.
     *
     * Uses "notification:" salt prefix to create domain-separated hash.
     * Formula: SHA256("notification:" + uid)
     *
     * @param uid The raw Firebase UID
     * @return The domain-separated SHA-256 hash as lowercase hex string
     */
    fun hashForNotification(uid: String): String = sha256(NOTIFICATION_SALT + uid)

    /**
     * Hash a UID for chainPath and chainPaths fields.
     *
     * Uses "chain:" salt prefix to create domain-separated hash.
     * Formula: SHA256("chain:" + uid)
     *
     * @param uid The raw Firebase UID
     * @return The domain-separated SHA-256 hash as lowercase hex string
     */
    fun hashForChain(uid: String): String = sha256(CHAIN_SALT + uid)

    /**
     * Hash a UID for reports.reporterId field.
     *
     * Uses "report:" salt prefix to create domain-separated hash.
     * Formula: SHA256("report:" + uid)
     *
     * @param uid The raw Firebase UID
     * @return The domain-separated SHA-256 hash as lowercase hex string
     */
    fun hashForReport(uid: String): String = sha256(REPORT_SALT + uid)

    /**
     * Compute SHA-256 hash of the input string and return as lowercase hex string.
     *
     * This is implemented using platform-specific cryptographic APIs:
     * - Android: java.security.MessageDigest
     * - iOS: CommonCrypto/CC_SHA256
     *
     * @param input The string to hash (UTF-8 encoded)
     * @return The SHA-256 hash as lowercase hex string
     */
    private fun sha256(input: String): String = sha256Impl(input)
}

/**
 * Platform-specific SHA-256 implementation.
 *
 * @param input The string to hash (UTF-8 encoded)
 * @return The SHA-256 hash as lowercase hex string
 */
internal expect fun sha256Impl(input: String): String
