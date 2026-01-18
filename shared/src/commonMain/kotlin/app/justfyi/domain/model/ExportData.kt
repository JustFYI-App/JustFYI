package app.justfyi.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing the complete user data export.
 * Contains all user data from Firestore collections for GDPR-compliant data portability.
 *
 * @property user The user's profile data (fcmToken excluded)
 * @property interactions List of recorded interactions with other users
 * @property notifications List of exposure notifications received
 * @property reports List of exposure reports submitted by the user
 */
@Serializable
data class ExportData(
    val user: ExportUser,
    val interactions: List<ExportInteraction>,
    val notifications: List<ExportNotification>,
    val reports: List<ExportReport>,
)

/**
 * Exported user data with fcmToken excluded for privacy.
 */
@Serializable
data class ExportUser(
    val anonymousId: String,
    val username: String,
    val createdAt: Long,
)

/**
 * Exported interaction data.
 */
@Serializable
data class ExportInteraction(
    val id: String,
    val partnerAnonymousId: String,
    val partnerUsernameSnapshot: String,
    val recordedAt: Long,
    val syncedToCloud: Boolean,
)

/**
 * Exported notification data.
 */
@Serializable
data class ExportNotification(
    val id: String,
    val type: String,
    val stiType: String?,
    val exposureDate: Long?,
    val chainData: String,
    val isRead: Boolean,
    val receivedAt: Long,
    val updatedAt: Long,
)

/**
 * Exported exposure report data.
 */
@Serializable
data class ExportReport(
    val id: String,
    val stiTypes: String,
    val testDate: Long,
    val privacyLevel: String,
    val reportedAt: Long,
    val syncedToCloud: Boolean,
)
