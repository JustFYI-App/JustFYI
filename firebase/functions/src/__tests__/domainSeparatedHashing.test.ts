/**
 * Domain-Separated Hashing Tests
 * Task 1.1: Write 4-6 focused tests for domain-separated hashing functions
 *
 * These tests verify:
 * 1. hashAnonymousId() with no salt produces expected hash
 * 2. hashForNotification() with "notification:" prefix produces different hash
 * 3. hashForChain() with "chain:" prefix produces different hash
 * 4. hashForReport() with "report:" prefix produces different hash
 * 5. Hash output is lowercase hex string
 */

import * as crypto from "crypto";
import {
  hashAnonymousId,
  hashForNotification,
  hashForChain,
  hashForReport,
} from "../utils/chainPropagation";

/**
 * Reference implementation for test verification
 * This matches exactly what the implementation should produce
 */
function referenceHash(input: string): string {
  return crypto.createHash("sha256").update(input, "utf8").digest("hex");
}

describe("Domain-Separated Hashing Functions", () => {
  // Test UID used across all tests
  const testUid = "TestUser123";

  /**
   * Test 1: hashAnonymousId() with no salt produces expected hash
   *
   * This function is used for partnerAnonymousId and must remain compatible
   * with BLE exchange (no salt prefix). It should produce SHA256(uid).
   */
  describe("Test 1: hashAnonymousId() with no salt", () => {
    it("should produce SHA256 hash of UID", () => {
      const result = hashAnonymousId(testUid);
      const expected = referenceHash(testUid);

      expect(result).toBe(expected);
    });

    it("should maintain BLE compatibility (no salt prefix)", () => {
      // The hash should be identical to raw SHA256 of input
      const rawHash = referenceHash(testUid);
      const functionHash = hashAnonymousId(testUid);

      expect(functionHash).toBe(rawHash);
    });
  });

  /**
   * Test 2: hashForNotification() with "notification:" prefix produces different hash
   *
   * Used for notifications.recipientId field.
   * Formula: SHA256("notification:" + uid)
   */
  describe("Test 2: hashForNotification() produces salted hash", () => {
    it("should produce SHA256 hash with notification: prefix", () => {
      const result = hashForNotification(testUid);
      const expected = referenceHash("notification:" + testUid);

      expect(result).toBe(expected);
    });

    it("should produce different hash than unsalted hashAnonymousId()", () => {
      const unsalted = hashAnonymousId(testUid);
      const salted = hashForNotification(testUid);

      expect(salted).not.toBe(unsalted);
    });
  });

  /**
   * Test 3: hashForChain() with "chain:" prefix produces different hash
   *
   * Used for notifications.chainPath and chainPaths fields.
   * Formula: SHA256("chain:" + uid)
   */
  describe("Test 3: hashForChain() produces salted hash", () => {
    it("should produce SHA256 hash with chain: prefix", () => {
      const result = hashForChain(testUid);
      const expected = referenceHash("chain:" + testUid);

      expect(result).toBe(expected);
    });

    it("should produce different hash than unsalted hashAnonymousId()", () => {
      const unsalted = hashAnonymousId(testUid);
      const salted = hashForChain(testUid);

      expect(salted).not.toBe(unsalted);
    });

    it("should produce different hash than hashForNotification()", () => {
      const notificationHash = hashForNotification(testUid);
      const chainHash = hashForChain(testUid);

      expect(chainHash).not.toBe(notificationHash);
    });
  });

  /**
   * Test 4: hashForReport() with "report:" prefix produces different hash
   *
   * Used for reports.reporterId field.
   * Formula: SHA256("report:" + uid)
   */
  describe("Test 4: hashForReport() produces salted hash", () => {
    it("should produce SHA256 hash with report: prefix", () => {
      const result = hashForReport(testUid);
      const expected = referenceHash("report:" + testUid);

      expect(result).toBe(expected);
    });

    it("should produce different hash than unsalted hashAnonymousId()", () => {
      const unsalted = hashAnonymousId(testUid);
      const salted = hashForReport(testUid);

      expect(salted).not.toBe(unsalted);
    });

    it("should produce different hash than other domain-specific hashes", () => {
      const notificationHash = hashForNotification(testUid);
      const chainHash = hashForChain(testUid);
      const reportHash = hashForReport(testUid);

      expect(reportHash).not.toBe(notificationHash);
      expect(reportHash).not.toBe(chainHash);
    });
  });

  /**
   * Test 5: Hash output is lowercase hex string
   *
   * All hash functions must return lowercase hexadecimal strings
   * for consistency across the system.
   */
  describe("Test 5: Hash output format", () => {
    it("should produce 64-character lowercase hex string (hashAnonymousId)", () => {
      const result = hashAnonymousId(testUid);

      // SHA-256 produces 256 bits = 64 hex characters
      expect(result).toHaveLength(64);
      // Should be all lowercase hex characters
      expect(result).toMatch(/^[0-9a-f]{64}$/);
      // Should not contain uppercase letters
      expect(result).not.toMatch(/[A-F]/);
    });

    it("should produce 64-character lowercase hex string (hashForNotification)", () => {
      const result = hashForNotification(testUid);

      expect(result).toHaveLength(64);
      expect(result).toMatch(/^[0-9a-f]{64}$/);
      expect(result).not.toMatch(/[A-F]/);
    });

    it("should produce 64-character lowercase hex string (hashForChain)", () => {
      const result = hashForChain(testUid);

      expect(result).toHaveLength(64);
      expect(result).toMatch(/^[0-9a-f]{64}$/);
      expect(result).not.toMatch(/[A-F]/);
    });

    it("should produce 64-character lowercase hex string (hashForReport)", () => {
      const result = hashForReport(testUid);

      expect(result).toHaveLength(64);
      expect(result).toMatch(/^[0-9a-f]{64}$/);
      expect(result).not.toMatch(/[A-F]/);
    });
  });

  /**
   * Additional test: Cross-collection correlation prevention
   *
   * Verify that the same UID produces different hashes in different contexts,
   * preventing correlation attacks if the database is breached.
   */
  describe("Cross-collection correlation prevention", () => {
    it("should produce four distinct hashes for the same UID", () => {
      const uid = "user123abc";

      const interactionHash = hashAnonymousId(uid);
      const notificationHash = hashForNotification(uid);
      const chainHash = hashForChain(uid);
      const reportHash = hashForReport(uid);

      // All four should be distinct
      const hashes = [interactionHash, notificationHash, chainHash, reportHash];
      const uniqueHashes = new Set(hashes);

      expect(uniqueHashes.size).toBe(4);
    });
  });
});
