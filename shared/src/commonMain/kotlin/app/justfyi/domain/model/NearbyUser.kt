package app.justfyi.domain.model

/**
 * Domain model representing a nearby user discovered via BLE.
 * This user is broadcasting their presence through the Just FYI BLE service.
 *
 * @property anonymousIdHash Hashed version of the user's Firebase anonymous ID
 *                           (not the actual ID for privacy)
 * @property username User's public display name as broadcast via BLE
 * @property signalStrength RSSI value indicating proximity (-30 to -100 dBm typical)
 *                          Higher values (closer to 0) mean closer proximity
 * @property lastSeen Timestamp when this user was last detected (millis since epoch)
 */
data class NearbyUser(
    val anonymousIdHash: String,
    val username: String,
    val signalStrength: Int,
    val lastSeen: Long,
) {
    /**
     * Unique identifier for this nearby user.
     * Uses the hashed ID as the primary identifier.
     */
    val id: String get() = anonymousIdHash
}
