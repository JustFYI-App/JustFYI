/**
 * Account Recovery Cloud Function
 *
 * Creates a custom authentication token for account recovery.
 * This allows users to sign back into their original anonymous account
 * after reinstalling the app using their saved ID.
 */

import * as functionsV1 from "firebase-functions/v1";
import * as admin from "firebase-admin";
import { logInfo, logWarn, logError } from "../utils/logger";
import { checkRateLimit } from "../utils/rateLimit";

interface RecoverAccountRequest {
  savedId: string;
}

interface RecoverAccountResponse {
  success: boolean;
  customToken?: string;
  error?: string;
}

/**
 * HTTPS Callable function to generate a custom auth token for account recovery.
 *
 * Flow:
 * 1. Client sends their saved anonymous ID
 * 2. Function verifies the user exists in Firestore
 * 3. Function creates a custom token for that UID
 * 4. Client uses signInWithCustomToken() to authenticate
 *
 * Security considerations:
 * - Validates ID format
 * - Rate limiting to prevent brute-force attacks (5 attempts per hour per ID)
 * - Verifies user exists in database
 */
export const recoverAccount = functionsV1
  .region("europe-west1")
  .https.onCall(
    async (
      data: RecoverAccountRequest,
      _context: functionsV1.https.CallableContext
    ): Promise<RecoverAccountResponse> => {

      try {
        // Validate input
        if (!data || !data.savedId) {
          logWarn("recoverAccount: Missing savedId in request");
          return {
            success: false,
            error: "Missing saved ID",
          };
        }

        const savedId = data.savedId.trim();

        // Validate ID format (20-40 alphanumeric characters)
        if (!/^[a-zA-Z0-9]{20,40}$/.test(savedId)) {
          logWarn(`recoverAccount: Invalid ID format: ${savedId.substring(0, 10)}...`);
          return {
            success: false,
            error: "Invalid ID format",
          };
        }

        // Check rate limit to prevent brute-force attacks
        const allowed = await checkRateLimit(savedId, "account_recovery");
        if (!allowed) {
          logWarn(`recoverAccount: Rate limited for ID: ${savedId.substring(0, 10)}...`);
          return {
            success: false,
            error: "Too many recovery attempts. Please try again later.",
          };
        }

        // Verify user exists in Firestore
        const db = admin.firestore();
        const userDoc = await db.collection("users").doc(savedId).get();

        if (!userDoc.exists) {
          logInfo(`recoverAccount: User not found: ${savedId.substring(0, 10)}...`);
          return {
            success: false,
            error: "Account not found. Please check your ID and try again.",
          };
        }

        // Create custom token for the saved UID
        const customToken = await admin.auth().createCustomToken(savedId);

        logInfo(`recoverAccount: Recovery token created for user: ${savedId.substring(0, 10)}...`);

        return {
          success: true,
          customToken,
        };
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        logError("recoverAccount: Error creating recovery token", error);
        return {
          success: false,
          error: `Recovery failed: ${errorMessage}`,
        };
      }
    }
  );
