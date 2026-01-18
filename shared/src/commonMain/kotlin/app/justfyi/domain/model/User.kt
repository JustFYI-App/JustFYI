package app.justfyi.domain.model

/**
 * Domain model representing the current user.
 * This is the authenticated anonymous user of the app.
 *
 * @property id Local database ID
 * @property anonymousId Firebase anonymous authentication ID
 * @property username User's public display name (ASCII, max 30 chars)
 * @property createdAt Timestamp when the user was created (millis since epoch)
 * @property fcmToken Firebase Cloud Messaging token for push notifications
 * @property idBackupConfirmed Whether the user has confirmed backing up their ID
 */
data class User(
    val id: String,
    val anonymousId: String,
    val username: String,
    val createdAt: Long,
    val fcmToken: String? = null,
    val idBackupConfirmed: Boolean = false,
)
