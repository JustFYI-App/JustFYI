/**
 * Test Report Cloud Functions
 *
 * Handles both positive and negative test reports through the unified reports collection.
 *
 * FUNCTIONS:
 * - reportPositiveTest: Submit a positive test report (triggers chain propagation)
 * - reportNegativeTest: Submit a negative test report (triggers status update propagation)
 * - getChainLinkInfo: Check for chain linking opportunity before reporting positive
 *
 * CHAIN LINKING (Task 5.4):
 * When a chain member (someone who received an exposure notification) reports positive:
 * - Check if they have existing notifications from previous exposure reports
 * - Extract the original reportId from the notification
 * - Pass linkedReportId when creating the new report
 * - This enables epidemiological tracking of infection spread through the network
 *
 * OPTIMIZATION (Task 5.3):
 * - Query user's notifications ONCE at the start
 * - Reuse for: chain link detection (findLinkedReportId) and own notification updates
 * - Removes duplicate notification query
 *
 * UNIFIED REPORTS:
 * Both positive and negative test results are stored in the reports collection
 * with a testResult field (POSITIVE or NEGATIVE) for complete history tracking.
 */

import * as functionsV1 from "firebase-functions/v1";
import {
  TestStatus,
  CONSTANTS,
  ReportDocument,
  ReportStatus,
  PrivacyLevel,
  NotificationDocument,
  NotificationType,
} from "../types";
import {
  propagatePositiveTestUpdate,
  hashForReport,
} from "../utils/chainPropagation";
import { getDb } from "../utils/database";
import { checkRateLimit } from "../utils/rateLimit";
import { getUserByUid } from "../utils/queries/userQueries";

/**
 * Find linked report ID from pre-fetched notifications.
 * Task 5.3: Extracted from findLinkedReportId to work with consolidated query results.
 *
 * @param notifications - Pre-fetched user notifications
 * @param stiType - Optional STI type to filter notifications
 * @returns The original reportId if a matching notification exists, undefined otherwise
 */
function findLinkedReportIdFromNotifications(
  notifications: FirebaseFirestore.QueryDocumentSnapshot[],
  stiType?: string,
): string | undefined {
  if (notifications.length === 0) {
    return undefined;
  }

  // Find the most recent notification that matches the STI type
  let matchingNotification: NotificationDocument | undefined;
  let mostRecentTime = 0;

  for (const doc of notifications) {
    const notification = doc.data() as NotificationDocument;

    // Only consider EXPOSURE type notifications
    if (notification.type !== NotificationType.EXPOSURE) {
      continue;
    }

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
}

/**
 * Check if notification STI types match the target STI type.
 * Handles both single string and JSON array formats.
 */
function stiTypesMatch(notificationStiType: string | undefined, targetStiType: string): boolean {
  if (!notificationStiType) {
    return true; // No STI type in notification means it matches any
  }

  let notifTypes: string[] = [];
  try {
    const parsed = JSON.parse(notificationStiType);
    notifTypes = Array.isArray(parsed) ? parsed : [notificationStiType];
  } catch {
    notifTypes = [notificationStiType];
  }

  // Check if any notification STI type matches the target (case-insensitive)
  return notifTypes.some(nst =>
    nst.toUpperCase() === targetStiType.toUpperCase()
  );
}

// Note: The old updateTestStatus Firestore trigger has been removed.
// Negative test processing is now handled directly in processExposureReport
// when it detects a report with testResult: NEGATIVE.

/**
 * Report a negative test result
 *
 * Creates a report document with testResult: NEGATIVE in the reports collection.
 * This provides a complete history of all test reports (positive and negative).
 *
 * The processExposureReport trigger will detect the NEGATIVE result and:
 * - Update the user's own notification (if notificationId provided)
 * - Propagate the negative status to downstream chain notifications
 */
export const reportNegativeTest = functionsV1
  .region("europe-west1")
  .https.onCall(async (data, context) => {
    // Verify authentication
    if (!context.auth) {
      throw new functionsV1.https.HttpsError("unauthenticated", "User must be authenticated");
    }

    const userId = context.auth.uid;

    // Rate limiting check
    const allowed = await checkRateLimit(userId, "negative_test");
    if (!allowed) {
      throw new functionsV1.https.HttpsError(
        "resource-exhausted",
        "Too many requests. Please try again later.",
      );
    }

    const stiType = data.stiType as string | undefined;
    const notificationId = data.notificationId as string | undefined;

    console.log(`User ${userId} reporting negative test result for notification ${notificationId}`);

    const db = getDb();

    try {
      // Look up user to get their stored hashes
      const userData = await getUserByUid(userId);
      if (!userData) {
        throw new functionsV1.https.HttpsError(
          "not-found",
          "User not found. Please ensure your account is set up correctly.",
        );
      }

      const reporterInteractionHashedId = userData.hashedInteractionId;
      const reporterNotificationHashedId = userData.hashedNotificationId;

      // Compute domain-separated hash for reporterId (privacy protection)
      const hashedReporterId = hashForReport(userId);

      // Format stiType as JSON array for consistency with positive reports
      // Handle case where stiType might already be a JSON array string
      let stiTypes: string;
      if (!stiType) {
        stiTypes = JSON.stringify([]);
      } else if (stiType.startsWith("[")) {
        // Already a JSON array, use as-is
        stiTypes = stiType;
      } else {
        // Single STI type, wrap in array
        stiTypes = JSON.stringify([stiType]);
      }

      const now = Date.now();

      // Create the report document with testResult: NEGATIVE
      const reportDoc: ReportDocument = {
        reporterId: hashedReporterId,
        reporterInteractionHashedId,
        reporterNotificationHashedId,
        stiTypes,
        testDate: now, // Use current time as test date for negative reports
        privacyLevel: PrivacyLevel.ANONYMOUS, // Negative reports don't need privacy level
        reportedAt: now,
        status: ReportStatus.PENDING,
        testResult: TestStatus.NEGATIVE, // Indicates this is a negative test report
        notificationId, // Link to the specific notification being responded to
      };

      // Add the report to Firestore - this triggers processExposureReport
      const docRef = await db
        .collection(CONSTANTS.COLLECTIONS.REPORTS)
        .add(reportDoc);

      console.log(`Created negative report ${docRef.id} for user ${userId}`);

      // Note: processExposureReport trigger will handle:
      // - Updating the user's own notification (if notificationId provided)
      // - Propagating negative status to downstream chain notifications
      // - Setting status to COMPLETED

      return {
        success: true,
        reportId: docRef.id,
      };
    } catch (error) {
      console.error(`Error reporting negative test for user ${userId}:`, error);
      throw new functionsV1.https.HttpsError(
        "internal",
        "An error occurred while processing your request. Please try again.",
      );
    }
  });

/**
 * Report a positive test result with chain linking
 *
 * CHAIN LINKING (Task 5.4):
 * When a chain member reports positive:
 * 1. Check if they have existing notifications from previous exposure reports
 * 2. If notification exists, extract the original reportId
 * 3. Create a new report with linkedReportId set to the original report
 * 4. This creates a linked chain for epidemiological tracking
 *
 * OPTIMIZATION (Task 5.3):
 * - Query notifications ONCE at the start: WHERE recipientId == hashedNotificationId
 * - Reuse for: chain link detection (findLinkedReportId) and own notification updates
 * - Removes the duplicate query that was previously in step 7
 *
 * @param data.stiTypes - JSON array of STI types (e.g., '["HIV", "SYPHILIS"]')
 * @param data.testDate - Timestamp of the test date
 * @param data.privacyLevel - Privacy level for the report
 */
export const reportPositiveTest = functionsV1
  .region("europe-west1")
  .https.onCall(async (data, context) => {
    // Verify authentication
    if (!context.auth) {
      throw new functionsV1.https.HttpsError("unauthenticated", "User must be authenticated");
    }

    const userId = context.auth.uid;

    // Rate limiting check
    const allowed = await checkRateLimit(userId, "positive_report");
    if (!allowed) {
      throw new functionsV1.https.HttpsError(
        "resource-exhausted",
        "Too many requests. Please try again later.",
      );
    }

    console.log(`User ${userId} reporting positive test result`);

    // Validate required fields before type casting
    if (!data.stiTypes || typeof data.stiTypes !== "string") {
      throw new functionsV1.https.HttpsError(
        "invalid-argument",
        "stiTypes is required and must be a string",
      );
    }

    // Input length validation
    if (data.stiTypes.length > CONSTANTS.MAX_INPUT_LENGTH.STI_TYPES_JSON) {
      throw new functionsV1.https.HttpsError(
        "invalid-argument",
        "stiTypes exceeds maximum length",
      );
    }

    if (!data.testDate || typeof data.testDate !== "number") {
      throw new functionsV1.https.HttpsError(
        "invalid-argument",
        "testDate is required and must be a number",
      );
    }

    // Now safe to assign after validation
    const stiTypes = data.stiTypes;
    const testDate = data.testDate;
    const privacyLevel = (data.privacyLevel as PrivacyLevel) || PrivacyLevel.ANONYMOUS;

    // Validate test date is not in the future
    if (testDate > Date.now()) {
      throw new functionsV1.https.HttpsError("invalid-argument", "testDate cannot be in the future");
    }

    // Validate test date is within retention period (180 days)
    const msPerDay = 24 * 60 * 60 * 1000;
    const retentionBoundary = Date.now() - CONSTANTS.RETENTION_DAYS * msPerDay;
    if (testDate < retentionBoundary) {
      throw new functionsV1.https.HttpsError(
        "invalid-argument",
        "testDate is older than 180 days",
      );
    }

    const db = getDb();

    try {
      // Look up user to get their stored hashes
      const userData = await getUserByUid(userId);
      if (!userData) {
        throw new functionsV1.https.HttpsError(
          "not-found",
          "User not found. Please ensure your account is set up correctly.",
        );
      }

      const reporterInteractionHashedId = userData.hashedInteractionId;
      const reporterNotificationHashedId = userData.hashedNotificationId;

      // OPTIMIZATION (Task 5.3): Query user's notifications ONCE
      // This replaces two separate queries:
      // 1. findLinkedReportId query (for chain linking)
      // 2. Own notification update query (for updating with positive status)
      console.log(`[reportPositiveTest] Querying notifications once for recipientId=${reporterNotificationHashedId}`);
      const userNotificationsSnapshot = await db
        .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
        .where("recipientId", "==", reporterNotificationHashedId)
        .get();

      console.log(`[reportPositiveTest] Found ${userNotificationsSnapshot.size} notifications for user`);

      // CHAIN LINKING (Task 5.4):
      // Use consolidated notifications for chain link detection
      // Parse the first STI type for filtering (if available)
      let primaryStiType: string | undefined;
      try {
        const parsedTypes = JSON.parse(stiTypes);
        if (Array.isArray(parsedTypes) && parsedTypes.length > 0) {
          primaryStiType = parsedTypes[0];
        }
      } catch {
        primaryStiType = stiTypes;
      }

      // Use pre-fetched notifications instead of calling findLinkedReportId
      const linkedReportId = findLinkedReportIdFromNotifications(
        userNotificationsSnapshot.docs,
        primaryStiType
      );

      if (linkedReportId) {
        console.log(`Found linked report ${linkedReportId} for user ${userId}'s positive report`);
      }

      // Create the report document with chain linking
      const now = Date.now();
      // Compute domain-separated hash for reporterId (privacy protection)
      const hashedReporterId = hashForReport(userId);
      const reportDoc: ReportDocument = {
        reporterId: hashedReporterId, // Domain-separated hash for privacy
        reporterInteractionHashedId, // For Cloud Function queries
        reporterNotificationHashedId, // For updating reporter's own notifications
        stiTypes,
        testDate,
        privacyLevel,
        reportedAt: now,
        status: ReportStatus.PENDING,
        testResult: TestStatus.POSITIVE, // Indicates this is a positive test report
        // Only include linkedReportId if it has a value (Firestore rejects undefined)
        ...(linkedReportId && { linkedReportId }),
      };

      // Add the report to Firestore - server generates the ID
      const docRef = await db
        .collection(CONSTANTS.COLLECTIONS.REPORTS)
        .add(reportDoc);

      const linkInfo = linkedReportId ? ` (linked to ${linkedReportId})` : "";
      console.log(`Created positive report ${docRef.id} for user ${userId}${linkInfo}`);

      // OPTIMIZATION (Task 5.3): Reuse pre-fetched notifications for updating own status
      // No need for a second query - use userNotificationsSnapshot from above

      // Parse the reported STI types to filter notifications
      let reportedStiTypes: string[] = [];
      try {
        const parsed = JSON.parse(stiTypes);
        if (Array.isArray(parsed)) {
          reportedStiTypes = parsed.filter((item): item is string => typeof item === "string");
        }
      } catch {
        // If parsing fails, treat stiTypes as a single type
        reportedStiTypes = [stiTypes];
      }
      console.log(`Reporter testing positive for: ${reportedStiTypes.join(", ")}`);

      let updatedCount = 0;
      let skippedCount = 0;
      for (const notifDoc of userNotificationsSnapshot.docs) {
        const notification = notifDoc.data();

        // Only update notifications that contain at least one of the reported STI types
        const notificationStiType = notification.stiType;
        if (notificationStiType) {
          // Parse notification STI types (could be single string or JSON array)
          let notificationStiTypes: string[] = [];
          try {
            const parsed = JSON.parse(notificationStiType);
            if (Array.isArray(parsed)) {
              notificationStiTypes = parsed.filter((item): item is string => typeof item === "string");
            } else {
              notificationStiTypes = [notificationStiType];
            }
          } catch {
            // Not JSON, treat as single type
            notificationStiTypes = [notificationStiType];
          }

          // Check if there's any overlap between notification STI types and reported types
          const overlappingStiTypes = notificationStiTypes.filter(nst => reportedStiTypes.includes(nst));
          if (overlappingStiTypes.length === 0) {
            console.log(`Skipping notification ${notifDoc.id} - STI types [${notificationStiTypes.join(", ")}] don't overlap with reported [${reportedStiTypes.join(", ")}]`);
            skippedCount++;
            continue;
          }
          console.log(`Notification ${notifDoc.id} overlaps on: [${overlappingStiTypes.join(", ")}]`);
        }

        // Calculate which STI types the user tested positive for (intersection)
        let testedPositiveFor: string[] = [];
        if (notificationStiType) {
          let notifTypes: string[] = [];
          try {
            const parsed = JSON.parse(notificationStiType);
            notifTypes = Array.isArray(parsed) ? parsed : [notificationStiType];
          } catch {
            notifTypes = [notificationStiType];
          }
          testedPositiveFor = notifTypes.filter(nst => reportedStiTypes.includes(nst));
        }

        if (notification?.chainData) {
          try {
            const chainVisualization = JSON.parse(notification.chainData);
            // Update the current user's node (last node with isCurrentUser=true)
            const updatedNodes = chainVisualization.nodes.map((node: { isCurrentUser?: boolean }) => {
              if (node.isCurrentUser) {
                return { ...node, testStatus: TestStatus.POSITIVE, testedPositiveFor };
              }
              return node;
            });
            // Update paths if they exist
            const updatedPaths = chainVisualization.paths?.map((path: Array<{ isCurrentUser?: boolean }>) =>
              path.map((node) => {
                if (node.isCurrentUser) {
                  return { ...node, testStatus: TestStatus.POSITIVE, testedPositiveFor };
                }
                return node;
              })
            );

            const updatedChainData = JSON.stringify({
              nodes: updatedNodes,
              paths: updatedPaths,
            });

            await notifDoc.ref.update({
              chainData: updatedChainData,
              updatedAt: now,
            });
            updatedCount++;
            console.log(`Updated notification ${notifDoc.id} (${notificationStiType}) with positive test status`);
          } catch (parseError) {
            console.error(`Failed to parse/update chainData for notification ${notifDoc.id}:`, parseError);
          }
        }
      }
      console.log(
        `Updated ${updatedCount} of user's own notifications with POSITIVE status ` +
        `(skipped ${skippedCount} non-matching STI types)`
      );

      // Update notifications where this user appears as an intermediary in the chain
      // This ensures upstream users see the updated status without creating duplicate notifications
      console.log(
        "[reportPositiveTest] Calling propagatePositiveTestUpdate " +
        `for hashedInteractionId=${reporterInteractionHashedId}`
      );
      try {
        const chainUpdatedCount = await propagatePositiveTestUpdate(
          reporterInteractionHashedId,
          stiTypes
        );
        console.log(
          `[reportPositiveTest] Updated ${chainUpdatedCount} chain notifications`
        );
      } catch (propagateError) {
        console.error(
          "[reportPositiveTest] propagatePositiveTestUpdate FAILED:",
          propagateError
        );
        throw propagateError;
      }

      // The processExposureReport trigger will handle NEW chain propagation for direct contacts

      return {
        success: true,
        reportId: docRef.id,
        linkedReportId: linkedReportId || null,
      };
    } catch (error) {
      console.error(`Error reporting positive test for user ${userId}:`, error);
      throw new functionsV1.https.HttpsError(
        "internal",
        "An error occurred while processing your request. Please try again.",
      );
    }
  });

/**
 * Get chain linking information for a user
 *
 * This endpoint allows the client to check if the current user has existing
 * exposure notifications that would result in chain linking when they report positive.
 */
export const getChainLinkInfo = functionsV1
  .region("europe-west1")
  .https.onCall(async (data, context) => {
    // Verify authentication
    if (!context.auth) {
      throw new functionsV1.https.HttpsError("unauthenticated", "User must be authenticated");
    }

    const userId = context.auth.uid;
    const stiType = data.stiType as string | undefined;

    try {
      // Look up user to get their hashedNotificationId
      const userData = await getUserByUid(userId);
      if (!userData) {
        return {
          hasExistingNotification: false,
          linkedReportId: null,
        };
      }

      // Query notifications once
      const db = getDb();
      const notificationsSnapshot = await db
        .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
        .where("recipientId", "==", userData.hashedNotificationId)
        .get();

      const linkedReportId = findLinkedReportIdFromNotifications(
        notificationsSnapshot.docs,
        stiType
      );

      return {
        hasExistingNotification: !!linkedReportId,
        linkedReportId: linkedReportId || null,
      };
    } catch (error) {
      console.error(`Error getting chain link info for user ${userId}:`, error);
      throw new functionsV1.https.HttpsError(
        "internal",
        "An error occurred while processing your request. Please try again.",
      );
    }
  });
