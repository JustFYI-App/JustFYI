/**
 * Push notification helper using FCM Admin SDK
 * Task 11.7: sendPushNotification helper
 *
 * Uses FCM localization keys so clients display notifications using their local translations.
 * This avoids server-side language detection and ensures notifications match device language.
 *
 * UTILITIES USED:
 * - getUserByHashedInteractionId, getUserByHashedNotificationId: User lookups
 * - getUsersByHashedInteractionIds: Batch user lookups
 * - clearUserFcmToken: Token cleanup
 */

import * as admin from "firebase-admin";
import {
  getUserByHashedInteractionId,
  getUserByHashedNotificationId,
  getUsersByHashedInteractionIds,
  clearUserFcmToken,
} from "./queries";

/**
 * Firebase error with code property
 */
interface FirebaseErrorWithCode {
  code?: string;
  message?: string;
}

/**
 * Localization keys for FCM notifications.
 *
 * SOURCE OF TRUTH for notification types: FirestoreCollections.NotificationTypes (Kotlin)
 * Path: shared/src/commonMain/kotlin/app/justfyi/data/model/FirestoreCollections.kt
 *
 * These keys must match the string resource names in:
 * - Android: androidApp/src/main/res/values/strings.xml
 * - iOS: iosApp/iosApp/*.lproj/Localizable.strings
 */
const NOTIFICATION_LOC_KEYS = {
  EXPOSURE: {
    titleKey: "notification_exposure_title",
    bodyKey: "notification_exposure_body",
  },
  UPDATE: {
    titleKey: "notification_update_title",
    bodyKey: "notification_update_body",
  },
  REPORT_DELETED: {
    titleKey: "notification_report_deleted_title",
    bodyKey: "notification_report_deleted_body",
  },
} as const;

/**
 * Notification payload with localization keys
 */
interface LocalizedNotificationPayload {
  titleLocKey: string;
  bodyLocKey: string;
  data?: Record<string, string>;
}

/**
 * Build FCM message with localization keys for both Android and iOS.
 *
 * @param token - FCM token to send to
 * @param payload - Notification payload with localization keys
 * @returns FCM message object
 */
function buildFcmMessage(
  token: string,
  payload: LocalizedNotificationPayload,
): admin.messaging.Message {
  return {
    token,
    // Android localization (uses titleLocKey/bodyLocKey)
    android: {
      priority: "high",
      notification: {
        channelId: "exposure_notifications",
        titleLocKey: payload.titleLocKey,
        bodyLocKey: payload.bodyLocKey,
        defaultSound: true,
        defaultVibrateTimings: true,
      },
    },
    // iOS/APNs localization
    // Note: Using type assertion because Firebase Admin SDK types don't include APNs loc keys
    apns: {
      payload: {
        aps: {
          alert: {
            "title-loc-key": payload.titleLocKey,
            "body-loc-key": payload.bodyLocKey,
          } as admin.messaging.ApsAlert,
          sound: "default",
        },
      },
    },
    // Data payload for deep linking (available on both platforms)
    data: payload.data || {},
  };
}

/**
 * Build FCM multicast message with localization keys for both Android and iOS.
 *
 * @param tokens - Array of FCM tokens to send to
 * @param payload - Notification payload with localization keys
 * @returns FCM multicast message object
 */
function buildFcmMulticastMessage(
  tokens: string[],
  payload: LocalizedNotificationPayload,
): admin.messaging.MulticastMessage {
  return {
    tokens,
    android: {
      priority: "high",
      notification: {
        channelId: "exposure_notifications",
        titleLocKey: payload.titleLocKey,
        bodyLocKey: payload.bodyLocKey,
        defaultSound: true,
      },
    },
    apns: {
      payload: {
        aps: {
          alert: {
            "title-loc-key": payload.titleLocKey,
            "body-loc-key": payload.bodyLocKey,
          } as admin.messaging.ApsAlert,
          sound: "default",
        },
      },
    },
    data: payload.data || {},
  };
}

/**
 * Handle invalid FCM token errors by clearing the token.
 *
 * @param error - The error from FCM send
 * @param docRef - Document reference to clear token from
 * @returns True if this was a token error that was handled
 */
async function handleFcmTokenError(
  error: unknown,
  docRef: admin.firestore.DocumentReference,
): Promise<boolean> {
  const firebaseError = error as FirebaseErrorWithCode;
  if (
    firebaseError.code === "messaging/invalid-registration-token" ||
    firebaseError.code === "messaging/registration-token-not-registered"
  ) {
    await clearUserFcmToken(docRef);
    return true;
  }
  return false;
}

/**
 * Send a push notification to a user via FCM using localization keys.
 * The client will display the notification using its local string resources.
 *
 * @param hashedInteractionId - The hashed interaction ID (from interaction.ownerId)
 * @param payload - The notification payload with localization keys
 * @returns True if notification was sent successfully, false otherwise
 */
export async function sendPushNotification(
  hashedInteractionId: string,
  payload: LocalizedNotificationPayload,
): Promise<boolean> {
  try {
    const user = await getUserByHashedInteractionId(hashedInteractionId);

    if (!user) {
      console.log(`User with hashedInteractionId ${hashedInteractionId} not found`);
      return false;
    }

    if (!user.fcmToken) {
      console.log(`No FCM token for user with hashedInteractionId ${hashedInteractionId}`);
      return false;
    }

    const message = buildFcmMessage(user.fcmToken, payload);
    const response = await admin.messaging().send(message);
    console.log(`Successfully sent notification to user (hashedInteractionId: ${hashedInteractionId}): ${response}`);
    return true;
  } catch (error: unknown) {
    // Try to get user reference for token cleanup
    const user = await getUserByHashedInteractionId(hashedInteractionId);
    if (user) {
      const wasTokenError = await handleFcmTokenError(error, user.docRef);
      if (wasTokenError) {
        console.log(`Invalid FCM token for user with hashedInteractionId ${hashedInteractionId}, cleared token`);
        return false;
      }
    }
    console.error(`Error sending notification to user (hashedInteractionId: ${hashedInteractionId}):`, error);
    return false;
  }
}

/**
 * Send exposure notification to a user using localization keys.
 * The client will display the notification in the device's language.
 *
 * Privacy: STI type is intentionally NOT included in push notification.
 * Users must open the app to see details. This prevents sensitive health
 * information from appearing on lock screens or notification previews.
 *
 * @param hashedInteractionId - The hashed interaction ID (from interaction.ownerId)
 * @param notificationId - The notification document ID
 * @param _stiType - Optional STI type (not used in push - only in app)
 */
export async function sendExposureNotification(
  hashedInteractionId: string,
  notificationId: string,
  _stiType?: string,
): Promise<boolean> {
  return sendPushNotification(hashedInteractionId, {
    titleLocKey: NOTIFICATION_LOC_KEYS.EXPOSURE.titleKey,
    bodyLocKey: NOTIFICATION_LOC_KEYS.EXPOSURE.bodyKey,
    data: {
      notificationId,
      type: "EXPOSURE",
    },
  });
}

/**
 * Send update notification when chain status changes using localization keys.
 * The client will display the notification in the device's language.
 *
 * @param hashedInteractionId - The hashed interaction ID (from interaction.ownerId)
 * @param notificationId - The notification document ID
 * @param _updateMessage - Unused (client uses localized string)
 */
export async function sendUpdateNotification(
  hashedInteractionId: string,
  notificationId: string,
  _updateMessage?: string,
): Promise<boolean> {
  return sendPushNotification(hashedInteractionId, {
    titleLocKey: NOTIFICATION_LOC_KEYS.UPDATE.titleKey,
    bodyLocKey: NOTIFICATION_LOC_KEYS.UPDATE.bodyKey,
    data: {
      notificationId,
      type: "UPDATE",
    },
  });
}

/**
 * Send notification when a report has been deleted/retracted.
 * The client will display the notification in the device's language.
 *
 * Privacy: STI type is intentionally NOT included in push notification.
 * Users must open the app to see details.
 *
 * NOTE: This function accepts hashedNotificationId (from notification.recipientId)
 * and looks up the user by that field instead of hashedInteractionId.
 *
 * @param hashedNotificationId - The hashed notification ID (recipientId from notification document)
 * @param notificationId - The notification document ID
 */
export async function sendReportDeletedNotification(
  hashedNotificationId: string,
  notificationId: string,
): Promise<boolean> {
  return sendPushNotificationByNotificationId(hashedNotificationId, {
    titleLocKey: NOTIFICATION_LOC_KEYS.REPORT_DELETED.titleKey,
    bodyLocKey: NOTIFICATION_LOC_KEYS.REPORT_DELETED.bodyKey,
    data: {
      notificationId,
      type: "REPORT_DELETED",
    },
  });
}

/**
 * Send a push notification to a user via FCM, looking up by hashedNotificationId.
 *
 * This is similar to sendPushNotification but queries by hashedNotificationId
 * instead of hashedInteractionId. Used when we have the notification.recipientId
 * rather than the interaction.ownerId.
 *
 * @param hashedNotificationId - The hashed notification ID (from notification.recipientId)
 * @param payload - The notification payload with localization keys
 * @returns True if notification was sent successfully, false otherwise
 */
async function sendPushNotificationByNotificationId(
  hashedNotificationId: string,
  payload: LocalizedNotificationPayload,
): Promise<boolean> {
  try {
    const user = await getUserByHashedNotificationId(hashedNotificationId);

    if (!user) {
      console.log(`User with hashedNotificationId ${hashedNotificationId} not found`);
      return false;
    }

    if (!user.fcmToken) {
      console.log(`No FCM token for user with hashedNotificationId ${hashedNotificationId}`);
      return false;
    }

    const message = buildFcmMessage(user.fcmToken, payload);
    const response = await admin.messaging().send(message);
    console.log(
      "Successfully sent notification to user " +
      `(hashedNotificationId: ${hashedNotificationId}): ${response}`
    );
    return true;
  } catch (error: unknown) {
    // Try to get user reference for token cleanup
    const user = await getUserByHashedNotificationId(hashedNotificationId);
    if (user) {
      const wasTokenError = await handleFcmTokenError(error, user.docRef);
      if (wasTokenError) {
        console.log(`Invalid FCM token for user with hashedNotificationId ${hashedNotificationId}, cleared token`);
        return false;
      }
    }
    console.error(
      `Error sending notification to user (hashedNotificationId: ${hashedNotificationId}):`,
      error
    );
    return false;
  }
}

/**
 * Send batch notifications to multiple users using localization keys.
 * Uses multicast for efficiency.
 *
 * @param hashedInteractionIds - Array of hashed interaction IDs to notify
 * @param payload - The notification payload with localization keys
 * @returns Number of successfully sent notifications
 */
export async function sendBatchNotifications(
  hashedInteractionIds: string[],
  payload: LocalizedNotificationPayload,
): Promise<number> {
  let successCount = 0;

  // Use shared utility for batch user lookups
  const usersMap = await getUsersByHashedInteractionIds(hashedInteractionIds);

  // Build token to user mapping for error handling
  const tokens: string[] = [];
  const tokenToUserMap = new Map<string, { hashedId: string; docRef: admin.firestore.DocumentReference }>();

  for (const [hashedId, user] of usersMap) {
    if (user.fcmToken) {
      tokens.push(user.fcmToken);
      tokenToUserMap.set(user.fcmToken, { hashedId, docRef: user.docRef });
    }
  }

  if (tokens.length === 0) {
    console.log("No valid FCM tokens found for batch notification");
    return 0;
  }

  // Send multicast message with localization keys
  try {
    const message = buildFcmMulticastMessage(tokens, payload);
    const response = await admin.messaging().sendEachForMulticast(message);
    successCount = response.successCount;

    // Handle failed tokens
    if (response.failureCount > 0) {
      const clearPromises: Promise<void>[] = [];

      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          const errorCode = resp.error?.code;
          if (
            errorCode === "messaging/invalid-registration-token" ||
            errorCode === "messaging/registration-token-not-registered"
          ) {
            const userInfo = tokenToUserMap.get(tokens[idx]);
            if (userInfo) {
              clearPromises.push(clearUserFcmToken(userInfo.docRef));
            }
          }
        }
      });

      await Promise.all(clearPromises);
    }

    console.log(
      `Batch notification sent: ${response.successCount} success, ${response.failureCount} failed`,
    );
  } catch (error) {
    console.error("Error sending batch notification:", error);
  }

  return successCount;
}
