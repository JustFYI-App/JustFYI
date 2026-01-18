package app.justfyi.presentation

import app.justfyi.data.model.FirestoreCollections
import app.justfyi.domain.model.Notification
import app.justfyi.util.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for REPORT_DELETED notification UI handling.
 * Task 7.1: Write 2-4 focused tests for REPORT_DELETED notification display
 *
 * These tests verify:
 * - REPORT_DELETED notification type is handled correctly
 * - REPORT_DELETED notifications display appropriate content
 * - REPORT_DELETED notifications are distinguishable from EXPOSURE notifications
 *
 * Note: These tests focus on data structure validation and type checking.
 * Full UI rendering tests would require Compose testing framework.
 */
class NotificationReportDeletedTest {
    // ==================== Notification Type Tests ====================

    @Test
    fun `test REPORT_DELETED notification type constant exists`() {
        // Verify the constant is defined
        val reportDeletedType = FirestoreCollections.NotificationTypes.REPORT_DELETED

        // Then - should match the expected value
        assertEquals("REPORT_DELETED", reportDeletedType)
    }

    @Test
    fun `test REPORT_DELETED notification is distinguishable from EXPOSURE notification`() {
        // Given - notifications of different types
        val now = currentTimeMillis()
        val exposureNotification =
            Notification(
                id = "exposure-1",
                type = FirestoreCollections.NotificationTypes.EXPOSURE,
                stiType = "[\"HIV\"]",
                exposureDate = now - 86400000L * 7,
                chainData = "{}",
                isRead = false,
                receivedAt = now - 3600000L,
                updatedAt = now,
            )

        val reportDeletedNotification =
            Notification(
                id = "deleted-1",
                type = FirestoreCollections.NotificationTypes.REPORT_DELETED,
                stiType = "[\"HIV\"]",
                exposureDate = now - 86400000L * 7,
                chainData = "{}",
                isRead = false,
                receivedAt = now - 1800000L,
                updatedAt = now,
            )

        // Then - types should be different
        assertNotEquals(exposureNotification.type, reportDeletedNotification.type)
        assertEquals("EXPOSURE", exposureNotification.type)
        assertEquals("REPORT_DELETED", reportDeletedNotification.type)
    }

    @Test
    fun `test REPORT_DELETED notification contains STI info when privacy allows`() {
        // Given - REPORT_DELETED notification with STI disclosure (from FULL/STI_ONLY privacy)
        val now = currentTimeMillis()
        val notification =
            Notification(
                id = "deleted-2",
                type = FirestoreCollections.NotificationTypes.REPORT_DELETED,
                stiType = "[\"SYPHILIS\", \"CHLAMYDIA\"]",
                exposureDate = now - 86400000L * 14,
                chainData = "{}",
                isRead = false,
                receivedAt = now,
                updatedAt = now,
            )

        // Then - should have STI type information
        assertTrue(notification.stiType != null)
        assertTrue(notification.stiType!!.contains("SYPHILIS"))
        assertTrue(notification.stiType!!.contains("CHLAMYDIA"))
    }

    @Test
    fun `test REPORT_DELETED notification can have null STI info for anonymous privacy`() {
        // Given - REPORT_DELETED notification without STI disclosure (ANONYMOUS privacy)
        val now = currentTimeMillis()
        val notification =
            Notification(
                id = "deleted-3",
                type = FirestoreCollections.NotificationTypes.REPORT_DELETED,
                stiType = null, // Anonymous privacy - no STI disclosure
                exposureDate = null, // Anonymous privacy - no date disclosure
                chainData = "{}",
                isRead = false,
                receivedAt = now,
                updatedAt = now,
            )

        // Then - should have no STI type or exposure date
        assertEquals(null, notification.stiType)
        assertEquals(null, notification.exposureDate)
        assertEquals("REPORT_DELETED", notification.type)
    }
}
