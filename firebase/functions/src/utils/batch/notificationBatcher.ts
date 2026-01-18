/**
 * Notification Batcher Utility
 *
 * Collects notification write operations during chain traversal and commits
 * them in Firestore batches (max 500 per batch) at the end of propagation.
 *
 * This reduces the number of individual Firestore writes by batching
 * notification creation operations.
 *
 * Task 3.2: Create NotificationBatcher utility
 */

import * as admin from "firebase-admin";
import { getDb } from "../database";
import { CONSTANTS } from "../../types";
import {
  PendingNotification,
  BatchResult,
  NotificationBatcherOptions,
} from "./types";

/**
 * Firestore batch size limit.
 * Each batch can contain a maximum of 500 operations.
 */
const FIRESTORE_BATCH_LIMIT = 500;

/**
 * Batcher for Firestore notification writes.
 *
 * Collects notification data during chain traversal and commits them
 * in batches to reduce write operations.
 *
 * Usage:
 * ```typescript
 * const batcher = createNotificationBatcher();
 *
 * // During chain traversal
 * batcher.add({
 *   data: notificationDoc,
 *   hashedInteractionId: "hash123",
 *   hashedNotificationId: "notif456",
 * });
 *
 * // At end of propagation
 * const result = await batcher.commit();
 * console.log(`Created ${result.successCount} notifications`);
 * ```
 */
export class NotificationBatcher {
  private pending: PendingNotification[];
  private maxBatchSize: number;
  private committed: boolean;

  constructor(options: NotificationBatcherOptions = {}) {
    this.pending = [];
    this.maxBatchSize = Math.min(
      options.maxBatchSize ?? FIRESTORE_BATCH_LIMIT,
      FIRESTORE_BATCH_LIMIT
    );
    this.committed = false;
  }

  /**
   * Add a notification to the pending queue.
   *
   * @param notification - The pending notification to add
   * @throws Error if commit() has already been called
   */
  add(notification: PendingNotification): void {
    if (this.committed) {
      throw new Error(
        "NotificationBatcher: Cannot add after commit() has been called"
      );
    }
    this.pending.push(notification);
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
  getPending(): ReadonlyArray<PendingNotification> {
    return this.pending;
  }

  /**
   * Clear all pending notifications without committing.
   */
  clear(): void {
    this.pending = [];
    this.committed = false;
  }

  /**
   * Commit all pending notifications to Firestore.
   *
   * Notifications are committed in batches of up to 500 (Firestore limit).
   * Each batch is committed atomically - either all operations in a batch
   * succeed or all fail.
   *
   * @returns BatchResult with success/failure counts and created IDs
   */
  async commit(): Promise<BatchResult> {
    if (this.committed) {
      console.warn("NotificationBatcher: commit() called multiple times");
      return {
        successCount: 0,
        failureCount: 0,
        createdIds: [],
        errors: [],
      };
    }

    this.committed = true;

    if (this.pending.length === 0) {
      return {
        successCount: 0,
        failureCount: 0,
        createdIds: [],
        errors: [],
      };
    }

    const db = getDb();
    const createdIds: (string | null)[] = new Array(this.pending.length).fill(null);
    const errors: (string | null)[] = new Array(this.pending.length).fill(null);
    let successCount = 0;
    let failureCount = 0;

    // Split pending notifications into batches
    const batches: PendingNotification[][] = [];
    for (let i = 0; i < this.pending.length; i += this.maxBatchSize) {
      batches.push(this.pending.slice(i, i + this.maxBatchSize));
    }

    console.log(
      `NotificationBatcher: Committing ${this.pending.length} notifications ` +
      `in ${batches.length} batch(es)`
    );

    // Process each batch
    for (let batchIndex = 0; batchIndex < batches.length; batchIndex++) {
      const batch = batches[batchIndex];
      const firestoreBatch = db.batch();
      const batchDocRefs: admin.firestore.DocumentReference[] = [];

      // Add all notifications in this batch to the Firestore batch
      for (const notification of batch) {
        const docRef = db
          .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
          .doc();

        firestoreBatch.set(docRef, notification.data as admin.firestore.DocumentData);
        batchDocRefs.push(docRef);
      }

      try {
        await firestoreBatch.commit();

        // Record success for all notifications in this batch
        const globalOffset = batchIndex * this.maxBatchSize;
        for (let i = 0; i < batch.length; i++) {
          createdIds[globalOffset + i] = batchDocRefs[i].id;
          successCount++;
        }

        console.log(
          `NotificationBatcher: Batch ${batchIndex + 1}/${batches.length} ` +
          `committed successfully (${batch.length} notifications)`
        );
      } catch (error) {
        // Record failure for all notifications in this batch
        const globalOffset = batchIndex * this.maxBatchSize;
        const errorMessage = error instanceof Error ? error.message : String(error);

        for (let i = 0; i < batch.length; i++) {
          errors[globalOffset + i] = errorMessage;
          failureCount++;
        }

        console.error(
          `NotificationBatcher: Batch ${batchIndex + 1}/${batches.length} ` +
          `failed: ${errorMessage}`
        );
      }
    }

    console.log(
      "NotificationBatcher: Commit complete - " +
      `${successCount} succeeded, ${failureCount} failed`
    );

    return {
      successCount,
      failureCount,
      createdIds,
      errors,
    };
  }

  /**
   * Get a mapping of hashedInteractionId to created notification ID.
   *
   * Must be called after commit().
   *
   * @param result - The BatchResult from commit()
   * @returns Map of hashedInteractionId to notificationId (or null if failed)
   */
  getCreatedIdMap(result: BatchResult): Map<string, string | null> {
    const map = new Map<string, string | null>();

    for (let i = 0; i < this.pending.length; i++) {
      map.set(this.pending[i].hashedInteractionId, result.createdIds[i]);
    }

    return map;
  }
}

/**
 * Factory function to create a new NotificationBatcher instance.
 *
 * @param options - Optional configuration options
 * @returns A new NotificationBatcher instance
 */
export function createNotificationBatcher(
  options?: NotificationBatcherOptions
): NotificationBatcher {
  return new NotificationBatcher(options);
}
