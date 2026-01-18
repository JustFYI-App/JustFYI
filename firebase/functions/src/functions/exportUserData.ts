/**
 * exportUserData Cloud Function
 * Task 1.2-1.5: Create exportUserData callable function
 *
 * HTTPS callable Cloud Function that exports all user data from Firestore.
 * - Requires Firebase Anonymous Auth
 * - Queries 4 collections: users, interactions, notifications, reports
 * - Excludes fcmToken from user data
 * - Returns chainData in raw JSON string form (no parsing)
 *
 * Response structure: { user: {...}, interactions: [...], notifications: [...], reports: [...] }
 */

import * as functionsV1 from "firebase-functions/v1";
import {
  UserDocument,
  InteractionDocument,
  NotificationDocument,
  ReportDocument,
  CONSTANTS,
} from "../types";
import { getDb } from "../utils/database";
import {
  hashAnonymousId,
  hashForNotification,
  hashForReport,
} from "../utils/chainPropagation";
import { checkRateLimit } from "../utils/rateLimit";

/**
 * Exported user data structure
 */
interface ExportedUserData {
  anonymousId: string;
  username: string;
  createdAt: number;
  // fcmToken is intentionally excluded for privacy/security
}

/**
 * Response structure for exportUserData
 */
interface ExportUserDataResponse {
  user: ExportedUserData | null;
  interactions: InteractionDocument[];
  notifications: NotificationDocument[];
  reports: ReportDocument[];
}

/**
 * Export all user data from Firestore
 *
 * This function enables GDPR-compliant data portability by allowing users
 * to export all their server-side data.
 */
export const exportUserData = functionsV1
  .region("europe-west1")
  .https.onCall(async (_data, context): Promise<ExportUserDataResponse> => {
    // Task 1.3: Verify authentication
    if (!context.auth) {
      throw new functionsV1.https.HttpsError(
        "unauthenticated",
        "User must be authenticated to export data"
      );
    }

    const userId = context.auth.uid;

    // Rate limiting check
    const allowed = await checkRateLimit(userId, "data_export");
    if (!allowed) {
      throw new functionsV1.https.HttpsError(
        "resource-exhausted",
        "Too many export requests. Please try again later."
      );
    }

    console.log(`User ${userId} requesting data export`);

    const db = getDb();

    try {
      // Task 1.4: Query all 4 Firestore collections

      // 1. Query users/{userId} document directly
      const userDoc = await db
        .collection(CONSTANTS.COLLECTIONS.USERS)
        .doc(userId)
        .get();

      // Task 1.5: Format and sanitize response data
      // Exclude fcmToken from user document
      let exportedUser: ExportedUserData | null = null;
      if (userDoc.exists) {
        const userData = userDoc.data() as UserDocument;
        exportedUser = {
          anonymousId: userData.anonymousId,
          username: userData.username,
          createdAt: userData.createdAt,
          // fcmToken intentionally excluded
        };
      }

      // 2. Query interactions/ where ownerId == hashedOwnerId
      // ownerId is stored as SHA256(uid.uppercase()) - no salt
      const hashedOwnerId = hashAnonymousId(userId);
      const interactionsSnapshot = await db
        .collection(CONSTANTS.COLLECTIONS.INTERACTIONS)
        .where("ownerId", "==", hashedOwnerId)
        .get();

      const interactions: InteractionDocument[] = interactionsSnapshot.docs.map(
        (doc) => doc.data() as InteractionDocument
      );

      // 3. Query notifications/ where recipientId == hashedRecipientId
      // recipientId is stored as SHA256("notification:" + uid.uppercase())
      const hashedRecipientId = hashForNotification(userId);
      const notificationsSnapshot = await db
        .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
        .where("recipientId", "==", hashedRecipientId)
        .get();

      // Keep chainData in raw JSON string form (no parsing/transformation)
      const notifications: NotificationDocument[] = notificationsSnapshot.docs.map(
        (doc) => doc.data() as NotificationDocument
      );

      // 4. Query reports/ where reporterId == hashedReporterId
      // reporterId is stored as SHA256("report:" + uid.uppercase())
      const hashedReporterId = hashForReport(userId);
      const reportsSnapshot = await db
        .collection(CONSTANTS.COLLECTIONS.REPORTS)
        .where("reporterId", "==", hashedReporterId)
        .get();

      const reports: ReportDocument[] = reportsSnapshot.docs.map(
        (doc) => doc.data() as ReportDocument
      );

      console.log(
        `Data export for user ${userId}: ` +
        `user=${userDoc.exists ? "found" : "not found"}, ` +
        `interactions=${interactions.length}, ` +
        `notifications=${notifications.length}, ` +
        `reports=${reports.length}`
      );

      // Return structured response
      return {
        user: exportedUser,
        interactions,
        notifications,
        reports,
      };
    } catch (error) {
      console.error(`Error exporting data for user ${userId}:`, error);
      throw new functionsV1.https.HttpsError(
        "internal",
        "An error occurred while exporting your data. Please try again."
      );
    }
  });
