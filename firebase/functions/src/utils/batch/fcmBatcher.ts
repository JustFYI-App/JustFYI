/**
 * FCM Batcher Utility
 *
 * Collects FCM push notification operations during chain propagation and
 * sends them as multicast at the end. This reduces the number of individual
 * FCM send calls.
 *
 * Task 3.3: Create FCMBatcher utility
 */

import * as admin from "firebase-admin";
import {
  PendingFCM,
  FCMBatchResult,
  FCMBatcherOptions,
} from "./types";

/**
 * FCM multicast limit.
 * Each multicast call can include a maximum of 500 tokens.
 */
const FCM_MULTICAST_LIMIT = 500;

/**
 * Batcher for FCM push notifications.
 *
 * Collects FCM notifications during chain propagation and sends them
 * as multicast calls to reduce individual send operations.
 *
 * Usage:
 * ```typescript
 * const batcher = createFCMBatcher();
 *
 * // During chain propagation
 * batcher.add({
 *   token: userFcmToken,
 *   notificationId: "notif123",
 *   type: "EXPOSURE",
 *   titleLocKey: "notification_exposure_title",
 *   bodyLocKey: "notification_exposure_body",
 * });
 *
 * // At end of propagation
 * const result = await batcher.send();
 * console.log(`Sent ${result.successCount} push notifications`);
 * ```
 */
export class FCMBatcher {
  private pending: PendingFCM[];
  private maxMulticastSize: number;
  private sent: boolean;

  constructor(options: FCMBatcherOptions = {}) {
    this.pending = [];
    this.maxMulticastSize = Math.min(
      options.maxMulticastSize ?? FCM_MULTICAST_LIMIT,
      FCM_MULTICAST_LIMIT
    );
    this.sent = false;
  }

  /**
   * Add a push notification to the pending queue.
   *
   * @param notification - The pending FCM notification to add
   * @throws Error if send() has already been called
   */
  add(notification: PendingFCM): void {
    if (this.sent) {
      throw new Error(
        "FCMBatcher: Cannot add after send() has been called"
      );
    }

    // Only add if token is not empty
    if (notification.token && notification.token.trim() !== "") {
      this.pending.push(notification);
    }
  }

  /**
   * Get the number of pending notifications.
   */
  get size(): number {
    return this.pending.length;
  }

  /**
   * Check if there are any pending notifications.
   */
  get isEmpty(): boolean {
    return this.pending.length === 0;
  }

  /**
   * Get the pending notifications (read-only).
   */
  getPending(): ReadonlyArray<PendingFCM> {
    return this.pending;
  }

  /**
   * Clear all pending notifications without sending.
   */
  clear(): void {
    this.pending = [];
    this.sent = false;
  }

  /**
   * Build a multicast message for a group of pending notifications.
   * Groups notifications by their payload (titleLocKey, bodyLocKey, type)
   * and sends as multicast.
   *
   * @param notifications - Array of pending notifications with same payload
   * @returns FCM MulticastMessage object
   */
  private buildMulticastMessage(
    notifications: PendingFCM[]
  ): admin.messaging.MulticastMessage {
    // Use the first notification's payload (all in this group have same payload)
    const firstNotification = notifications[0];
    const tokens = notifications.map(n => n.token);

    return {
      tokens,
      android: {
        priority: "high",
        notification: {
          channelId: "exposure_notifications",
          titleLocKey: firstNotification.titleLocKey,
          bodyLocKey: firstNotification.bodyLocKey,
          defaultSound: true,
        },
      },
      apns: {
        payload: {
          aps: {
            alert: {
              "title-loc-key": firstNotification.titleLocKey,
              "body-loc-key": firstNotification.bodyLocKey,
            } as admin.messaging.ApsAlert,
            sound: "default",
          },
        },
      },
      // For multicast with different notificationIds, we include the type
      // Each recipient can use their notification sync to get the specific ID
      data: {
        type: firstNotification.type,
        ...(firstNotification.data || {}),
      },
    };
  }

  /**
   * Send all pending notifications as FCM multicast calls.
   *
   * Notifications are grouped by payload and sent in batches of up to 500 tokens.
   * Handles partial failures gracefully - some notifications may succeed while
   * others fail.
   *
   * @returns FCMBatchResult with success/failure counts and invalid token indices
   */
  async send(): Promise<FCMBatchResult> {
    if (this.sent) {
      console.warn("FCMBatcher: send() called multiple times");
      return {
        successCount: 0,
        failureCount: 0,
        invalidTokenIndices: [],
      };
    }

    this.sent = true;

    if (this.pending.length === 0) {
      return {
        successCount: 0,
        failureCount: 0,
        invalidTokenIndices: [],
      };
    }

    // Group notifications by payload signature
    const groups = this.groupByPayload();

    let totalSuccessCount = 0;
    let totalFailureCount = 0;
    const allInvalidTokenIndices: number[] = [];

    console.log(
      `FCMBatcher: Sending ${this.pending.length} notifications ` +
      `in ${groups.size} payload group(s)`
    );

    // Process each payload group
    for (const [payloadKey, notifications] of groups) {
      // Split into multicast batches if needed
      const batches: PendingFCM[][] = [];
      for (let i = 0; i < notifications.length; i += this.maxMulticastSize) {
        batches.push(notifications.slice(i, i + this.maxMulticastSize));
      }

      for (const batch of batches) {
        const result = await this.sendMulticastBatch(batch, payloadKey);

        totalSuccessCount += result.successCount;
        totalFailureCount += result.failureCount;

        // Convert batch-relative indices to global pending array indices
        for (const batchIndex of result.invalidTokenIndices) {
          const globalIndex = this.pending.indexOf(batch[batchIndex]);
          if (globalIndex !== -1) {
            allInvalidTokenIndices.push(globalIndex);
          }
        }
      }
    }

    console.log(
      `FCMBatcher: Send complete - ${totalSuccessCount} succeeded, ` +
      `${totalFailureCount} failed, ${allInvalidTokenIndices.length} invalid tokens`
    );

    return {
      successCount: totalSuccessCount,
      failureCount: totalFailureCount,
      invalidTokenIndices: allInvalidTokenIndices,
    };
  }

  /**
   * Group pending notifications by their payload signature.
   * This allows sending notifications with the same content as a single multicast.
   */
  private groupByPayload(): Map<string, PendingFCM[]> {
    const groups = new Map<string, PendingFCM[]>();

    for (const notification of this.pending) {
      // Create a key from the payload components
      const key = `${notification.titleLocKey}:${notification.bodyLocKey}:${notification.type}`;

      const group = groups.get(key) || [];
      group.push(notification);
      groups.set(key, group);
    }

    return groups;
  }

  /**
   * Send a single multicast batch and return the result.
   */
  private async sendMulticastBatch(
    batch: PendingFCM[],
    payloadKey: string
  ): Promise<FCMBatchResult> {
    const message = this.buildMulticastMessage(batch);

    try {
      const response = await admin.messaging().sendEachForMulticast(message);

      const invalidTokenIndices: number[] = [];

      // Check for invalid token errors
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          const errorCode = resp.error?.code;
          if (
            errorCode === "messaging/invalid-registration-token" ||
            errorCode === "messaging/registration-token-not-registered"
          ) {
            invalidTokenIndices.push(idx);
          }
        }
      });

      console.log(
        `FCMBatcher: Multicast batch for "${payloadKey}" - ` +
        `${response.successCount} success, ${response.failureCount} failed`
      );

      return {
        successCount: response.successCount,
        failureCount: response.failureCount,
        invalidTokenIndices,
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      console.error(
        `FCMBatcher: Multicast batch for "${payloadKey}" failed: ${errorMessage}`
      );

      // All notifications in this batch failed
      return {
        successCount: 0,
        failureCount: batch.length,
        invalidTokenIndices: [],
      };
    }
  }

  /**
   * Get the tokens that should be cleared due to invalid token errors.
   *
   * @param result - The FCMBatchResult from send()
   * @returns Array of tokens that should be removed from user documents
   */
  getInvalidTokens(result: FCMBatchResult): string[] {
    return result.invalidTokenIndices.map(idx => this.pending[idx]?.token).filter(Boolean);
  }
}

/**
 * Factory function to create a new FCMBatcher instance.
 *
 * @param options - Optional configuration options
 * @returns A new FCMBatcher instance
 */
export function createFCMBatcher(options?: FCMBatcherOptions): FCMBatcher {
  return new FCMBatcher(options);
}
