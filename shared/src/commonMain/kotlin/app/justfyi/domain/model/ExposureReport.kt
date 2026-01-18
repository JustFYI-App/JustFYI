package app.justfyi.domain.model

/**
 * Domain model representing a test report submitted by a user.
 * Can be either a positive or negative test report.
 *
 * Note: The `contactedIds` field has been removed. The backend now
 * automatically determines which contacts to notify using unidirectional
 * graph traversal with 10-hop chain propagation. This prevents malicious
 * reporting where a user could claim an interaction that wasn't recorded.
 *
 * @property id Unique identifier for this report
 * @property stiTypes JSON array string of STI types being reported
 * @property testDate Date when the user received their test (millis since epoch)
 * @property privacyLevel Level of disclosure: "FULL" (disclose STI and date), "PARTIAL", or "ANONYMOUS"
 * @property reportedAt Timestamp when the report was submitted (millis since epoch)
 * @property syncedToCloud Whether this report has been synced to Firestore
 * @property testResult The result of the test: POSITIVE, NEGATIVE, or UNKNOWN
 */
data class ExposureReport(
    val id: String,
    val stiTypes: String,
    val testDate: Long,
    val privacyLevel: String,
    val reportedAt: Long,
    val syncedToCloud: Boolean = false,
    val testResult: TestStatus = TestStatus.POSITIVE,
)
