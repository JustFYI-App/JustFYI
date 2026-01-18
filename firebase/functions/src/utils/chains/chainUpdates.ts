/**
 * Chain status update utilities
 *
 * Handles negative test propagation and chain linking for epidemiological tracking.
 */

import { getDb } from "../database";
import {
  ChainVisualization,
  NotificationDocument,
  NotificationType,
  TestStatus,
  CONSTANTS,
} from "../../types";
import { hashAnonymousId, hashForChain, hashForNotification } from "../crypto/hashing";
import { sendUpdateNotification } from "../pushNotification";
import { getUserByHashedNotificationId } from "../queries/userQueries";
import { stiTypesMatch, parseStiTypes } from "./stiUtils";
import { parseChainPaths } from "./pathUtils";

/**
 * Update chain data when a user in the chain tests negative
 *
 * ENHANCED FUNCTIONALITY (Task 5.2):
 * - STI type filtering: Only updates notifications that match the STI type of the negative test
 * - Push notifications: Sends notifications to downstream users about reduced risk
 * - Update indicator: Marks notifications with UPDATE type for client awareness
 *
 * @param userId - The UNHASHED user who tested negative
 * @param stiType - Optional STI type to filter updates (only update matching notifications)
 * @returns Number of notifications updated
 */
export async function propagateNegativeTestUpdate(
  userId: string,
  stiType?: string,
): Promise<number> {
  const db = getDb();
  let updatedCount = 0;

  // Hash the userId to match how it's stored in chainPath:
  // chainPath stores hashForChain(hashAnonymousId(uid)) - interaction hash wrapped with chain: prefix
  const interactionHashedId = hashAnonymousId(userId);
  const hashedUserId = hashForChain(interactionHashedId);

  // Find all notifications where this user is in the chain path
  const notificationsSnapshot = await db
    .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
    .where("chainPath", "array-contains", hashedUserId)
    .get();

  const batch = db.batch();
  const now = Date.now();
  const notificationsToNotify: Array<{ docId: string; notification: NotificationDocument }> = [];

  for (const doc of notificationsSnapshot.docs) {
    const notification = doc.data() as NotificationDocument;

    // STI TYPE FILTERING (Task 5.2):
    // Only update notifications that match the STI type of the negative test
    if (!stiTypesMatch(notification.stiType, stiType)) {
      console.log(
        `Skipping notification ${doc.id} - STI type mismatch ` +
        `(notification: ${notification.stiType}, reported: ${stiType})`,
      );
      continue;
    }

    // Parse chain data
    let chainVisualization: ChainVisualization;
    try {
      chainVisualization = JSON.parse(notification.chainData);
    } catch (error) {
      console.error(`Failed to parse chain data for notification ${doc.id}:`, error);
      console.error(`Chain data content: ${notification.chainData?.substring(0, 100)}...`);
      continue;
    }

    // Validate chain data structure before processing
    if (!chainVisualization.nodes || !Array.isArray(chainVisualization.nodes)) {
      console.error(`Invalid chain data structure for notification ${doc.id}: missing or invalid nodes array`);
      continue;
    }

    // Validate index alignment between nodes and chainPath
    if (chainVisualization.nodes.length !== notification.chainPath.length) {
      console.error(
        `Chain data mismatch for notification ${doc.id}: ` +
        `nodes length (${chainVisualization.nodes.length}) !== chainPath length (${notification.chainPath.length})`
      );
      continue;
    }

    // Find the user in the chain and update their status
    const updatedNodes = chainVisualization.nodes.map((node, index) => {
      const nodeHashedUserId = notification.chainPath[index];
      if (nodeHashedUserId === hashedUserId) {
        return {
          ...node,
          testStatus: TestStatus.NEGATIVE,
        };
      }
      return node;
    });

    // Parse chainPaths from JSON string
    const parsedChainPaths = parseChainPaths(notification.chainPaths, [notification.chainPath]);

    // Update paths if they exist (multi-path support)
    const updatedPaths = chainVisualization.paths?.map((path) =>
      path.map((node, index) => {
        const pathIndex = chainVisualization.paths?.indexOf(path) ?? 0;
        const chainPathsEntry = parsedChainPaths[pathIndex] || notification.chainPath;
        const nodeHashedUserId = chainPathsEntry[index];
        if (nodeHashedUserId === hashedUserId) {
          return {
            ...node,
            testStatus: TestStatus.NEGATIVE,
          };
        }
        return node;
      })
    );

    // Update the notification with new chain data
    const updatedChainData = JSON.stringify({
      nodes: updatedNodes,
      paths: updatedPaths,
    });

    batch.update(doc.ref, {
      chainData: updatedChainData,
      updatedAt: now,
    });

    updatedCount++;

    // Track notifications that need push notifications
    const userIndex = notification.chainPath.indexOf(hashedUserId);
    // Notify if user is an intermediary (not the original reporter or recipient)
    if (userIndex > 0 && userIndex < notification.chainPath.length - 1) {
      notificationsToNotify.push({ docId: doc.id, notification });
    }
  }

  // Commit batch update
  if (updatedCount > 0) {
    await batch.commit();
    console.log(`Updated ${updatedCount} notifications with negative test status for user ${userId} (hashed: ${hashedUserId}, STI: ${stiType || "all"})`);
  }

  // PUSH NOTIFICATIONS (Task 5.2):
  // Send update notifications to affected recipients about reduced risk
  // notification.recipientId is hashForNotification format, but sendUpdateNotification
  // expects hashedInteractionId format - so we look up the user first
  for (const { docId, notification } of notificationsToNotify) {
    const user = await getUserByHashedNotificationId(notification.recipientId);
    if (user?.hashedInteractionId) {
      await sendUpdateNotification(user.hashedInteractionId, docId);
    } else {
      console.log(`Could not find user for notification ${docId} to send push`);
    }
  }

  return updatedCount;
}

/**
 * Update chain data when a user in the chain tests positive
 *
 * When someone in a chain reports positive (not the original reporter),
 * we need to update all notifications where they appear as an intermediary
 * to show they have now tested positive.
 *
 * @param hashedInteractionId - The HASHED interaction ID (from hashAnonymousId)
 * @param stiType - Optional STI type to filter updates
 * @returns Number of notifications updated
 */
export async function propagatePositiveTestUpdate(
  hashedInteractionId: string,
  stiType?: string,
): Promise<number> {
  const db = getDb();
  let updatedCount = 0;

  // Wrap with chain prefix to match how it's stored in chainPath
  const hashedUserId = hashForChain(hashedInteractionId);

  console.log(
    `propagatePositiveTestUpdate: hashedInteractionId=${hashedInteractionId}, ` +
    `hashedUserId=${hashedUserId}, stiType=${stiType}`
  );

  // Find all notifications where this user is in the chain path
  const notificationsSnapshot = await db
    .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
    .where("chainPath", "array-contains", hashedUserId)
    .get();

  console.log(`propagatePositiveTestUpdate: Found ${notificationsSnapshot.size} notifications with user in chainPath`);

  const batch = db.batch();
  const now = Date.now();
  const notificationsToNotify: Array<{
    docId: string;
    notification: NotificationDocument;
  }> = [];

  for (const doc of notificationsSnapshot.docs) {
    const notification = doc.data() as NotificationDocument;

    // STI TYPE FILTERING: Only update notifications that match the STI type
    if (!stiTypesMatch(notification.stiType, stiType)) {
      console.log(
        `Skipping notification ${doc.id} - STI type mismatch ` +
        `(notification: ${notification.stiType}, reported: ${stiType})`,
      );
      continue;
    }

    // Parse chain data
    let chainVisualization: ChainVisualization;
    try {
      chainVisualization = JSON.parse(notification.chainData);
    } catch (error) {
      console.error(`Failed to parse chain data for notification ${doc.id}:`, error);
      continue;
    }

    // Validate chain data structure
    if (!chainVisualization.nodes || !Array.isArray(chainVisualization.nodes)) {
      console.error(`Invalid chain data for notification ${doc.id}`);
      continue;
    }

    if (chainVisualization.nodes.length !== notification.chainPath.length) {
      console.error(
        `Chain data mismatch for notification ${doc.id}: ` +
        `nodes (${chainVisualization.nodes.length}) != chainPath (${notification.chainPath.length})`
      );
      continue;
    }

    // Calculate which STI types this person tested positive for (intersection)
    // Notification has: e.g., [HIV, SYPHILIS, GONORRHEA]
    // Reporter tested positive for: e.g., [SYPHILIS, GONORRHEA, CHLAMYDIA]
    // testedPositiveFor = [SYPHILIS, GONORRHEA] (the overlap)
    let testedPositiveFor: string[] = [];
    if (notification.stiType && stiType) {
      const notifTypes = parseStiTypes(notification.stiType);
      const reportedTypes = parseStiTypes(stiType);
      testedPositiveFor = notifTypes.filter(nst =>
        reportedTypes.some(rst => rst.toUpperCase() === nst.toUpperCase())
      );
      console.log(
        `propagatePositiveTestUpdate: notification STIs=[${notifTypes.join(",")}], ` +
        `reported STIs=[${reportedTypes.join(",")}], overlap=[${testedPositiveFor.join(",")}]`
      );
    }

    // Find the user in the chain and update their status to POSITIVE
    let foundMatch = false;
    const updatedNodes = chainVisualization.nodes.map((node, index) => {
      const nodeHashedUserId = notification.chainPath[index];
      if (nodeHashedUserId === hashedUserId) {
        foundMatch = true;
        console.log(
          `propagatePositiveTestUpdate: Found match at index ${index} for notification ${doc.id}, ` +
          `updating ${node.username} from ${node.testStatus} to POSITIVE ` +
          `for [${testedPositiveFor.join(",")}]`
        );
        return {
          ...node,
          testStatus: TestStatus.POSITIVE,
          testedPositiveFor,
        };
      }
      return node;
    });

    if (!foundMatch) {
      console.log(
        `propagatePositiveTestUpdate: No match found in notification ${doc.id}. ` +
        `hashedUserId=${hashedUserId}, chainPath=${JSON.stringify(notification.chainPath)}`
      );
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
        if (nodeHashedUserId === hashedUserId) {
          return {
            ...node,
            testStatus: TestStatus.POSITIVE,
            testedPositiveFor,
          };
        }
        return node;
      })
    );

    const updatedChainData = JSON.stringify({
      nodes: updatedNodes,
      paths: updatedPaths,
    });

    batch.update(doc.ref, {
      chainData: updatedChainData,
      updatedAt: now,
    });

    updatedCount++;

    // Track notifications for push notification
    // Notify recipient if the user who tested positive is an intermediary
    const userIndex = notification.chainPath.indexOf(hashedUserId);
    if (userIndex > 0 && userIndex < notification.chainPath.length - 1) {
      notificationsToNotify.push({ docId: doc.id, notification });
    }
  }

  // Commit batch update
  if (updatedCount > 0) {
    await batch.commit();
    console.log(
      `Updated ${updatedCount} notifications with positive test status ` +
      `(hashedInteractionId: ${hashedInteractionId}, STI: ${stiType || "all"})`
    );
  }

  // Send push notifications to affected recipients about increased risk
  // notification.recipientId is hashForNotification format, but sendUpdateNotification
  // expects hashedInteractionId format - so we look up the user first
  for (const { docId, notification } of notificationsToNotify) {
    const user = await getUserByHashedNotificationId(notification.recipientId);
    if (user?.hashedInteractionId) {
      await sendUpdateNotification(user.hashedInteractionId, docId);
    } else {
      console.log(`Could not find user for notification ${docId} to send push`);
    }
  }

  return updatedCount;
}

/**
 * Find existing notifications for a user to extract linked report information.
 *
 * CHAIN LINKING (Task 5.3):
 * When a chain member (someone who received an exposure notification) reports positive,
 * we find their existing notification to extract the original reportId. This allows
 * the new report to be linked to the original chain for epidemiological tracking.
 *
 * @param userId - The UNHASHED user to find notifications for
 * @param stiType - Optional STI type to filter notifications
 * @returns The original reportId if a matching notification exists, undefined otherwise
 */
export async function findLinkedReportId(
  userId: string,
  stiType?: string,
): Promise<string | undefined> {
  const db = getDb();

  // Hash the userId with notification: prefix to match how it's stored
  const hashedUserId = hashForNotification(userId);

  try {
    // Query for notifications where this user is the recipient
    const query = db
      .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
      .where("recipientId", "==", hashedUserId)
      .where("type", "==", NotificationType.EXPOSURE);

    const snapshot = await query.get();

    if (snapshot.empty) {
      return undefined;
    }

    // Find the most recent notification that matches the STI type
    let matchingNotification: NotificationDocument | undefined;
    let mostRecentTime = 0;

    for (const doc of snapshot.docs) {
      const notification = doc.data() as NotificationDocument;

      // If STI type is specified, filter by it
      if (stiType && notification.stiType) {
        if (!stiTypesMatch(notification.stiType, stiType)) {
          continue;
        }
      }

      // Track the most recent matching notification
      if (notification.receivedAt > mostRecentTime) {
        mostRecentTime = notification.receivedAt;
        matchingNotification = notification;
      }
    }

    return matchingNotification?.reportId;
  } catch (error) {
    console.error(`Error finding linked report for user ${userId} (hashed: ${hashedUserId}):`, error);
    return undefined;
  }
}
