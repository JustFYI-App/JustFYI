/**
 * Batch Operations Utilities
 *
 * Exports batch operation utilities for optimized Firestore writes
 * and FCM push notification sending during chain propagation.
 */

// Types
export {
  PendingNotification,
  PendingFCM,
  BatchResult,
  FCMBatchResult,
  NotificationBatcherOptions,
  FCMBatcherOptions,
} from "./types";

// Notification Batcher
export {
  NotificationBatcher,
  createNotificationBatcher,
} from "./notificationBatcher";

// FCM Batcher
export {
  FCMBatcher,
  createFCMBatcher,
} from "./fcmBatcher";
