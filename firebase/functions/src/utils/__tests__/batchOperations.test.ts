/**
 * Batch Operations Utility Tests
 * Task 3.1: Tests for batch utilities
 *
 * Tests:
 * 1. Test NotificationBatcher with < 500 notifications
 * 2. Test NotificationBatcher with > 500 notifications (multiple batches)
 * 3. Test FCM multicast batching with various payload sizes
 * 4. Test batch commit error handling and partial failure recovery
 */

import {
  createNotificationBatcher,
  createFCMBatcher,
  PendingNotification,
  PendingFCM,
} from "../batch";
import { NotificationType, NotificationDocument } from "../../types";

// Track commit calls across all batch instances
let commitCallCount = 0;
let mockCommitFn: jest.Mock;

// Mock firebase-admin
jest.mock("firebase-admin", () => {
  const mockMessaging = {
    sendEachForMulticast: jest.fn().mockResolvedValue({
      successCount: 0,
      failureCount: 0,
      responses: [],
    }),
  };

  return {
    firestore: jest.fn().mockImplementation(() => {
      let docIdCounter = 0;
      return {
        batch: jest.fn().mockImplementation(() => ({
          set: jest.fn().mockReturnThis(),
          commit: jest.fn().mockImplementation(() => {
            commitCallCount++;
            if (mockCommitFn) {
              return mockCommitFn();
            }
            return Promise.resolve();
          }),
        })),
        collection: jest.fn().mockImplementation(() => ({
          doc: jest.fn().mockImplementation(() => ({
            id: `doc-${docIdCounter++}`,
          })),
        })),
      };
    }),
    messaging: jest.fn().mockReturnValue(mockMessaging),
  };
});

// Mock database utility
jest.mock("../database", () => {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const adminModule = require("firebase-admin");
  return {
    getDb: jest.fn().mockImplementation(() => adminModule.firestore()),
  };
});

// Import admin after mocking
import * as admin from "firebase-admin";

// Helper to create a mock notification document
function createMockNotificationData(index: number): NotificationDocument {
  return {
    recipientId: `recipient-${index}`,
    type: NotificationType.EXPOSURE,
    chainData: JSON.stringify({ nodes: [] }),
    isRead: false,
    receivedAt: Date.now(),
    updatedAt: Date.now(),
    reportId: `report-${index}`,
    chainPath: [`chain-${index}`],
  };
}

// Helper to create a pending notification
function createPendingNotification(index: number): PendingNotification {
  return {
    data: createMockNotificationData(index),
    hashedInteractionId: `hash-interaction-${index}`,
    hashedNotificationId: `hash-notification-${index}`,
  };
}

// Helper to create a pending FCM notification
function createPendingFCM(index: number, type: string = "EXPOSURE"): PendingFCM {
  return {
    token: `token-${index}`,
    notificationId: `notif-${index}`,
    type,
    titleLocKey: "notification_exposure_title",
    bodyLocKey: "notification_exposure_body",
  };
}

describe("Batch Operations Utilities", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    commitCallCount = 0;
    mockCommitFn = jest.fn().mockResolvedValue(undefined);

    const mockMessaging = admin.messaging();
    (mockMessaging.sendEachForMulticast as jest.Mock).mockResolvedValue({
      successCount: 0,
      failureCount: 0,
      responses: [],
    });
  });

  describe("Test 1: NotificationBatcher with < 500 notifications", () => {
    it("should add notifications to pending queue", () => {
      const batcher = createNotificationBatcher();

      batcher.add(createPendingNotification(1));
      batcher.add(createPendingNotification(2));
      batcher.add(createPendingNotification(3));

      expect(batcher.size).toBe(3);
      expect(batcher.isEmpty).toBe(false);
    });

    it("should commit notifications in a single batch", async () => {
      const batcher = createNotificationBatcher();

      // Add 10 notifications (< 500)
      for (let i = 0; i < 10; i++) {
        batcher.add(createPendingNotification(i));
      }

      const result = await batcher.commit();

      expect(result.successCount).toBe(10);
      expect(result.failureCount).toBe(0);
      expect(result.createdIds.length).toBe(10);
      expect(result.createdIds.every(id => id !== null)).toBe(true);
      expect(commitCallCount).toBe(1);
    });

    it("should return empty result when no notifications are pending", async () => {
      const batcher = createNotificationBatcher();

      const result = await batcher.commit();

      expect(result.successCount).toBe(0);
      expect(result.failureCount).toBe(0);
      expect(result.createdIds.length).toBe(0);
    });

    it("should provide created ID mapping", async () => {
      const batcher = createNotificationBatcher();

      batcher.add(createPendingNotification(1));
      batcher.add(createPendingNotification(2));

      const result = await batcher.commit();
      const idMap = batcher.getCreatedIdMap(result);

      expect(idMap.size).toBe(2);
      expect(idMap.has("hash-interaction-1")).toBe(true);
      expect(idMap.has("hash-interaction-2")).toBe(true);
    });
  });

  describe("Test 2: NotificationBatcher with > 500 notifications (multiple batches)", () => {
    it("should split notifications into multiple batches", async () => {
      const batcher = createNotificationBatcher();

      // Add 600 notifications (> 500, needs 2 batches)
      for (let i = 0; i < 600; i++) {
        batcher.add(createPendingNotification(i));
      }

      const result = await batcher.commit();

      expect(result.successCount).toBe(600);
      expect(result.failureCount).toBe(0);
      expect(result.createdIds.length).toBe(600);
      // Should have called batch.commit() twice (500 + 100)
      expect(commitCallCount).toBe(2);
    });

    it("should respect maxBatchSize option", async () => {
      const batcher = createNotificationBatcher({ maxBatchSize: 100 });

      // Add 250 notifications
      for (let i = 0; i < 250; i++) {
        batcher.add(createPendingNotification(i));
      }

      await batcher.commit();

      // Should have called batch.commit() 3 times (100 + 100 + 50)
      expect(commitCallCount).toBe(3);
    });

    it("should not exceed Firestore batch limit even if maxBatchSize is larger", async () => {
      const batcher = createNotificationBatcher({ maxBatchSize: 1000 });

      // Add 600 notifications
      for (let i = 0; i < 600; i++) {
        batcher.add(createPendingNotification(i));
      }

      await batcher.commit();

      // Should still use max 500 per batch (500 + 100)
      expect(commitCallCount).toBe(2);
    });
  });

  describe("Test 3: FCM multicast batching with various payload sizes", () => {
    it("should add FCM notifications to pending queue", () => {
      const batcher = createFCMBatcher();

      batcher.add(createPendingFCM(1));
      batcher.add(createPendingFCM(2));
      batcher.add(createPendingFCM(3));

      expect(batcher.size).toBe(3);
      expect(batcher.isEmpty).toBe(false);
    });

    it("should skip notifications with empty tokens", () => {
      const batcher = createFCMBatcher();

      batcher.add(createPendingFCM(1));
      batcher.add({ ...createPendingFCM(2), token: "" });
      batcher.add({ ...createPendingFCM(3), token: "   " });
      batcher.add(createPendingFCM(4));

      expect(batcher.size).toBe(2);
    });

    it("should send as single multicast when < 500 tokens", async () => {
      const batcher = createFCMBatcher();
      const mockMessaging = admin.messaging();

      (mockMessaging.sendEachForMulticast as jest.Mock).mockResolvedValue({
        successCount: 10,
        failureCount: 0,
        responses: new Array(10).fill({ success: true }),
      });

      // Add 10 FCM notifications with same payload
      for (let i = 0; i < 10; i++) {
        batcher.add(createPendingFCM(i));
      }

      const result = await batcher.send();

      expect(result.successCount).toBe(10);
      expect(result.failureCount).toBe(0);
      expect(mockMessaging.sendEachForMulticast).toHaveBeenCalledTimes(1);
    });

    it("should split into multiple multicasts when > 500 tokens", async () => {
      const batcher = createFCMBatcher();
      const mockMessaging = admin.messaging();

      (mockMessaging.sendEachForMulticast as jest.Mock).mockImplementation((msg) => ({
        successCount: msg.tokens.length,
        failureCount: 0,
        responses: new Array(msg.tokens.length).fill({ success: true }),
      }));

      // Add 600 FCM notifications with same payload
      for (let i = 0; i < 600; i++) {
        batcher.add(createPendingFCM(i));
      }

      const result = await batcher.send();

      expect(result.successCount).toBe(600);
      expect(result.failureCount).toBe(0);
      // Should have called sendEachForMulticast twice (500 + 100)
      expect(mockMessaging.sendEachForMulticast).toHaveBeenCalledTimes(2);
    });

    it("should group notifications by payload", async () => {
      const batcher = createFCMBatcher();
      const mockMessaging = admin.messaging();

      (mockMessaging.sendEachForMulticast as jest.Mock).mockImplementation((msg) => ({
        successCount: msg.tokens.length,
        failureCount: 0,
        responses: new Array(msg.tokens.length).fill({ success: true }),
      }));

      // Add notifications with different types (different payloads)
      for (let i = 0; i < 5; i++) {
        batcher.add(createPendingFCM(i, "EXPOSURE"));
      }
      for (let i = 5; i < 10; i++) {
        batcher.add({
          ...createPendingFCM(i, "UPDATE"),
          titleLocKey: "notification_update_title",
          bodyLocKey: "notification_update_body",
        });
      }

      await batcher.send();

      // Should have called sendEachForMulticast twice (one per payload type)
      expect(mockMessaging.sendEachForMulticast).toHaveBeenCalledTimes(2);
    });

    it("should return empty result when no notifications are pending", async () => {
      const batcher = createFCMBatcher();

      const result = await batcher.send();

      expect(result.successCount).toBe(0);
      expect(result.failureCount).toBe(0);
      expect(result.invalidTokenIndices.length).toBe(0);
    });
  });

  describe("Test 4: Batch commit error handling and partial failure recovery", () => {
    it("should handle batch commit failure", async () => {
      const batcher = createNotificationBatcher();

      // Make batch.commit() fail
      mockCommitFn = jest.fn().mockRejectedValue(new Error("Batch commit failed"));

      for (let i = 0; i < 5; i++) {
        batcher.add(createPendingNotification(i));
      }

      const result = await batcher.commit();

      expect(result.successCount).toBe(0);
      expect(result.failureCount).toBe(5);
      expect(result.errors.filter(e => e !== null).length).toBe(5);
      expect(result.errors[0]).toContain("Batch commit failed");
    });

    it("should handle partial batch failures", async () => {
      const batcher = createNotificationBatcher({ maxBatchSize: 5 });

      let callCount = 0;
      mockCommitFn = jest.fn().mockImplementation(() => {
        callCount++;
        // First batch succeeds, second fails
        if (callCount === 2) {
          return Promise.reject(new Error("Second batch failed"));
        }
        return Promise.resolve();
      });

      // Add 10 notifications (2 batches of 5)
      for (let i = 0; i < 10; i++) {
        batcher.add(createPendingNotification(i));
      }

      const result = await batcher.commit();

      expect(result.successCount).toBe(5);
      expect(result.failureCount).toBe(5);
      // First 5 should have IDs, last 5 should have errors
      expect(result.createdIds.slice(0, 5).every(id => id !== null)).toBe(true);
      expect(result.errors.slice(5, 10).every(e => e !== null)).toBe(true);
    });

    it("should identify invalid FCM tokens", async () => {
      const batcher = createFCMBatcher();
      const mockMessaging = admin.messaging();

      (mockMessaging.sendEachForMulticast as jest.Mock).mockResolvedValue({
        successCount: 3,
        failureCount: 2,
        responses: [
          { success: true },
          { success: false, error: { code: "messaging/invalid-registration-token" } },
          { success: true },
          { success: false, error: { code: "messaging/registration-token-not-registered" } },
          { success: true },
        ],
      });

      for (let i = 0; i < 5; i++) {
        batcher.add(createPendingFCM(i));
      }

      const result = await batcher.send();

      expect(result.successCount).toBe(3);
      expect(result.failureCount).toBe(2);
      expect(result.invalidTokenIndices).toContain(1);
      expect(result.invalidTokenIndices).toContain(3);

      const invalidTokens = batcher.getInvalidTokens(result);
      expect(invalidTokens).toContain("token-1");
      expect(invalidTokens).toContain("token-3");
    });

    it("should handle complete FCM multicast failure", async () => {
      const batcher = createFCMBatcher();
      const mockMessaging = admin.messaging();

      (mockMessaging.sendEachForMulticast as jest.Mock).mockRejectedValue(
        new Error("FCM service unavailable")
      );

      for (let i = 0; i < 5; i++) {
        batcher.add(createPendingFCM(i));
      }

      const result = await batcher.send();

      expect(result.successCount).toBe(0);
      expect(result.failureCount).toBe(5);
    });

    it("should prevent adding after commit", () => {
      const batcher = createNotificationBatcher();

      batcher.add(createPendingNotification(1));
      batcher.commit(); // Mark as committed

      expect(() => {
        batcher.add(createPendingNotification(2));
      }).toThrow("Cannot add after commit()");
    });

    it("should prevent adding after send for FCMBatcher", () => {
      const batcher = createFCMBatcher();

      batcher.add(createPendingFCM(1));
      batcher.send(); // Mark as sent

      expect(() => {
        batcher.add(createPendingFCM(2));
      }).toThrow("Cannot add after send()");
    });

    it("should handle multiple commit calls gracefully", async () => {
      const batcher = createNotificationBatcher();

      batcher.add(createPendingNotification(1));

      const result1 = await batcher.commit();
      const result2 = await batcher.commit();

      expect(result1.successCount).toBe(1);
      expect(result2.successCount).toBe(0);
    });

    it("should allow clear and reuse", async () => {
      const batcher = createNotificationBatcher();

      batcher.add(createPendingNotification(1));
      await batcher.commit();

      // Clear should reset the batcher
      batcher.clear();

      expect(batcher.size).toBe(0);
      expect(batcher.isEmpty).toBe(true);

      // Should be able to add again after clear
      batcher.add(createPendingNotification(2));
      expect(batcher.size).toBe(1);
    });
  });
});
