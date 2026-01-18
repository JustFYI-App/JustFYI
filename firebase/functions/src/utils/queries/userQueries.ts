/**
 * User query utilities
 *
 * Centralized user lookup functions to avoid code duplication
 * across different modules.
 */

import * as admin from "firebase-admin";
import { getDb } from "../database";
import { CONSTANTS } from "../../types";

/**
 * User data returned from lookup
 */
export interface UserLookupResult {
  hashedNotificationId: string;
  hashedInteractionId: string;
  fcmToken?: string;
  docRef: admin.firestore.DocumentReference;
}

/**
 * Look up user data from a hashed interaction ID.
 *
 * Since interaction.ownerId stores hashForInteraction(uid), we query the
 * users collection by hashedInteractionId to get the precomputed hashedNotificationId.
 *
 * @param hashedInteractionId - The hashed interaction ID (from interaction.ownerId)
 * @returns Object with user data, or null if not found
 */
export async function getUserByHashedInteractionId(
  hashedInteractionId: string,
): Promise<UserLookupResult | null> {
  const db = getDb();

  try {
    const userQuery = await db
      .collection(CONSTANTS.COLLECTIONS.USERS)
      .where("hashedInteractionId", "==", hashedInteractionId)
      .limit(1)
      .get();

    if (userQuery.empty) {
      console.log(`No user found with hashedInteractionId: ${hashedInteractionId}`);
      return null;
    }

    const userDoc = userQuery.docs[0];
    const userData = userDoc.data();
    const hashedNotificationId = typeof userData?.hashedNotificationId === "string"
      ? userData.hashedNotificationId
      : undefined;
    const storedHashedInteractionId = typeof userData?.hashedInteractionId === "string"
      ? userData.hashedInteractionId
      : undefined;

    if (!hashedNotificationId || !storedHashedInteractionId) {
      console.log(`User found but missing hash fields for: ${hashedInteractionId}`);
      return null;
    }

    return {
      hashedNotificationId,
      hashedInteractionId: storedHashedInteractionId,
      fcmToken: typeof userData?.fcmToken === "string" ? userData.fcmToken : undefined,
      docRef: userDoc.ref,
    };
  } catch (error) {
    console.error("Error looking up user by hashedInteractionId:", error);
    return null;
  }
}

/**
 * Look up user data from a hashed notification ID.
 *
 * Used when we have the notification.recipientId and need to find the user.
 *
 * @param hashedNotificationId - The hashed notification ID (from notification.recipientId)
 * @returns Object with user data, or null if not found
 */
export async function getUserByHashedNotificationId(
  hashedNotificationId: string,
): Promise<UserLookupResult | null> {
  const db = getDb();

  try {
    const userQuery = await db
      .collection(CONSTANTS.COLLECTIONS.USERS)
      .where("hashedNotificationId", "==", hashedNotificationId)
      .limit(1)
      .get();

    if (userQuery.empty) {
      console.log(`No user found with hashedNotificationId: ${hashedNotificationId}`);
      return null;
    }

    const userDoc = userQuery.docs[0];
    const userData = userDoc.data();
    const storedHashedNotificationId = typeof userData?.hashedNotificationId === "string"
      ? userData.hashedNotificationId
      : undefined;
    const storedHashedInteractionId = typeof userData?.hashedInteractionId === "string"
      ? userData.hashedInteractionId
      : undefined;

    if (!storedHashedNotificationId || !storedHashedInteractionId) {
      console.log(`User found but missing hash fields for hashedNotificationId: ${hashedNotificationId}`);
      return null;
    }

    return {
      hashedNotificationId: storedHashedNotificationId,
      hashedInteractionId: storedHashedInteractionId,
      fcmToken: typeof userData?.fcmToken === "string" ? userData.fcmToken : undefined,
      docRef: userDoc.ref,
    };
  } catch (error) {
    console.error("Error looking up user by hashedNotificationId:", error);
    return null;
  }
}

/**
 * Look up multiple users by their hashed interaction IDs.
 *
 * Uses batched queries to handle Firestore's 30-item limit for 'in' queries.
 *
 * @param hashedInteractionIds - Array of hashed interaction IDs
 * @returns Map of hashedInteractionId to UserLookupResult
 */
export async function getUsersByHashedInteractionIds(
  hashedInteractionIds: string[],
): Promise<Map<string, UserLookupResult>> {
  const db = getDb();
  const results = new Map<string, UserLookupResult>();

  // Firestore 'in' queries are limited to 30 items
  const batchSize = 30;

  for (let i = 0; i < hashedInteractionIds.length; i += batchSize) {
    const batch = hashedInteractionIds.slice(i, i + batchSize);

    try {
      const userQuery = await db
        .collection(CONSTANTS.COLLECTIONS.USERS)
        .where("hashedInteractionId", "in", batch)
        .get();

      for (const doc of userQuery.docs) {
        const userData = doc.data();
        const hashedId = typeof userData?.hashedInteractionId === "string"
          ? userData.hashedInteractionId
          : undefined;
        const hashedNotifId = typeof userData?.hashedNotificationId === "string"
          ? userData.hashedNotificationId
          : undefined;

        // Include users only if they have required hash fields
        if (hashedId && hashedNotifId) {
          results.set(hashedId, {
            hashedNotificationId: hashedNotifId,
            hashedInteractionId: hashedId,
            fcmToken: typeof userData?.fcmToken === "string" ? userData.fcmToken : undefined,
            docRef: doc.ref,
          });
        }
      }
    } catch (error) {
      console.error("Error looking up users by hashedInteractionIds:", error);
    }
  }

  return results;
}

/**
 * Look up multiple users by their hashed notification IDs.
 *
 * Uses batched queries to handle Firestore's 30-item limit for 'in' queries.
 * This is used for batch FCM push notifications when we have notification.recipientId values.
 *
 * Task 5.4: Added for deleteExposureReport optimization
 * Task 5.5: Used for updateTestStatus optimization
 *
 * @param hashedNotificationIds - Array of hashed notification IDs (from notification.recipientId)
 * @returns Map of hashedNotificationId to UserLookupResult
 */
export async function getUsersByHashedNotificationIds(
  hashedNotificationIds: string[],
): Promise<Map<string, UserLookupResult>> {
  const db = getDb();
  const results = new Map<string, UserLookupResult>();

  if (hashedNotificationIds.length === 0) {
    return results;
  }

  // Firestore 'in' queries are limited to 30 items
  const batchSize = 30;

  for (let i = 0; i < hashedNotificationIds.length; i += batchSize) {
    const batch = hashedNotificationIds.slice(i, i + batchSize);

    try {
      const userQuery = await db
        .collection(CONSTANTS.COLLECTIONS.USERS)
        .where("hashedNotificationId", "in", batch)
        .get();

      for (const doc of userQuery.docs) {
        const userData = doc.data();
        const hashedNotifId = typeof userData?.hashedNotificationId === "string"
          ? userData.hashedNotificationId
          : undefined;
        const hashedIntId = typeof userData?.hashedInteractionId === "string"
          ? userData.hashedInteractionId
          : undefined;

        if (hashedNotifId && hashedIntId) {
          results.set(hashedNotifId, {
            hashedNotificationId: hashedNotifId,
            hashedInteractionId: hashedIntId,
            fcmToken: typeof userData?.fcmToken === "string" ? userData.fcmToken : undefined,
            docRef: doc.ref,
          });
        }
      }
    } catch (error) {
      console.error("Error looking up users by hashedNotificationIds:", error);
    }
  }

  return results;
}

/**
 * Look up user data from a raw Firebase UID.
 *
 * The user document ID is typically the raw Firebase UID.
 *
 * @param uid - The raw Firebase UID
 * @returns Object with user data, or null if not found
 */
export async function getUserByUid(
  uid: string,
): Promise<UserLookupResult | null> {
  const db = getDb();

  try {
    const userDoc = await db
      .collection(CONSTANTS.COLLECTIONS.USERS)
      .doc(uid)
      .get();

    if (!userDoc.exists) {
      console.log(`No user found with uid: ${uid}`);
      return null;
    }

    const userData = userDoc.data();
    const hashedInteractionId = userData?.hashedInteractionId as string | undefined;
    const hashedNotificationId = userData?.hashedNotificationId as string | undefined;

    if (!hashedInteractionId || !hashedNotificationId) {
      console.log(`User found but missing hash fields for uid: ${uid}`);
      return null;
    }

    return {
      hashedNotificationId,
      hashedInteractionId,
      fcmToken: userData?.fcmToken as string | undefined,
      docRef: userDoc.ref,
    };
  } catch (error) {
    console.error("Error looking up user by uid:", error);
    return null;
  }
}

/**
 * Clear the FCM token for a user.
 *
 * Called when a token is invalid and needs to be removed.
 *
 * @param docRef - The document reference for the user
 */
export async function clearUserFcmToken(
  docRef: admin.firestore.DocumentReference,
): Promise<void> {
  try {
    await docRef.update({
      fcmToken: admin.firestore.FieldValue.delete(),
    });
    console.log("Cleared invalid FCM token for user");
  } catch (error) {
    console.error("Error clearing FCM token:", error);
  }
}
