/**
 * Security Rules Hash Validation Tests
 * Task 2.1: Write 4-6 focused tests for security rules hash validation
 *
 * These tests verify that the security rules properly:
 * 1. Require matching hashed ownerId for interactions read
 * 2. Require matching hashed recipientId for notifications read
 * 3. Require matching hashed reporterId for reports read
 * 4. Reject pre-computed hashes from client (server computes own hash)
 * 5. Domain separation prevents cross-collection access
 *
 * NOTE: These tests verify the hash computation logic that security rules use.
 * The actual rules enforcement is tested via the Firebase emulator.
 * Since Firestore security rules use `hashing.sha256(bytes(...)).toHexString()`,
 * we verify our test helpers produce the same output as the backend.
 */

import * as crypto from "crypto";
import {
  hashAnonymousId,
  hashForNotification,
  hashForReport,
} from "../utils/chainPropagation";

/**
 * Simulate what Firestore security rules compute with:
 * hashing.sha256(bytes(input)).toHexString()
 *
 * This is the reference implementation for security rules hash validation.
 */
function simulateFirestoreRulesHash(input: string): string {
  return crypto.createHash("sha256").update(input, "utf8").digest("hex");
}

/**
 * Simulate hashForInteraction in security rules:
 * hashing.sha256(bytes(uid)).toHexString()
 */
function rulesHashForInteraction(uid: string): string {
  return simulateFirestoreRulesHash(uid);
}

/**
 * Simulate hashForNotification in security rules:
 * hashing.sha256(bytes("notification:" + uid)).toHexString()
 */
function rulesHashForNotification(uid: string): string {
  return simulateFirestoreRulesHash("notification:" + uid);
}

/**
 * Simulate hashForReport in security rules:
 * hashing.sha256(bytes("report:" + uid)).toHexString()
 */
function rulesHashForReport(uid: string): string {
  return simulateFirestoreRulesHash("report:" + uid);
}

describe("Security Rules Hash Validation", () => {
  // Test UID used across all tests
  const testUid = "TestUser123";

  /**
   * Test 1: Interactions read requires matching hashed ownerId
   *
   * Security rules should compute: hashForInteraction(request.auth.uid)
   * and compare with resource.data.ownerId
   *
   * The hash formula is: SHA256(uid)
   */
  describe("Test 1: Interactions read requires matching hashed ownerId", () => {
    it("should compute hash matching backend hashAnonymousId()", () => {
      // Security rules compute: hashing.sha256(bytes(uid)).toHexString()
      const rulesHash = rulesHashForInteraction(testUid);

      // Backend computes: hashAnonymousId(uid)
      const backendHash = hashAnonymousId(testUid);

      // They must match for authorization to work
      expect(rulesHash).toBe(backendHash);
    });

    it("should allow read when computed hash matches stored ownerId", () => {
      const authUid = testUid;

      // Document stored with hashed ownerId (as client would store it)
      const storedOwnerId = hashAnonymousId(authUid);

      // Security rules compute hash from auth.uid
      const computedHash = rulesHashForInteraction(authUid);

      // Authorization check: computedHash == storedOwnerId
      expect(computedHash).toBe(storedOwnerId);
    });

    it("should deny read when auth.uid does not match stored ownerId", () => {
      const authUid = "differentUser456";
      const documentOwner = testUid;

      // Document stored with original owner's hashed ID
      const storedOwnerId = hashAnonymousId(documentOwner);

      // Security rules compute hash from different auth.uid
      const computedHash = rulesHashForInteraction(authUid);

      // Authorization check should fail
      expect(computedHash).not.toBe(storedOwnerId);
    });
  });

  /**
   * Test 2: Notifications read requires matching hashed recipientId
   *
   * Security rules should compute: hashForNotification(request.auth.uid)
   * and compare with resource.data.recipientId
   *
   * The hash formula is: SHA256("notification:" + uid)
   */
  describe("Test 2: Notifications read requires matching hashed recipientId", () => {
    it("should compute hash matching backend hashForNotification()", () => {
      // Security rules compute: hashing.sha256(bytes("notification:" + uid)).toHexString()
      const rulesHash = rulesHashForNotification(testUid);

      // Backend computes: hashForNotification(uid)
      const backendHash = hashForNotification(testUid);

      // They must match for authorization to work
      expect(rulesHash).toBe(backendHash);
    });

    it("should allow read when computed hash matches stored recipientId", () => {
      const authUid = testUid;

      // Document stored with hashed recipientId (as backend would store it)
      const storedRecipientId = hashForNotification(authUid);

      // Security rules compute hash from auth.uid
      const computedHash = rulesHashForNotification(authUid);

      // Authorization check: computedHash == storedRecipientId
      expect(computedHash).toBe(storedRecipientId);
    });

    it("should deny read when auth.uid does not match stored recipientId", () => {
      const authUid = "differentUser456";
      const documentRecipient = testUid;

      // Document stored with original recipient's hashed ID
      const storedRecipientId = hashForNotification(documentRecipient);

      // Security rules compute hash from different auth.uid
      const computedHash = rulesHashForNotification(authUid);

      // Authorization check should fail
      expect(computedHash).not.toBe(storedRecipientId);
    });
  });

  /**
   * Test 3: Reports read requires matching hashed reporterId
   *
   * Security rules should compute: hashForReport(request.auth.uid)
   * and compare with resource.data.reporterId
   *
   * The hash formula is: SHA256("report:" + uid)
   */
  describe("Test 3: Reports read requires matching hashed reporterId", () => {
    it("should compute hash matching backend hashForReport()", () => {
      // Security rules compute: hashing.sha256(bytes("report:" + uid)).toHexString()
      const rulesHash = rulesHashForReport(testUid);

      // Backend computes: hashForReport(uid)
      const backendHash = hashForReport(testUid);

      // They must match for authorization to work
      expect(rulesHash).toBe(backendHash);
    });

    it("should allow read when computed hash matches stored reporterId", () => {
      const authUid = testUid;

      // Document stored with hashed reporterId (as client would store it)
      const storedReporterId = hashForReport(authUid);

      // Security rules compute hash from auth.uid
      const computedHash = rulesHashForReport(authUid);

      // Authorization check: computedHash == storedReporterId
      expect(computedHash).toBe(storedReporterId);
    });

    it("should deny read when auth.uid does not match stored reporterId", () => {
      const authUid = "differentUser456";
      const documentReporter = testUid;

      // Document stored with original reporter's hashed ID
      const storedReporterId = hashForReport(documentReporter);

      // Security rules compute hash from different auth.uid
      const computedHash = rulesHashForReport(authUid);

      // Authorization check should fail
      expect(computedHash).not.toBe(storedReporterId);
    });
  });

  /**
   * Test 4: Pre-computed hashes from client are rejected
   *
   * Security rules must COMPUTE the hash from request.auth.uid,
   * NOT trust any hash value provided by the client.
   *
   * This prevents attacks where a malicious client:
   * 1. Pre-computes hash for victim's UID
   * 2. Tries to read victim's documents
   *
   * The server always computes: hash(request.auth.uid) and compares to stored value.
   */
  describe("Test 4: Pre-computed client hashes are rejected", () => {
    it("should not allow access via pre-computed hash of another user", () => {
      const attackerUid = "attacker123";
      const victimUid = testUid;

      // Attacker knows victim's UID and pre-computes their hash
      const preComputedVictimHash = hashAnonymousId(victimUid);

      // But security rules use attacker's auth.uid to compute hash
      const rulesComputedHash = rulesHashForInteraction(attackerUid);

      // The pre-computed hash does NOT help - rules use auth.uid
      expect(rulesComputedHash).not.toBe(preComputedVictimHash);
    });

    it("should reject notification access via pre-computed recipientId hash", () => {
      const attackerUid = "attacker123";
      const victimUid = testUid;

      // Attacker pre-computes victim's notification hash
      const preComputedVictimHash = hashForNotification(victimUid);

      // But security rules use attacker's auth.uid to compute hash
      const rulesComputedHash = rulesHashForNotification(attackerUid);

      // The pre-computed hash does NOT help - rules use auth.uid
      expect(rulesComputedHash).not.toBe(preComputedVictimHash);
    });

    it("should reject report access via pre-computed reporterId hash", () => {
      const attackerUid = "attacker123";
      const victimUid = testUid;

      // Attacker pre-computes victim's report hash
      const preComputedVictimHash = hashForReport(victimUid);

      // But security rules use attacker's auth.uid to compute hash
      const rulesComputedHash = rulesHashForReport(attackerUid);

      // The pre-computed hash does NOT help - rules use auth.uid
      expect(rulesComputedHash).not.toBe(preComputedVictimHash);
    });
  });

  /**
   * Test 5: Domain separation prevents cross-collection attacks
   *
   * Even with correct auth.uid, accessing wrong collection type should fail
   * because each collection uses different salt prefixes.
   */
  describe("Test 5: Domain separation prevents cross-collection access", () => {
    it("should not allow interaction hash to access notifications", () => {
      const authUid = testUid;

      // Suppose a notification was stored for this user
      const storedNotificationRecipientId = hashForNotification(authUid);

      // If someone tried to use interaction hash format
      const wrongHash = rulesHashForInteraction(authUid);

      // It won't match the notification's recipientId
      expect(wrongHash).not.toBe(storedNotificationRecipientId);
    });

    it("should not allow notification hash to access reports", () => {
      const authUid = testUid;

      // Suppose a report was stored for this user
      const storedReporterId = hashForReport(authUid);

      // If someone tried to use notification hash format
      const wrongHash = rulesHashForNotification(authUid);

      // It won't match the report's reporterId
      expect(wrongHash).not.toBe(storedReporterId);
    });

    it("should not allow report hash to access interactions", () => {
      const authUid = testUid;

      // Suppose an interaction was stored for this user
      const storedOwnerId = hashAnonymousId(authUid);

      // If someone tried to use report hash format
      const wrongHash = rulesHashForReport(authUid);

      // It won't match the interaction's ownerId
      expect(wrongHash).not.toBe(storedOwnerId);
    });

    it("all three hashes for same UID should be different", () => {
      const authUid = testUid;

      const interactionHash = rulesHashForInteraction(authUid);
      const notificationHash = rulesHashForNotification(authUid);
      const reportHash = rulesHashForReport(authUid);

      // All three must be different (domain separation)
      expect(interactionHash).not.toBe(notificationHash);
      expect(interactionHash).not.toBe(reportHash);
      expect(notificationHash).not.toBe(reportHash);
    });
  });
});
