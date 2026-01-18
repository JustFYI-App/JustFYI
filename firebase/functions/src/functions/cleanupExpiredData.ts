/**
 * cleanupExpiredData Cloud Function
 * Task 11.6: Create cleanupExpiredData function
 *
 * Trigger: Scheduled (daily)
 * - Deletes interactions older than 180 days
 * - Deletes notifications older than 180 days
 * - Deletes reports older than 180 days
 * - Logs cleanup statistics
 */

import * as functionsV1 from "firebase-functions/v1";
import * as admin from "firebase-admin";
import { CleanupStats, CONSTANTS } from "../types";
import { getDb } from "../utils/database";

/**
 * Calculate the cutoff timestamp (180 days ago)
 */
function getCutoffTimestamp(): number {
  const msPerDay = 24 * 60 * 60 * 1000;
  return Date.now() - CONSTANTS.RETENTION_DAYS * msPerDay;
}

/**
 * Delete documents older than cutoff in batches
 */
async function deleteOldDocuments(
  db: admin.firestore.Firestore,
  collectionName: string,
  timestampField: string,
  cutoff: number,
): Promise<number> {
  let totalDeleted = 0;
  let hasMore = true;

  while (hasMore) {
    // Query for documents older than cutoff
    const snapshot = await db
      .collection(collectionName)
      .where(timestampField, "<", cutoff)
      .limit(CONSTANTS.BATCH_SIZE)
      .get();

    if (snapshot.empty) {
      hasMore = false;
      break;
    }

    // Delete in batch
    const batch = db.batch();
    snapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });

    await batch.commit();
    totalDeleted += snapshot.size;

    console.log(`Deleted ${snapshot.size} documents from ${collectionName}, total: ${totalDeleted}`);

    // If we got fewer than batch size, we're done
    if (snapshot.size < CONSTANTS.BATCH_SIZE) {
      hasMore = false;
    }
  }

  return totalDeleted;
}

/**
 * Log cleanup statistics to Firestore
 */
async function logCleanupStats(stats: CleanupStats): Promise<void> {
  const db = getDb();

  await db.collection(CONSTANTS.COLLECTIONS.CLEANUP_LOGS).add({
    ...stats,
    timestamp: admin.firestore.FieldValue.serverTimestamp(),
  });
}

/**
 * Scheduled function to cleanup expired data
 * Runs daily at 3:00 AM UTC
 */
export const cleanupExpiredData = functionsV1
  .region("europe-west1")
  .pubsub
  .schedule("0 3 * * *") // Every day at 3:00 AM UTC
  .timeZone("UTC")
  .onRun(async (_context) => {
    const db = getDb();
    const cutoff = getCutoffTimestamp();

    console.log(`Starting cleanup for data older than ${new Date(cutoff).toISOString()}`);

    const stats: CleanupStats = {
      interactionsDeleted: 0,
      notificationsDeleted: 0,
      reportsDeleted: 0,
      timestamp: Date.now(),
    };

    try {
      // Delete old interactions
      console.log("Cleaning up old interactions...");
      stats.interactionsDeleted = await deleteOldDocuments(
        db,
        CONSTANTS.COLLECTIONS.INTERACTIONS,
        "recordedAt",
        cutoff,
      );

      // Delete old notifications
      console.log("Cleaning up old notifications...");
      stats.notificationsDeleted = await deleteOldDocuments(
        db,
        CONSTANTS.COLLECTIONS.NOTIFICATIONS,
        "receivedAt",
        cutoff,
      );

      // Delete old reports
      console.log("Cleaning up old reports...");
      stats.reportsDeleted = await deleteOldDocuments(
        db,
        CONSTANTS.COLLECTIONS.REPORTS,
        "reportedAt",
        cutoff,
      );

      // Log cleanup statistics
      await logCleanupStats(stats);

      console.log("Cleanup completed:", stats);
    } catch (error) {
      console.error("Error during cleanup:", error);

      // Still log the partial stats
      await logCleanupStats({
        ...stats,
        timestamp: Date.now(),
      });

      throw error;
    }
  });

/**
 * HTTP endpoint to manually trigger cleanup (for testing/admin purposes)
 * Should be protected by appropriate authentication in production
 */
export const triggerCleanup = functionsV1
  .region("europe-west1")
  .https.onCall(async (_data, context) => {
  // Verify authentication and admin status
  if (!context.auth) {
    throw new functionsV1.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  // Require admin claim for manual cleanup trigger
  if (!context.auth.token.admin) {
    throw new functionsV1.https.HttpsError("permission-denied", "Only admins can trigger cleanup");
  }

  const db = getDb();
  const cutoff = getCutoffTimestamp();

  console.log(`Manual cleanup triggered for data older than ${new Date(cutoff).toISOString()}`);

  const stats: CleanupStats = {
    interactionsDeleted: 0,
    notificationsDeleted: 0,
    reportsDeleted: 0,
    timestamp: Date.now(),
  };

  try {
    stats.interactionsDeleted = await deleteOldDocuments(
      db,
      CONSTANTS.COLLECTIONS.INTERACTIONS,
      "recordedAt",
      cutoff,
    );

    stats.notificationsDeleted = await deleteOldDocuments(
      db,
      CONSTANTS.COLLECTIONS.NOTIFICATIONS,
      "receivedAt",
      cutoff,
    );

    stats.reportsDeleted = await deleteOldDocuments(
      db,
      CONSTANTS.COLLECTIONS.REPORTS,
      "reportedAt",
      cutoff,
    );

    await logCleanupStats(stats);

    return {
      success: true,
      stats,
    };
  } catch (error) {
    console.error("Error during manual cleanup:", error);
    throw new functionsV1.https.HttpsError(
      "internal",
      "An error occurred during cleanup. Please try again.",
    );
  }
});
