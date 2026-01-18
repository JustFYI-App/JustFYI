/**
 * processExposureReport Cloud Function
 *
 * Trigger: Firestore document create in reports/
 *
 * Handles both POSITIVE and NEGATIVE test reports from the unified reports collection.
 *
 * FOR POSITIVE REPORTS:
 * - Validates report data
 * - Backend automatically discovers contacts via unidirectional query
 * - Creates notification documents for discovered contacts
 * - Initiates chain propagation for indirect contacts
 * - Updates reporter's own notifications with positive status
 * - Respects privacy options in notification content
 *
 * FOR NEGATIVE REPORTS:
 * - Updates the reporter's own notification (if notificationId provided)
 * - Propagates negative status to downstream chain notifications
 * - No new notifications created
 *
 * SECURITY: The backend handles contact discovery.
 * The client no longer provides contactedIds, preventing false reporting.
 * Contacts are discovered by querying: partnerAnonymousId == reporterId
 *
 * OPTIMIZATION (Task 5.2):
 * - Query reporter's notifications ONCE at the start
 * - Store in function-scoped variable (userNotifications)
 * - Reuse for: updating own status
 */

import { onDocumentCreated } from "firebase-functions/v2/firestore";
import {
  ReportDocument,
  ReportStatus,
  PrivacyLevel,
  CONSTANTS,
  TestStatus,
} from "../types";
import {
  propagateExposureChain,
  propagatePositiveTestUpdate,
  propagateNegativeTestUpdate,
} from "../utils/chainPropagation";
import { getDb } from "../utils/database";
import { getUserByHashedNotificationId } from "../utils/queries/userQueries";

/**
 * Parse JSON array string to array of strings
 */
function parseJsonArray(jsonString: string): string[] {
  try {
    const parsed = JSON.parse(jsonString);
    if (Array.isArray(parsed)) {
      return parsed.filter((item): item is string => typeof item === "string");
    }
    console.warn(`parseJsonArray: Expected array but got ${typeof parsed}`);
    return [];
  } catch (error) {
    console.warn(
      `parseJsonArray: Failed to parse JSON string: ${jsonString?.substring(0, 50)}...`,
      error,
    );
    return [];
  }
}

/**
 * Validate report data
 *
 * Handles both POSITIVE and NEGATIVE reports with appropriate validation.
 * NEGATIVE reports have relaxed validation (no privacy level required, stiTypes can be empty).
 *
 * NOTE: contactedIds is NO LONGER required.
 * The backend automatically discovers contacts using unidirectional queries.
 * This is a security improvement that prevents false reporting.
 */
function validateReport(
  data: Partial<ReportDocument>,
): { valid: boolean; error?: string } {
  if (!data.reporterId) {
    return { valid: false, error: "Missing reporterId" };
  }

  // Input length validation for reporterId
  if (data.reporterId.length > CONSTANTS.MAX_INPUT_LENGTH.REPORTER_ID) {
    return { valid: false, error: "reporterId exceeds maximum length" };
  }

  if (!data.reporterInteractionHashedId) {
    return { valid: false, error: "Missing reporterInteractionHashedId" };
  }

  // Input length validation for reporterInteractionHashedId
  if (data.reporterInteractionHashedId.length > CONSTANTS.MAX_INPUT_LENGTH.REPORTER_ID) {
    return { valid: false, error: "reporterInteractionHashedId exceeds maximum length" };
  }

  // testResult is required
  if (!data.testResult || !Object.values(TestStatus).includes(data.testResult)) {
    return { valid: false, error: "Missing or invalid testResult" };
  }

  // For POSITIVE reports, require stiTypes and validate strictly
  if (data.testResult === TestStatus.POSITIVE) {
    if (!data.stiTypes) {
      return { valid: false, error: "Missing stiTypes for positive report" };
    }

    // Input length validation for stiTypes
    if (data.stiTypes.length > CONSTANTS.MAX_INPUT_LENGTH.STI_TYPES_JSON) {
      return { valid: false, error: "stiTypes exceeds maximum length" };
    }

    const stiTypes = parseJsonArray(data.stiTypes);
    if (stiTypes.length === 0) {
      return { valid: false, error: "Invalid or empty stiTypes array for positive report" };
    }

    if (!data.testDate || typeof data.testDate !== "number") {
      return { valid: false, error: "Missing or invalid testDate" };
    }

    // Validate test date is not in the future
    if (data.testDate > Date.now()) {
      return { valid: false, error: "testDate cannot be in the future" };
    }

    // Validate test date is within retention period
    const msPerDay = 24 * 60 * 60 * 1000;
    const retentionBoundary = Date.now() - CONSTANTS.RETENTION_DAYS * msPerDay;
    if (data.testDate < retentionBoundary) {
      return { valid: false, error: "testDate is older than 180 days" };
    }

    if (!data.privacyLevel || !Object.values(PrivacyLevel).includes(data.privacyLevel)) {
      return { valid: false, error: "Invalid privacyLevel for positive report" };
    }
  }

  // For NEGATIVE reports, stiTypes is optional and privacy level is not required
  // testDate defaults to reportedAt if not provided

  return { valid: true };
}

/**
 * Process a POSITIVE test report
 *
 * - Propagates exposure chain to discover and notify contacts
 * - Updates reporter's own notifications with positive status
 * - Updates chain notifications where reporter appears as intermediary
 */
async function processPositiveReport(
  reportId: string,
  reportData: Partial<ReportDocument>,
  reporterInteractionHashedId: string,
  reporterNotificationHashedId: string | undefined,
): Promise<void> {
  const stiTypes = reportData.stiTypes!;
  const testDate = reportData.testDate!;
  const privacyLevel = reportData.privacyLevel!;

  // NOTE: We cannot look up the reporter's username by reporterId because
  // reporterId is now a domain-separated hash. For now, we use "Someone".
  const reporterUsername = "Someone";

  // Propagate exposure chain with automatic contact discovery
  // If linkedReportId is set, this is a chain member reporting positive.
  // Users who already have notifications from the linked report should NOT
  // receive new notifications - their existing ones were already updated.
  const linkedReportId = reportData.linkedReportId;
  const notificationCount = await propagateExposureChain(
    reportId,
    reporterInteractionHashedId,
    reporterUsername,
    stiTypes,
    testDate,
    privacyLevel,
    linkedReportId,
  );

  console.log(`Report ${reportId} processed: ${notificationCount} notifications created`);

  // Update the reporter's own notifications to show they tested positive
  if (reporterNotificationHashedId) {
    const reportedStiTypes = parseJsonArray(stiTypes);
    console.log(`Reporter testing positive for: ${reportedStiTypes.join(", ")}`);

    const db = getDb();
    console.log(
      `[processExposureReport] Querying notifications once for recipientId=${reporterNotificationHashedId}`
    );
    const userNotifications = await db
      .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
      .where("recipientId", "==", reporterNotificationHashedId)
      .get();

    console.log(`[processExposureReport] Found ${userNotifications.size} notifications for reporter`);

    let updatedCount = 0;
    let skippedCount = 0;
    for (const notifDoc of userNotifications.docs) {
      const notification = notifDoc.data();
      const notificationStiType = notification.stiType;

      // Check STI type overlap
      if (notificationStiType) {
        let notificationStiTypes: string[] = [];
        try {
          const parsed = JSON.parse(notificationStiType);
          notificationStiTypes = Array.isArray(parsed) ? parsed : [notificationStiType];
        } catch {
          notificationStiTypes = [notificationStiType];
        }

        // Case-insensitive STI type comparison
        const reportedStiTypesUpper = reportedStiTypes.map(s => s.toUpperCase());
        const overlappingStiTypes = notificationStiTypes.filter(nst =>
          reportedStiTypesUpper.includes(nst.toUpperCase())
        );
        if (overlappingStiTypes.length === 0) {
          console.log(
            `Skipping notification ${notifDoc.id} - STI types ` +
            `[${notificationStiTypes.join(", ")}] don't overlap with ` +
            `reported [${reportedStiTypes.join(", ")}]`
          );
          skippedCount++;
          continue;
        }
      }

      // Calculate testedPositiveFor (intersection of STI types, case-insensitive)
      let testedPositiveFor: string[] = [];
      if (notificationStiType) {
        let notifTypes: string[] = [];
        try {
          const parsed = JSON.parse(notificationStiType);
          notifTypes = Array.isArray(parsed) ? parsed : [notificationStiType];
        } catch {
          notifTypes = [notificationStiType];
        }
        const reportedStiTypesUpper = reportedStiTypes.map(s => s.toUpperCase());
        testedPositiveFor = notifTypes.filter(nst =>
          reportedStiTypesUpper.includes(nst.toUpperCase())
        );
      }

      if (notification?.chainData) {
        try {
          const chainVisualization = JSON.parse(notification.chainData);
          const updatedNodes = chainVisualization.nodes.map((node: { isCurrentUser?: boolean }) => {
            if (node.isCurrentUser) {
              return { ...node, testStatus: TestStatus.POSITIVE, testedPositiveFor };
            }
            return node;
          });
          const updatedPaths = chainVisualization.paths?.map((path: Array<{ isCurrentUser?: boolean }>) =>
            path.map((node) => {
              if (node.isCurrentUser) {
                return { ...node, testStatus: TestStatus.POSITIVE, testedPositiveFor };
              }
              return node;
            })
          );

          const updatedChainData = JSON.stringify({ nodes: updatedNodes, paths: updatedPaths });
          await notifDoc.ref.update({ chainData: updatedChainData, updatedAt: Date.now() });
          updatedCount++;
          console.log(`Updated notification ${notifDoc.id} with positive test status`);
        } catch (parseError) {
          console.error(`Failed to parse/update chainData for notification ${notifDoc.id}:`, parseError);
        }
      }
    }
    console.log(
      `Updated ${updatedCount} of reporter's own notifications with POSITIVE status ` +
      `(skipped ${skippedCount} non-matching STI types)`
    );

    // Update chain notifications where reporter appears as intermediary
    const reporterUser = await getUserByHashedNotificationId(reporterNotificationHashedId);
    if (reporterUser?.hashedInteractionId) {
      console.log(
        "[processExposureReport] Calling propagatePositiveTestUpdate " +
        `for hashedInteractionId=${reporterUser.hashedInteractionId}`
      );
      try {
        const chainUpdatedCount = await propagatePositiveTestUpdate(
          reporterUser.hashedInteractionId,
          stiTypes
        );
        console.log(`[processExposureReport] Updated ${chainUpdatedCount} chain notifications`);
      } catch (propagateError) {
        console.error("[processExposureReport] propagatePositiveTestUpdate FAILED:", propagateError);
      }
    }
  } else {
    console.log("No reporterNotificationHashedId in report, skipping own notification update");
  }
}

/**
 * Process a NEGATIVE test report
 *
 * - Updates the reporter's own notification (if notificationId provided)
 * - Propagates negative status to downstream chain notifications
 */
async function processNegativeReport(
  reportId: string,
  reportData: Partial<ReportDocument>,
  reporterInteractionHashedId: string,
): Promise<void> {
  const db = getDb();
  const notificationId = reportData.notificationId;
  const stiTypes = reportData.stiTypes;

  // Parse STI type for filtering (first one if array)
  let stiType: string | undefined;
  if (stiTypes) {
    try {
      const parsed = JSON.parse(stiTypes);
      stiType = Array.isArray(parsed) && parsed.length > 0 ? parsed[0] : stiTypes;
    } catch {
      stiType = stiTypes;
    }
  }

  console.log(`Processing negative report ${reportId} for notification ${notificationId}`);

  // Update the user's own notification to show they tested negative
  if (notificationId) {
    const notificationRef = db.collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS).doc(notificationId);
    const notificationDoc = await notificationRef.get();

    if (notificationDoc.exists) {
      const notification = notificationDoc.data();
      if (notification?.chainData) {
        try {
          const chainVisualization = JSON.parse(notification.chainData);
          const updatedNodes = chainVisualization.nodes.map((node: { isCurrentUser?: boolean }) => {
            if (node.isCurrentUser) {
              return { ...node, testStatus: TestStatus.NEGATIVE };
            }
            return node;
          });
          const updatedPaths = chainVisualization.paths?.map((path: Array<{ isCurrentUser?: boolean }>) =>
            path.map((node) => {
              if (node.isCurrentUser) {
                return { ...node, testStatus: TestStatus.NEGATIVE };
              }
              return node;
            })
          );

          const updatedChainData = JSON.stringify({ nodes: updatedNodes, paths: updatedPaths });
          await notificationRef.update({ chainData: updatedChainData, updatedAt: Date.now() });
          console.log(`Updated notification ${notificationId} with negative test status`);
        } catch (parseError) {
          console.error(`Failed to parse/update chainData for notification ${notificationId}:`, parseError);
        }
      }
    } else {
      console.log(`Notification ${notificationId} not found`);
    }
  }

  // Propagate negative status to downstream chain notifications
  // We need to get the user's UID from their hashedInteractionId
  // Since we stored reporterInteractionHashedId = SHA256(uid), we need to look up the user
  const usersSnapshot = await db
    .collection(CONSTANTS.COLLECTIONS.USERS)
    .where("hashedInteractionId", "==", reporterInteractionHashedId)
    .limit(1)
    .get();

  if (!usersSnapshot.empty) {
    const userData = usersSnapshot.docs[0].data();
    const userId = userData.anonymousId;

    console.log(`Propagating negative test update for user ${userId}`);
    const updatedCount = await propagateNegativeTestUpdate(userId, stiType);
    console.log(`Updated ${updatedCount} downstream notifications with negative status`);
  } else {
    console.log(`Could not find user for hashedInteractionId ${reporterInteractionHashedId}`);
  }
}

/**
 * Process exposure report when created in Firestore
 * Uses v2 syntax with named database support
 */
export const processExposureReport = onDocumentCreated(
  {
    document: `${CONSTANTS.COLLECTIONS.REPORTS}/{reportId}`,
    region: "europe-west1",
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      console.error("No data in event");
      return;
    }

    const reportId = event.params.reportId;
    const reportData = snapshot.data() as Partial<ReportDocument>;

    console.log(`Processing exposure report: ${reportId}`);

    // Update status to processing
    await snapshot.ref.update({
      status: ReportStatus.PROCESSING,
    });

    try {
      // Validate report data
      const validation = validateReport(reportData);
      if (!validation.valid) {
        console.error(`Invalid report ${reportId}: ${validation.error}`);
        await snapshot.ref.update({
          status: ReportStatus.FAILED,
          error: validation.error,
        });
        return;
      }

      // Extract common fields
      const reporterInteractionHashedId = reportData.reporterInteractionHashedId!;
      const reporterNotificationHashedId = reportData.reporterNotificationHashedId;
      const testResult = reportData.testResult!;

      // Branch based on test result type
      if (testResult === TestStatus.POSITIVE) {
        // ========== POSITIVE REPORT PROCESSING ==========
        await processPositiveReport(
          reportId,
          reportData,
          reporterInteractionHashedId,
          reporterNotificationHashedId,
        );
      } else if (testResult === TestStatus.NEGATIVE) {
        // ========== NEGATIVE REPORT PROCESSING ==========
        await processNegativeReport(
          reportId,
          reportData,
          reporterInteractionHashedId,
        );
      } else {
        console.log(`Unknown testResult: ${testResult}, skipping processing`);
      }

      // Update report status to completed
      await snapshot.ref.update({
        status: ReportStatus.COMPLETED,
        processedAt: Date.now(),
      });
    } catch (error) {
      console.error(`Error processing report ${reportId}:`, error);

      // Update status to failed
      await snapshot.ref.update({
        status: ReportStatus.FAILED,
        error: error instanceof Error ? error.message : "Unknown error",
      });
    }
  },
);
