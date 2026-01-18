/**
 * Just FYI Cloud Functions
 * Firebase Cloud Functions for Just FYI - Privacy-First STI Exposure Notification App
 *
 * Functions:
 * - processExposureReport: Process new exposure reports (both POSITIVE and NEGATIVE)
 * - reportPositiveTest: HTTPS callable for users to report positive tests
 * - reportNegativeTest: HTTPS callable for users to report negative tests
 * - getChainLinkInfo: HTTPS callable to check chain linking before positive report
 * - cleanupExpiredData: Daily cleanup of data older than 180 days
 * - triggerCleanup: Manual cleanup trigger (admin only)
 * - exportUserData: HTTPS callable for GDPR-compliant user data export
 * - deleteExposureReport: HTTPS callable for users to delete their own reports
 * - recoverAccount: HTTPS callable for account recovery
 */

import * as admin from "firebase-admin";

// Initialize Firebase Admin SDK
// When running in Cloud Functions, uses default credentials automatically
// For custom token signing, ensure the Cloud Functions service account has
// the "Service Account Token Creator" IAM role
admin.initializeApp();

// Export Cloud Functions
export { processExposureReport } from "./functions/processExposureReport";
export {
  reportNegativeTest,
  reportPositiveTest,
  getChainLinkInfo,
} from "./functions/updateTestStatus";
export { cleanupExpiredData, triggerCleanup } from "./functions/cleanupExpiredData";
export { exportUserData } from "./functions/exportUserData";
export { deleteExposureReport } from "./functions/deleteExposureReport";
export { recoverAccount } from "./functions/recoverAccount";
