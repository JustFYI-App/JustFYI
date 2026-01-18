/**
 * Batch Operation Types
 *
 * Type definitions for batch write operations used during chain propagation.
 * These interfaces enable collecting operations during traversal and committing
 * them in efficient batches.
 *
 * Task 3.4: Add batch operation interfaces
 */

import { NotificationDocument } from "../../types";

/**
 * A pending notification write operation.
 * Collected during chain traversal and committed in Firestore batches.
 */
export interface PendingNotification {
  /**
   * The notification data to write.
   * The document ID will be auto-generated when committed.
   */
  data: NotificationDocument;

  /**
   * The hashed interaction ID of the recipient.
   * Used for logging and FCM notification sending.
   */
  hashedInteractionId: string;

  /**
   * The hashed notification ID of the recipient.
   * Used for recipient identification in the notification document.
   */
  hashedNotificationId: string;
}

/**
 * A pending FCM push notification.
 * Collected during chain propagation and sent as multicast at the end.
 */
export interface PendingFCM {
  /**
   * The FCM token to send to.
   */
  token: string;

  /**
   * The notification document ID for deep linking.
   */
  notificationId: string;

  /**
   * The notification type (EXPOSURE, UPDATE, REPORT_DELETED).
   */
  type: string;

  /**
   * Title localization key for the push notification.
   */
  titleLocKey: string;

  /**
   * Body localization key for the push notification.
   */
  bodyLocKey: string;

  /**
   * Optional additional data payload.
   */
  data?: Record<string, string>;
}

/**
 * Result of a batch commit operation.
 */
export interface BatchResult {
  /**
   * Number of operations that succeeded.
   */
  successCount: number;

  /**
   * Number of operations that failed.
   */
  failureCount: number;

  /**
   * Array of created document IDs (for notification batches).
   * Index corresponds to the original pending notification index.
   */
  createdIds: (string | null)[];

  /**
   * Array of error messages for failed operations.
   * Index corresponds to the original operation index.
   */
  errors: (string | null)[];
}

/**
 * Result of an FCM multicast operation.
 */
export interface FCMBatchResult {
  /**
   * Number of notifications sent successfully.
   */
  successCount: number;

  /**
   * Number of notifications that failed.
   */
  failureCount: number;

  /**
   * Array of token indices that failed with invalid token errors.
   * These tokens should be cleared from user documents.
   */
  invalidTokenIndices: number[];
}

/**
 * Options for NotificationBatcher configuration.
 */
export interface NotificationBatcherOptions {
  /**
   * Maximum number of operations per Firestore batch.
   * Firestore limit is 500. Default: 500
   */
  maxBatchSize?: number;
}

/**
 * Options for FCMBatcher configuration.
 */
export interface FCMBatcherOptions {
  /**
   * Maximum number of tokens per multicast call.
   * FCM limit is 500. Default: 500
   */
  maxMulticastSize?: number;
}
