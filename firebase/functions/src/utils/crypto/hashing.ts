/**
 * Domain-separated hashing functions for privacy protection
 *
 * To prevent cross-collection correlation attacks if the database is breached,
 * UIDs are hashed with domain-specific salt prefixes:
 * - interactions.ownerId: SHA256(uid) - no salt (BLE compatibility)
 * - notifications.recipientId: SHA256("notification:" + uid)
 * - notifications.chainPath/chainPaths: SHA256("chain:" + uid)
 * - reports.reporterId: SHA256("report:" + uid)
 */

import * as crypto from "crypto";

/**
 * Hash an anonymous ID using SHA-256 with no salt prefix.
 *
 * This matches the hashing performed by the client when exchanging IDs via BLE.
 * The client stores partnerAnonymousId as a SHA-256 hash of the partner's Firebase UID,
 * so we must hash the reporterId before querying.
 *
 * @param anonymousId - The raw Firebase UID
 * @returns The SHA-256 hash (lowercase hex string)
 */
export function hashAnonymousId(anonymousId: string): string {
  return crypto.createHash("sha256").update(anonymousId, "utf8").digest("hex");
}

/**
 * Hash a UID for notification recipientId field.
 *
 * Uses "notification:" salt prefix to create domain-separated hash.
 * Formula: SHA256("notification:" + uid)
 *
 * @param uid - The raw Firebase UID
 * @returns The domain-separated SHA-256 hash (lowercase hex string)
 */
export function hashForNotification(uid: string): string {
  return crypto.createHash("sha256").update("notification:" + uid, "utf8").digest("hex");
}

/**
 * Hash a UID for chainPath and chainPaths fields.
 *
 * Uses "chain:" salt prefix to create domain-separated hash.
 * Formula: SHA256("chain:" + uid)
 *
 * @param uid - The raw Firebase UID
 * @returns The domain-separated SHA-256 hash (lowercase hex string)
 */
export function hashForChain(uid: string): string {
  return crypto.createHash("sha256").update("chain:" + uid, "utf8").digest("hex");
}

/**
 * Hash a UID for reports.reporterId field.
 *
 * Uses "report:" salt prefix to create domain-separated hash.
 * Formula: SHA256("report:" + uid)
 *
 * @param uid - The raw Firebase UID
 * @returns The domain-separated SHA-256 hash (lowercase hex string)
 */
export function hashForReport(uid: string): string {
  return crypto.createHash("sha256").update("report:" + uid, "utf8").digest("hex");
}
