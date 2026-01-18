/**
 * deleteExposureReport Cloud Function
 *
 * Callable function that allows users to delete their own exposure reports.
 * When a report is deleted:
 * 1. Validates that the authenticated user owns the report (by comparing reporterId hash)
 * 2. Marks all notifications associated with this report as "deleted" (adds deletedAt field)
 * 3. Marks notifications as unread so users see the update
 * 4. Sends push notifications to inform impacted users
 *
 * SECURITY MODEL:
 * - Only the original reporter can delete their report
 * - Report ownership is validated by comparing SHA256("report:" + uid) with stored reporterId
 * - This is done server-side to prevent spoofing
 *
 * DELETION MODEL:
 * - Notifications are soft-deleted (marked with deletedAt timestamp) rather than physically deleted
 * - The original notification is updated (not a new one created) for cleaner UX
 * - Clients show the notification as "retracted" based on the deletedAt field
 *
 * OPTIMIZATION (Task 5.4):
 * - Collect all unique recipientIds from affected notifications
 * - Batch query users using getUsersByHashedNotificationIds
 * - Use results for FCM push notifications (reduced queries from N to ~N/30)
 */

import * as functionsV1 from "firebase-functions/v1";
import * as admin from "firebase-admin";
import {
  ChainVisualization,
  NotificationDocument,
  ReportDocument,
  TestStatus,
  CONSTANTS,
} from "../types";
import { getDb } from "../utils/database";
import { hashForReport, hashAnonymousId, hashForChain } from "../utils/chainPropagation";
import {
  getUsersByHashedNotificationIds,
  clearUserFcmToken,
} from "../utils/queries/userQueries";
import { parseChainPaths } from "../utils/chains/pathUtils";

/**
 * Response type for deleteExposureReport function
 */
interface DeleteExposureReportResponse {
  success: boolean;
  deletedNotificationsCount: number;
  createdNotificationsCount?: number;
  error?: string;
}

/**
 * Localization keys for FCM notifications.
 * Must match keys in client string resources.
 */
const NOTIFICATION_LOC_KEYS = {
  REPORT_DELETED: {
    titleKey: "notification_report_deleted_title",
    bodyKey: "notification_report_deleted_body",
  },
  UPDATE: {
    titleKey: "notification_update_title",
    bodyKey: "notification_update_body",
  },
} as const;

/**
 * Build FCM multicast message for report deleted notification.
 *
 * @param tokens - Array of FCM tokens to send to
 * @returns FCM multicast message object
 */
function buildReportDeletedMulticastMessage(
  tokens: string[],
): admin.messaging.MulticastMessage {
  return {
    tokens,
    android: {
      priority: "high",
      notification: {
        channelId: "exposure_notifications",
        titleLocKey: NOTIFICATION_LOC_KEYS.REPORT_DELETED.titleKey,
        bodyLocKey: NOTIFICATION_LOC_KEYS.REPORT_DELETED.bodyKey,
        defaultSound: true,
      },
    },
    apns: {
      payload: {
        aps: {
          alert: {
            "title-loc-key": NOTIFICATION_LOC_KEYS.REPORT_DELETED.titleKey,
            "body-loc-key": NOTIFICATION_LOC_KEYS.REPORT_DELETED.bodyKey,
          } as admin.messaging.ApsAlert,
          sound: "default",
        },
      },
    },
    data: {
      type: "REPORT_DELETED",
    },
  };
}

/**
 * Build FCM multicast message for chain update notification.
 * Used when a negative report is deleted and chain status reverts.
 *
 * @param tokens - Array of FCM tokens to send to
 * @returns FCM multicast message object
 */
function buildUpdateMulticastMessage(
  tokens: string[],
): admin.messaging.MulticastMessage {
  return {
    tokens,
    android: {
      priority: "high",
      notification: {
        channelId: "exposure_notifications",
        titleLocKey: NOTIFICATION_LOC_KEYS.UPDATE.titleKey,
        bodyLocKey: NOTIFICATION_LOC_KEYS.UPDATE.bodyKey,
        defaultSound: true,
      },
    },
    apns: {
      payload: {
        aps: {
          alert: {
            "title-loc-key": NOTIFICATION_LOC_KEYS.UPDATE.titleKey,
            "body-loc-key": NOTIFICATION_LOC_KEYS.UPDATE.bodyKey,
          } as admin.messaging.ApsAlert,
          sound: "default",
        },
      },
    },
    data: {
      type: "UPDATE",
    },
  };
}

/**
 * Handle deletion of a NEGATIVE test report.
 *
 * When a user deletes their negative test report:
 * 1. Finds all notifications where this user appears in the chainPath
 * 2. Reverts the user's testStatus from NEGATIVE back to UNKNOWN
 * 3. Sends UPDATE push notifications to affected recipients
 * 4. Deletes the report document
 *
 * @param db - Firestore database instance
 * @param userId - The authenticated user's ID (unhashed)
 * @param reportId - The report ID to delete
 * @param reportRef - Reference to the report document
 * @returns DeleteExposureReportResponse
 */
async function handleNegativeReportDeletion(
  db: FirebaseFirestore.Firestore,
  userId: string,
  reportId: string,
  reportRef: FirebaseFirestore.DocumentReference,
): Promise<DeleteExposureReportResponse> {
  // Hash the userId to match how it's stored in chainPath
  const interactionHashedId = hashAnonymousId(userId);
  const hashedUserId = hashForChain(interactionHashedId);

  console.log(
    "[handleNegativeReportDeletion] Looking for notifications with " +
    `hashedUserId=${hashedUserId} in chainPath`
  );

  // Find all notifications where this user is in the chain path
  const notificationsSnapshot = await db
    .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
    .where("chainPath", "array-contains", hashedUserId)
    .get();

  console.log(
    `[handleNegativeReportDeletion] Found ${notificationsSnapshot.size} notifications to update`
  );

  const batch = db.batch();
  const now = Date.now();
  let updatedCount = 0;
  const recipientIds = new Set<string>();

  for (const notificationDoc of notificationsSnapshot.docs) {
    const notification = notificationDoc.data() as NotificationDocument;

    // Parse chain data
    let chainVisualization: ChainVisualization;
    try {
      chainVisualization = JSON.parse(notification.chainData);
    } catch (error) {
      console.error(
        `Failed to parse chain data for notification ${notificationDoc.id}:`,
        error
      );
      continue;
    }

    // Validate chain data structure
    if (!chainVisualization.nodes || !Array.isArray(chainVisualization.nodes)) {
      console.error(`Invalid chain data for notification ${notificationDoc.id}`);
      continue;
    }

    if (chainVisualization.nodes.length !== notification.chainPath.length) {
      console.error(
        `Chain data mismatch for notification ${notificationDoc.id}: ` +
        `nodes (${chainVisualization.nodes.length}) != chainPath (${notification.chainPath.length})`
      );
      continue;
    }

    // Revert the user's status from NEGATIVE back to UNKNOWN
    let foundMatch = false;
    const updatedNodes = chainVisualization.nodes.map((node, index) => {
      const nodeHashedUserId = notification.chainPath[index];
      if (nodeHashedUserId === hashedUserId && node.testStatus === TestStatus.NEGATIVE) {
        foundMatch = true;
        return {
          ...node,
          testStatus: TestStatus.UNKNOWN,
        };
      }
      return node;
    });

    if (!foundMatch) {
      continue; // User not found or wasn't marked NEGATIVE
    }

    // Update paths if they exist (multi-path support)
    const parsedChainPaths = parseChainPaths(
      notification.chainPaths,
      [notification.chainPath]
    );
    const updatedPaths = chainVisualization.paths?.map((path) =>
      path.map((node, index) => {
        const pathIndex = chainVisualization.paths?.indexOf(path) ?? 0;
        const chainPathsEntry = parsedChainPaths[pathIndex] || notification.chainPath;
        const nodeHashedUserId = chainPathsEntry[index];
        if (nodeHashedUserId === hashedUserId && node.testStatus === TestStatus.NEGATIVE) {
          return {
            ...node,
            testStatus: TestStatus.UNKNOWN,
          };
        }
        return node;
      })
    );

    const updatedChainData = JSON.stringify({
      nodes: updatedNodes,
      paths: updatedPaths,
    });

    batch.update(notificationDoc.ref, {
      chainData: updatedChainData,
      updatedAt: now,
      isRead: false, // Mark as unread so user sees the change
    });

    updatedCount++;
    recipientIds.add(notification.recipientId);
  }

  // Delete the report document
  batch.delete(reportRef);

  // Commit the batch
  await batch.commit();

  console.log(
    `[handleNegativeReportDeletion] Reverted ${updatedCount} notifications from NEGATIVE to UNKNOWN`
  );

  // Send UPDATE push notifications to affected recipients
  if (recipientIds.size > 0) {
    console.log(
      `[handleNegativeReportDeletion] Sending UPDATE notifications to ${recipientIds.size} recipients`
    );

    const userLookupResults = await getUsersByHashedNotificationIds(Array.from(recipientIds));

    const tokensToSend: string[] = [];
    const tokenToRecipientId = new Map<string, string>();

    for (const [recipientId, user] of userLookupResults) {
      if (user.fcmToken) {
        tokensToSend.push(user.fcmToken);
        tokenToRecipientId.set(user.fcmToken, recipientId);
      }
    }

    if (tokensToSend.length > 0) {
      const multicastLimit = 500;
      let successCount = 0;
      let failureCount = 0;

      for (let i = 0; i < tokensToSend.length; i += multicastLimit) {
        const batchTokens = tokensToSend.slice(i, i + multicastLimit);
        const message = buildUpdateMulticastMessage(batchTokens);

        try {
          const response = await admin.messaging().sendEachForMulticast(message);
          successCount += response.successCount;
          failureCount += response.failureCount;

          // Handle invalid tokens
          if (response.failureCount > 0) {
            const clearPromises: Promise<void>[] = [];

            response.responses.forEach((resp, idx) => {
              if (!resp.success) {
                const errorCode = resp.error?.code;
                if (
                  errorCode === "messaging/invalid-registration-token" ||
                  errorCode === "messaging/registration-token-not-registered"
                ) {
                  const token = batchTokens[idx];
                  const recipientId = tokenToRecipientId.get(token);
                  if (recipientId) {
                    const user = userLookupResults.get(recipientId);
                    if (user?.docRef) {
                      clearPromises.push(clearUserFcmToken(user.docRef));
                    }
                  }
                }
              }
            });

            await Promise.all(clearPromises);
          }
        } catch (multicastError) {
          console.error(
            "[handleNegativeReportDeletion] FCM multicast failed:",
            multicastError
          );
          failureCount += batchTokens.length;
        }
      }

      console.log(
        `[handleNegativeReportDeletion] FCM complete: ${successCount} success, ${failureCount} failed`
      );
    }
  }

  return {
    success: true,
    deletedNotificationsCount: updatedCount,
  };
}

/**
 * Delete an exposure report and notify impacted users.
 *
 * For POSITIVE reports:
 * - Marks all notifications associated with this report as "deleted"
 * - Sends REPORT_DELETED push notifications to impacted users
 *
 * For NEGATIVE reports:
 * - Reverts chain status from NEGATIVE back to UNKNOWN
 * - Sends UPDATE push notifications to affected users
 * - Deletes the report document
 *
 * OPTIMIZATION (Task 5.4):
 * - Collects all unique recipientIds from affected notifications
 * - Uses getUsersByHashedNotificationIds for batch lookup (max 30 per query)
 * - Sends FCM as multicast instead of individual sends
 *
 * @param data.reportId - The ID of the report to delete
 * @returns DeleteExposureReportResponse with success status and count
 */
export const deleteExposureReport = functionsV1
  .region("europe-west1")
  .https.onCall(async (data, context): Promise<DeleteExposureReportResponse> => {
    // Verify authentication
    if (!context.auth) {
      throw new functionsV1.https.HttpsError(
        "unauthenticated",
        "User must be authenticated to delete a report"
      );
    }

    const userId = context.auth.uid;
    const reportId = data.reportId as string | undefined;

    // Validate reportId parameter
    if (!reportId || typeof reportId !== "string") {
      throw new functionsV1.https.HttpsError(
        "invalid-argument",
        "reportId is required and must be a string"
      );
    }

    if (reportId.length > 100) {
      throw new functionsV1.https.HttpsError(
        "invalid-argument",
        "reportId exceeds maximum length"
      );
    }

    console.log(`User ${userId} requesting deletion of report ${reportId}`);

    const db = getDb();

    try {
      // Fetch the report document
      const reportRef = db.collection(CONSTANTS.COLLECTIONS.REPORTS).doc(reportId);
      const reportDoc = await reportRef.get();

      if (!reportDoc.exists) {
        console.log(`Report ${reportId} not found`);
        return {
          success: false,
          deletedNotificationsCount: 0,
          error: "Report not found",
        };
      }

      const reportData = reportDoc.data() as ReportDocument;

      // Validate ownership: compare SHA256("report:" + uid) with stored reporterId
      const expectedReporterId = hashForReport(userId);
      if (reportData.reporterId.toLowerCase() !== expectedReporterId.toLowerCase()) {
        console.log(
          `User ${userId} attempted to delete report ${reportId} owned by different user`
        );
        throw new functionsV1.https.HttpsError(
          "permission-denied",
          "You do not have permission to delete this report"
        );
      }

      // Check if this is a negative report - handle differently
      if (reportData.testResult === TestStatus.NEGATIVE) {
        console.log(`Deleting NEGATIVE report ${reportId}`);
        const result = await handleNegativeReportDeletion(db, userId, reportId, reportRef);
        return result;
      }

      // For POSITIVE reports, mark notifications as deleted
      console.log(`Deleting POSITIVE report ${reportId}`);

      // Find all notifications associated with this report
      const notificationsSnapshot = await db
        .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
        .where("reportId", "==", reportId)
        .get();

      const now = Date.now();
      const batch = db.batch();
      let deletedCount = 0;

      // OPTIMIZATION (Task 5.4): Collect unique recipientIds for batch lookup
      const recipientIdToNotificationIds = new Map<string, string[]>();

      // Mark existing notifications as deleted and unread
      for (const notificationDoc of notificationsSnapshot.docs) {
        const notification = notificationDoc.data() as NotificationDocument;

        // Skip if already marked as deleted
        if (notification.deletedAt) {
          continue;
        }

        // Mark notification as deleted and unread (so user sees the change)
        batch.update(notificationDoc.ref, {
          deletedAt: now,
          updatedAt: now,
          isRead: false,
        });
        deletedCount++;

        // Track for batch push notification
        const recipientId = notification.recipientId;
        if (recipientId) {
          const notifIds = recipientIdToNotificationIds.get(recipientId) || [];
          notifIds.push(notificationDoc.id);
          recipientIdToNotificationIds.set(recipientId, notifIds);
        }
      }

      // Commit the batch
      await batch.commit();

      console.log(
        `Report ${reportId} deleted by user ${userId}: ` +
        `${deletedCount} notifications marked as deleted`
      );

      // OPTIMIZATION (Task 5.4): Batch query users for FCM delivery
      // Instead of N sequential getUserByHashedNotificationId calls,
      // we do ceil(N/30) batch queries using getUsersByHashedNotificationIds
      const uniqueRecipientIds = Array.from(recipientIdToNotificationIds.keys());

      if (uniqueRecipientIds.length > 0) {
        console.log(
          `[deleteExposureReport] Batch looking up ${uniqueRecipientIds.length} unique recipients`
        );

        // Batch query all users at once
        const userLookupResults = await getUsersByHashedNotificationIds(uniqueRecipientIds);

        console.log(
          `[deleteExposureReport] Found ${userLookupResults.size} users with FCM tokens`
        );

        // Build list of valid FCM tokens
        const tokensToSend: string[] = [];
        const tokenToRecipientId = new Map<string, string>();

        for (const [recipientId, user] of userLookupResults) {
          if (user.fcmToken) {
            tokensToSend.push(user.fcmToken);
            tokenToRecipientId.set(user.fcmToken, recipientId);
          }
        }

        // Send FCM multicast if we have tokens
        if (tokensToSend.length > 0) {
          console.log(
            `[deleteExposureReport] Sending FCM multicast to ${tokensToSend.length} tokens`
          );

          // FCM multicast limit is 500 tokens per call
          const multicastLimit = 500;
          let successCount = 0;
          let failureCount = 0;

          for (let i = 0; i < tokensToSend.length; i += multicastLimit) {
            const batchTokens = tokensToSend.slice(i, i + multicastLimit);
            const message = buildReportDeletedMulticastMessage(batchTokens);

            try {
              const response = await admin.messaging().sendEachForMulticast(message);
              successCount += response.successCount;
              failureCount += response.failureCount;

              // Handle invalid tokens
              if (response.failureCount > 0) {
                const clearPromises: Promise<void>[] = [];

                response.responses.forEach((resp, idx) => {
                  if (!resp.success) {
                    const errorCode = resp.error?.code;
                    if (
                      errorCode === "messaging/invalid-registration-token" ||
                      errorCode === "messaging/registration-token-not-registered"
                    ) {
                      const token = batchTokens[idx];
                      const recipientId = tokenToRecipientId.get(token);
                      if (recipientId) {
                        const user = userLookupResults.get(recipientId);
                        if (user?.docRef) {
                          clearPromises.push(clearUserFcmToken(user.docRef));
                        }
                      }
                    }
                  }
                });

                await Promise.all(clearPromises);
              }
            } catch (multicastError) {
              console.error(
                "[deleteExposureReport] FCM multicast failed:",
                multicastError
              );
              failureCount += batchTokens.length;
            }
          }

          console.log(
            `[deleteExposureReport] FCM multicast complete: ${successCount} success, ${failureCount} failed`
          );
        }
      }

      console.log(
        `Completed deletion of report ${reportId}: ${deletedCount} notifications affected`
      );

      return {
        success: true,
        deletedNotificationsCount: deletedCount,
      };
    } catch (error) {
      // Re-throw HttpsError directly
      if (error instanceof functionsV1.https.HttpsError) {
        throw error;
      }

      console.error(`Error deleting report ${reportId}:`, error);
      throw new functionsV1.https.HttpsError(
        "internal",
        "An error occurred while deleting the report. Please try again."
      );
    }
  });
