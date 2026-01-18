package app.justfyi.data.model

/**
 * Firestore collection structure for Just FYI app.
 *
 * Collections:
 *
 * 1. `users/{anonymousId}` - Minimal user data
 *    Fields:
 *    - anonymousId: String (document ID)
 *    - username: String (public display name, ASCII, max 30 chars)
 *    - createdAt: Long (timestamp millis)
 *    - fcmToken: String? (push notification token)
 *
 * 2. `interactions/{interactionId}` - Encrypted interaction records
 *    Fields:
 *    - partnerAnonymousId: String (partner's anonymous ID)
 *    - partnerUsernameSnapshot: String (username at recording time)
 *    - recordedAt: Long (timestamp millis)
 *    - ownerId: String (user who recorded this interaction)
 *
 * 3. `notifications/{notificationId}` - Exposure notifications
 *    Fields:
 *    - recipientId: String (anonymous ID of notification recipient)
 *    - type: String ("EXPOSURE", "UPDATE")
 *    - stiType: String? (if disclosed by reporter)
 *    - exposureDate: Long? (if disclosed by reporter)
 *    - chainData: String (JSON with chain visualization data)
 *    - isRead: Boolean (read status)
 *    - receivedAt: Long (timestamp millis)
 *    - updatedAt: Long (timestamp millis)
 *
 * 4. `reports/{reportId}` - Exposure reports for processing
 *    Fields:
 *    - reporterId: String (anonymous ID of reporter)
 *    - stiTypes: String (JSON array of STI types)
 *    - testDate: Long (date of positive test)
 *    - privacyLevel: String ("FULL", "PARTIAL", "ANONYMOUS")
 *    - contactedIds: String (JSON array of partner anonymous IDs)
 *    - reportedAt: Long (timestamp millis)
 *    - status: String ("pending", "processing", "completed")
 *
 * Security Rules (to be configured in Firebase Console):
 * - Users can only read/write their own user document
 * - Interactions are readable only by the owner
 * - Notifications are writable only by Cloud Functions (service account)
 * - Reports are writable by authenticated users, readable by Cloud Functions
 */
object FirestoreCollections {
    // Collection names
    const val USERS = "users"
    const val INTERACTIONS = "interactions"
    const val NOTIFICATIONS = "notifications"
    const val REPORTS = "reports"

    /**
     * Notification document fields.
     *
     * SOURCE OF TRUTH for notification field names.
     * Used by repository implementations and backend.
     */
    object NotificationFields {
        const val RECIPIENT_ID = "recipientId"
        const val TYPE = "type"
        const val STI_TYPE = "stiType"
        const val EXPOSURE_DATE = "exposureDate"
        const val CHAIN_DATA = "chainData"
        const val IS_READ = "isRead"
        const val RECEIVED_AT = "receivedAt"
        const val UPDATED_AT = "updatedAt"
        const val DELETED_AT = "deletedAt"
    }

    /**
     * Notification types.
     *
     * SOURCE OF TRUTH for notification type constants.
     * Backend (TypeScript) must be kept in sync manually.
     * See: firebase/functions/src/types.ts -> NotificationType enum
     */
    object NotificationTypes {
        const val EXPOSURE = "EXPOSURE"
        const val UPDATE = "UPDATE"
        const val REPORT_DELETED = "REPORT_DELETED"
    }
}
