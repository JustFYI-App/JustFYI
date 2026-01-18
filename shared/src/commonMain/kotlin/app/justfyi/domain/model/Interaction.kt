package app.justfyi.domain.model

/**
 * Domain model representing a recorded interaction with another user.
 * The username is captured at recording time and preserved as a snapshot.
 *
 * @property id Unique identifier for this interaction
 * @property partnerAnonymousId Anonymous ID of the interaction partner
 * @property partnerUsernameSnapshot Username of the partner at recording time (not updated if they change name)
 * @property recordedAt Timestamp when the interaction was recorded (millis since epoch)
 * @property syncedToCloud Whether this interaction has been synced to Firestore
 */
data class Interaction(
    val id: String,
    val partnerAnonymousId: String,
    val partnerUsernameSnapshot: String,
    val recordedAt: Long,
    val syncedToCloud: Boolean = false,
)
