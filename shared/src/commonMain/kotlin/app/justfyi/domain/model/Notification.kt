package app.justfyi.domain.model

/**
 * Domain model representing an exposure notification.
 * Contains chain visualization data showing the path of potential exposure.
 *
 * @property id Unique identifier for this notification
 * @property type Type of notification (e.g., "EXPOSURE", "UPDATE", "REPORT_DELETED")
 * @property stiType The STI type if disclosed by the reporter, null if anonymous
 * @property exposureDate Date of potential exposure if disclosed (millis since epoch)
 * @property chainData JSON string containing the chain visualization data
 * @property isRead Whether the notification has been read by the user
 * @property receivedAt Timestamp when the notification was received (millis since epoch)
 * @property updatedAt Timestamp when the notification was last updated (millis since epoch)
 * @property deletedAt Timestamp when the original report was retracted (millis since epoch), null if not retracted
 */
data class Notification(
    val id: String,
    val type: String,
    val stiType: String? = null,
    val exposureDate: Long? = null,
    val chainData: String,
    val isRead: Boolean = false,
    val receivedAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
