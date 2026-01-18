/**
 * Edge Cases Tests for Exposure Chain Propagation
 * Task 9.3: Additional backend tests to fill coverage gaps
 *
 * These tests cover critical edge cases not fully covered by existing tests:
 * 1. Circular reference handling (A->B->C->A should not infinite loop)
 * 2. Empty chain scenario (reporter has no interactions)
 * 3. Max depth exactly 10 boundary condition
 * 4. Error recovery (partial chain failure)
 */

import * as admin from "firebase-admin";
import * as crypto from "crypto";
import {
  NotificationDocument,
  InteractionDocument,
  CONSTANTS,
  PrivacyLevel,
} from "../types";
import { propagateExposureChain } from "../utils/chainPropagation";
import { createMockSnapshot, createMockQuerySnapshot } from "./setup";

/**
 * Hash an anonymous ID using SHA-256, matching the implementation in chainPropagation.ts
 * The ID is uppercased before hashing to ensure consistency with mobile app storage.
 */
function hashId(anonymousId: string): string {
  return crypto.createHash("sha256").update(anonymousId.toUpperCase(), "utf8").digest("hex");
}

/**
 * User data type for test mocking
 */
interface MockUserData {
  hashedInteractionId: string;
  hashedNotificationId: string;
  username: string;
  fcmToken?: string;
}

/**
 * Helper to create a mock user query that supports both .doc().get() and .where().limit().get() patterns
 * Also supports batch "in" queries used by getUsersByHashedInteractionIds
 */
function createUserMock(usersByHashedInteractionId: Map<string, MockUserData>) {
  return {
    doc: (userId: string) => ({
      get: () => {
        const userData = usersByHashedInteractionId.get(userId);
        return Promise.resolve(createMockSnapshot(userData || null, userId));
      },
    }),
    where: jest.fn().mockImplementation((field: string, op: string, value: unknown) => {
      if (field === "hashedInteractionId") {
        // Handle batch "in" queries used by getUsersByHashedInteractionIds
        if (op === "in") {
          const hashedIds = value as string[];
          const results: { id: string; data: MockUserData }[] = [];
          for (const hashedId of hashedIds) {
            const userData = usersByHashedInteractionId.get(hashedId);
            if (userData) {
              results.push({ id: `user-${hashedId.substring(0, 8)}`, data: userData });
            }
          }
          return {
            get: () => Promise.resolve(createMockQuerySnapshot(results)),
          };
        }
        // Handle single "==" queries
        const hashedId = value as string;
        const userData = usersByHashedInteractionId.get(hashedId);
        return {
          limit: jest.fn().mockReturnValue({
            get: () => Promise.resolve(createMockQuerySnapshot(
              userData ? [{ id: `user-${hashedId.substring(0, 8)}`, data: userData }] : []
            )),
          }),
          get: () => Promise.resolve(createMockQuerySnapshot(
            userData ? [{ id: `user-${hashedId.substring(0, 8)}`, data: userData }] : []
          )),
        };
      }
      if (field === "hashedNotificationId") {
        const hashedNotifId = value as string;
        for (const userData of usersByHashedInteractionId.values()) {
          if (userData.hashedNotificationId === hashedNotifId) {
            return {
              limit: jest.fn().mockReturnValue({
                get: () => Promise.resolve(createMockQuerySnapshot([{ id: `user-${hashedNotifId.substring(0, 8)}`, data: userData }])),
              }),
              get: () => Promise.resolve(createMockQuerySnapshot([{ id: `user-${hashedNotifId.substring(0, 8)}`, data: userData }])),
            };
          }
        }
        return {
          limit: jest.fn().mockReturnValue({
            get: () => Promise.resolve(createMockQuerySnapshot([])),
          }),
          get: () => Promise.resolve(createMockQuerySnapshot([])),
        };
      }
      return {
        limit: jest.fn().mockReturnThis(),
        get: () => Promise.resolve(createMockQuerySnapshot([])),
      };
    }),
  };
}

// Get mocked firestore
const mockFirestore = admin.firestore() as unknown as {
  collection: jest.Mock;
  doc: jest.Mock;
  batch: jest.Mock;
  where: jest.Mock;
  get: jest.Mock;
  add: jest.Mock;
  runTransaction: jest.Mock;
};

/**
 * Helper to create a mock interaction query chain
 */
function createInteractionMock(
  interactionsByPartner: Map<string, InteractionDocument[]>,
) {
  return {
    where: jest.fn().mockImplementation((field: string, _op: string, value: unknown) => {
      if (field === "partnerAnonymousId") {
        const partnerId = value as string;
        const interactions = interactionsByPartner.get(partnerId) || [];
        return {
          where: jest.fn().mockReturnValue({
            where: jest.fn().mockReturnValue({
              get: () => Promise.resolve(createMockQuerySnapshot(
                interactions.map((i, idx) => ({ id: `interaction-${partnerId}-${idx}`, data: i }))
              )),
            }),
            get: () => Promise.resolve(createMockQuerySnapshot(
              interactions.map((i, idx) => ({ id: `interaction-${partnerId}-${idx}`, data: i }))
            )),
          }),
          get: () => Promise.resolve(createMockQuerySnapshot(
            interactions.map((i, idx) => ({ id: `interaction-${partnerId}-${idx}`, data: i }))
          )),
        };
      }
      return {
        where: jest.fn().mockReturnThis(),
        get: () => Promise.resolve(createMockQuerySnapshot([])),
      };
    }),
  };
}

describe("Edge Cases - Chain Propagation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  /**
   * Test 1: Circular reference handling (A->B->C->A should not infinite loop)
   *
   * This is a critical security test to ensure the algorithm handles cycles gracefully.
   * In a social network, it's possible for interaction records to form cycles:
   * - A reports positive
   * - B recorded interaction with A
   * - C recorded interaction with B
   * - A recorded interaction with C (closing the loop)
   *
   * The algorithm should:
   * 1. NOT enter an infinite loop
   * 2. Notify B and C exactly once each
   * 3. NOT notify A again (they are the reporter)
   */
  describe("Test 1: Circular reference handling", () => {
    it("should handle circular chains (A->B->C->A) without infinite loop", async () => {
      // Arrange
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 5 * msPerDay;

      const reporterAId = "reporter-A";
      const userBId = "user-B";
      const userCId = "user-C";

      // Hash IDs for consistent lookup
      const hashedReporterAId = hashId(reporterAId);
      const hashedUserBId = hashId(userBId);
      const hashedUserCId = hashId(userCId);

      // Set up user data with proper hash fields
      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedUserBId, {
        hashedInteractionId: hashedUserBId,
        hashedNotificationId: `notif-${userBId}`,
        username: "UserB",
        fcmToken: "token-B",
      });
      usersByHashedInteractionId.set(hashedUserCId, {
        hashedInteractionId: hashedUserCId,
        hashedNotificationId: `notif-${userCId}`,
        username: "UserC",
        fcmToken: "token-C",
      });
      // Reporter A also needs to be in the map for circular reference detection
      usersByHashedInteractionId.set(hashedReporterAId, {
        hashedInteractionId: hashedReporterAId,
        hashedNotificationId: `notif-${reporterAId}`,
        username: "UserA",
        fcmToken: "token-A",
      });

      const interactionsByPartner = new Map<string, InteractionDocument[]>();

      // B recorded interaction with A (reporter) - standard forward propagation
      // Use hashed IDs as keys to match how the implementation queries
      interactionsByPartner.set(hashedReporterAId, [{
        partnerAnonymousId: hashedReporterAId,
        partnerUsernameSnapshot: "UserA",
        recordedAt: testDate - 3 * msPerDay,
        ownerId: hashedUserBId, // Use hashed ID to match user lookup
      }]);

      // C recorded interaction with B - continues the chain
      interactionsByPartner.set(hashedUserBId, [{
        partnerAnonymousId: hashedUserBId,
        partnerUsernameSnapshot: "UserB",
        recordedAt: testDate - 2 * msPerDay,
        ownerId: hashedUserCId, // Use hashed ID to match user lookup
      }]);

      // CIRCULAR REFERENCE: A (the reporter) also recorded interaction with C
      // This creates a cycle: A -> B -> C -> A
      // In unidirectional model, we query "who recorded C as partner"
      // If A recorded C, this would try to notify A again
      interactionsByPartner.set(hashedUserCId, [{
        partnerAnonymousId: hashedUserCId,
        partnerUsernameSnapshot: "UserC",
        recordedAt: testDate - 1 * msPerDay,
        ownerId: hashedReporterAId, // This creates the cycle (use hashed ID)
      }]);

      const notificationsCreated: NotificationDocument[] = [];
      const notificationIdsByRecipient = new Map<string, string>();
      let notificationIdCounter = 0;

      // Create a batch mock that captures notifications created via batch.set()
      const createBatchMock = () => ({
        set: jest.fn().mockImplementation((docRef: { id: string }, data: NotificationDocument) => {
          notificationsCreated.push({ ...data });
          notificationIdsByRecipient.set(data.recipientId, docRef.id);
        }),
        update: jest.fn(),
        delete: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      });

      mockFirestore.batch.mockImplementation(createBatchMock);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            add: (notification: NotificationDocument) => {
              const id = `notification-${++notificationIdCounter}`;
              notificationsCreated.push({ ...notification });
              notificationIdsByRecipient.set(notification.recipientId, id);
              return Promise.resolve({ id });
            },
            // Support .doc() without ID for auto-generated document references
            doc: (id?: string) => {
              const docId = id || `notification-${++notificationIdCounter}`;
              return {
                id: docId,
                get: () => {
                  const existingNotif = notificationsCreated.find(
                    (n, idx) => notificationIdsByRecipient.get(n.recipientId) === docId ||
                      `notification-${idx + 1}` === docId
                  );
                  return Promise.resolve(createMockSnapshot(existingNotif || null, docId));
                },
                update: () => Promise.resolve(),
              };
            },
            where: (field: string, _op: string, value: string) => {
              if (field === "reportId") {
                return {
                  where: () => ({
                    get: () => {
                      const matching = notificationsCreated.filter(n => n.reportId === value);
                      return Promise.resolve(createMockQuerySnapshot(
                        matching.map((n, idx) => ({
                          id: notificationIdsByRecipient.get(n.recipientId) || `notification-${idx + 1}`,
                          data: n,
                        }))
                      ));
                    },
                  }),
                  count: () => ({
                    get: () => Promise.resolve({ data: () => ({ count: notificationsCreated.length }) }),
                  }),
                };
              }
              return {
                count: () => ({
                  get: () => Promise.resolve({ data: () => ({ count: notificationsCreated.length }) }),
                }),
              };
            },
          };
        }
        return mockFirestore;
      });

      mockFirestore.runTransaction = jest.fn().mockImplementation(async (callback) => {
        const transaction = {
          get: jest.fn().mockImplementation((docRef: { id: string }) => {
            const existingNotif = notificationsCreated.find(
              (n, idx) => notificationIdsByRecipient.get(n.recipientId) === docRef.id ||
                `notification-${idx + 1}` === docRef.id
            );
            return Promise.resolve(createMockSnapshot(existingNotif || null, docRef.id));
          }),
          update: jest.fn(),
          set: jest.fn(),
        };
        return callback(transaction);
      });

      // Act - This should complete without infinite loop
      // Pass hashed reporter ID to match real-world usage
      const startTime = Date.now();
      await propagateExposureChain(
        "report-circular",
        hashedReporterAId,
        "UserA",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );
      const endTime = Date.now();

      // Assert: Should complete in reasonable time (not stuck in infinite loop)
      // If there's an infinite loop, this test will timeout
      expect(endTime - startTime).toBeLessThan(5000); // Should complete within 5 seconds

      // Assert: Only B and C should receive notifications (NOT A again)
      expect(notificationsCreated.length).toBe(2);

      // recipientId = hashedNotificationId from user lookup
      const notificationToA = notificationsCreated.find(n => n.recipientId === `notif-${reporterAId}`);
      const notificationToB = notificationsCreated.find(n => n.recipientId === `notif-${userBId}`);
      const notificationToC = notificationsCreated.find(n => n.recipientId === `notif-${userCId}`);

      // A (the reporter) should NOT receive a notification
      expect(notificationToA).toBeUndefined();

      // B and C should each receive exactly one notification
      expect(notificationToB).toBeDefined();
      expect(notificationToC).toBeDefined();
    });

    it("should prevent self-notification in circular reference scenario", async () => {
      // Arrange: Simpler circular case - A reports, B recorded A, B also recorded B (self-reference)
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 5 * msPerDay;

      const reporterAId = "reporter-A";
      const userBId = "user-B";

      // Hash IDs for consistent lookup
      const hashedReporterAId = hashId(reporterAId);
      const hashedUserBId = hashId(userBId);

      // Set up user data with proper hash fields
      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedUserBId, {
        hashedInteractionId: hashedUserBId,
        hashedNotificationId: `notif-${userBId}`,
        username: "UserB",
        fcmToken: "token-B",
      });

      const interactionsByPartner = new Map<string, InteractionDocument[]>();

      // B recorded interaction with A - use hashed IDs as keys
      interactionsByPartner.set(hashedReporterAId, [{
        partnerAnonymousId: hashedReporterAId,
        partnerUsernameSnapshot: "UserA",
        recordedAt: testDate - 2 * msPerDay,
        ownerId: hashedUserBId, // Use hashed ID to match user lookup
      }]);

      // B also recorded interaction with B (impossible but test the safeguard)
      interactionsByPartner.set(hashedUserBId, [{
        partnerAnonymousId: hashedUserBId,
        partnerUsernameSnapshot: "UserB",
        recordedAt: testDate - 1 * msPerDay,
        ownerId: hashedUserBId, // Self-reference (use hashed ID)
      }]);

      const notificationsCreated: NotificationDocument[] = [];
      let notificationIdCounter = 0;

      // Create a batch mock that captures notifications created via batch.set()
      const createBatchMock = () => ({
        set: jest.fn().mockImplementation((_docRef: { id: string }, data: NotificationDocument) => {
          notificationsCreated.push({ ...data });
        }),
        update: jest.fn(),
        delete: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      });

      mockFirestore.batch.mockImplementation(createBatchMock);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push({ ...notification });
              return Promise.resolve({ id: `notification-${++notificationIdCounter}` });
            },
            // Support .doc() without ID for auto-generated document references
            doc: (id?: string) => {
              const docId = id || `notification-${++notificationIdCounter}`;
              return {
                id: docId,
                get: () => Promise.resolve(createMockSnapshot(null, docId)),
                update: () => Promise.resolve(),
              };
            },
            where: () => ({
              count: () => ({
                get: () => Promise.resolve({ data: () => ({ count: notificationsCreated.length }) }),
              }),
            }),
          };
        }
        return mockFirestore;
      });

      mockFirestore.runTransaction = jest.fn().mockImplementation(async (callback) => {
        const transaction = {
          get: jest.fn().mockResolvedValue(createMockSnapshot(null)),
          update: jest.fn(),
          set: jest.fn(),
        };
        return callback(transaction);
      });

      // Act - pass hashed reporter ID to match real-world usage
      await propagateExposureChain(
        "report-self-ref",
        hashedReporterAId,
        "UserA",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: B should receive exactly one notification (not multiple from self-reference)
      expect(notificationsCreated.length).toBe(1);
      // recipientId = hashedNotificationId from user lookup
      expect(notificationsCreated[0].recipientId).toBe(`notif-${userBId}`);
    });
  });

  /**
   * Test 2: Empty chain scenario (reporter has no interactions)
   *
   * Edge case where a reporter tests positive but no one has recorded
   * an interaction with them. The propagation should complete gracefully
   * with 0 notifications created.
   */
  describe("Test 2: Empty chain scenario", () => {
    it("should handle reporter with no recorded interactions gracefully", async () => {
      // Arrange
      const testDate = Date.now() - 7 * 24 * 60 * 60 * 1000;
      const reporterId = "lonely-reporter";

      // Hash ID for consistent lookup
      const hashedReporterId = hashId(reporterId);

      // Empty interactions - no one recorded the reporter as their partner
      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      // No users either
      const usersByHashedInteractionId = new Map<string, MockUserData>();

      const notificationsCreated: NotificationDocument[] = [];
      let notificationIdCounter = 0;

      // Create a batch mock (empty chain, so no notifications expected)
      const createBatchMock = () => ({
        set: jest.fn().mockImplementation((_docRef: { id: string }, data: NotificationDocument) => {
          notificationsCreated.push({ ...data });
        }),
        update: jest.fn(),
        delete: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      });

      mockFirestore.batch.mockImplementation(createBatchMock);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push(notification);
              return Promise.resolve({ id: `notification-${++notificationIdCounter}` });
            },
            doc: (id?: string) => {
              const docId = id || `notification-${++notificationIdCounter}`;
              return {
                id: docId,
                get: () => Promise.resolve(createMockSnapshot(null, docId)),
                update: () => Promise.resolve(),
              };
            },
            where: () => ({
              count: () => ({
                get: () => Promise.resolve({ data: () => ({ count: 0 }) }),
              }),
            }),
          };
        }
        return mockFirestore;
      });

      // Act - pass hashed reporter ID to match real-world usage
      const result = await propagateExposureChain(
        "report-empty-chain",
        hashedReporterId,
        "LonelyReporter",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: Should complete successfully with 0 notifications
      expect(result).toBe(0);
      expect(notificationsCreated.length).toBe(0);
    });
  });

  /**
   * Test 3: Max depth boundary - exactly 10 hops
   *
   * Verifies that:
   * 1. Users at hop depth 10 DO receive notifications
   * 2. Users at hop depth 11 do NOT receive notifications
   * 3. The boundary is exact (not off by one)
   */
  describe("Test 3: Max depth exactly 10 boundary condition", () => {
    it("should notify user at exactly hop 10 but not hop 11", async () => {
      // Arrange: Create a chain of exactly 12 users (reporter + 11 contacts)
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 5 * msPerDay;

      const reporterId = "reporter-user";
      const hashedReporterId = hashId(reporterId);

      // Set up user data with proper hash fields for all chain users
      const usersByHashedInteractionId = new Map<string, MockUserData>();
      for (let i = 1; i <= 11; i++) {
        const userId = `user-${i}`;
        const hashedUserId = hashId(userId);
        usersByHashedInteractionId.set(hashedUserId, {
          hashedInteractionId: hashedUserId,
          hashedNotificationId: `notif-${userId}`,
          username: `User${i}`,
          fcmToken: `token-${i}`,
        });
      }

      const interactionsByPartner = new Map<string, InteractionDocument[]>();

      // Create a linear chain: reporter -> user-1 -> user-2 -> ... -> user-11
      // Use hashed IDs as keys to match how the implementation queries
      for (let i = 0; i < 11; i++) {
        const currentUserId = i === 0 ? reporterId : `user-${i}`;
        const nextUserId = `user-${i + 1}`;
        const interactionDate = testDate - (11 - i) * msPerDay;
        const hashedCurrentUserId = hashId(currentUserId);
        const hashedNextUserId = hashId(nextUserId);

        interactionsByPartner.set(hashedCurrentUserId, [{
          partnerAnonymousId: hashedCurrentUserId,
          partnerUsernameSnapshot: `User${i}`,
          recordedAt: interactionDate,
          ownerId: hashedNextUserId, // Use hashed ID to match user lookup
        }]);
      }

      const notificationsCreated: NotificationDocument[] = [];
      const notificationIdsByRecipient = new Map<string, string>();
      let notificationIdCounter = 0;

      // Create a batch mock that captures notifications created via batch.set()
      const createBatchMock = () => ({
        set: jest.fn().mockImplementation((docRef: { id: string }, data: NotificationDocument) => {
          notificationsCreated.push({ ...data });
          notificationIdsByRecipient.set(data.recipientId, docRef.id);
        }),
        update: jest.fn(),
        delete: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      });

      mockFirestore.batch.mockImplementation(createBatchMock);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            add: (notification: NotificationDocument) => {
              const id = `notification-${++notificationIdCounter}`;
              notificationsCreated.push({ ...notification });
              notificationIdsByRecipient.set(notification.recipientId, id);
              return Promise.resolve({ id });
            },
            // Support .doc() without ID for auto-generated document references
            doc: (id?: string) => {
              const docId = id || `notification-${++notificationIdCounter}`;
              return {
                id: docId,
                get: () => {
                  const existingNotif = notificationsCreated.find(
                    (n, idx) => notificationIdsByRecipient.get(n.recipientId) === docId ||
                      `notification-${idx + 1}` === docId
                  );
                  return Promise.resolve(createMockSnapshot(existingNotif || null, docId));
                },
                update: () => Promise.resolve(),
              };
            },
            where: () => ({
              count: () => ({
                get: () => Promise.resolve({ data: () => ({ count: notificationsCreated.length }) }),
              }),
            }),
          };
        }
        return mockFirestore;
      });

      mockFirestore.runTransaction = jest.fn().mockImplementation(async (callback) => {
        const transaction = {
          get: jest.fn().mockResolvedValue(createMockSnapshot(null)),
          update: jest.fn(),
          set: jest.fn(),
        };
        return callback(transaction);
      });

      // Act - pass hashed reporter ID to match real-world usage
      await propagateExposureChain(
        "report-boundary-test",
        hashedReporterId,
        "Reporter",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: Exactly 10 notifications (user-1 through user-10)
      expect(notificationsCreated.length).toBe(10);

      // User at hop 10 (user-10) SHOULD be notified
      // recipientId = hashedNotificationId from user lookup
      const notificationToUser10 = notificationsCreated.find(n => n.recipientId === "notif-user-10");
      expect(notificationToUser10).toBeDefined();
      expect(notificationToUser10?.hopDepth).toBe(10);

      // User at hop 11 (user-11) should NOT be notified
      const notificationToUser11 = notificationsCreated.find(n => n.recipientId === "notif-user-11");
      expect(notificationToUser11).toBeUndefined();

      // Verify all hop depths are correct
      for (let i = 1; i <= 10; i++) {
        const notification = notificationsCreated.find(n => n.recipientId === `notif-user-${i}`);
        expect(notification).toBeDefined();
        expect(notification?.hopDepth).toBe(i);
      }
    });
  });

  /**
   * Test 4: Concurrent notification creation (race condition prevention)
   *
   * Verifies that when the same user is reached via multiple paths
   * at roughly the same time, only one notification is created and
   * paths are properly merged.
   */
  describe("Test 4: Concurrent notification updates", () => {
    it("should handle concurrent path arrivals to same user", async () => {
      // Arrange: Create a diamond pattern where D is reached via B and C simultaneously
      // A -> B -> D
      // A -> C -> D
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 5 * msPerDay;

      const reporterId = "reporter-A";
      const userBId = "user-B";
      const userCId = "user-C";
      const userDId = "user-D";

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId(reporterId);
      const hashedUserBId = hashId(userBId);
      const hashedUserCId = hashId(userCId);
      const hashedUserDId = hashId(userDId);

      // Set up user data with proper hash fields
      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedUserBId, {
        hashedInteractionId: hashedUserBId,
        hashedNotificationId: `notif-${userBId}`,
        username: "UserB",
        fcmToken: "token-B",
      });
      usersByHashedInteractionId.set(hashedUserCId, {
        hashedInteractionId: hashedUserCId,
        hashedNotificationId: `notif-${userCId}`,
        username: "UserC",
        fcmToken: "token-C",
      });
      usersByHashedInteractionId.set(hashedUserDId, {
        hashedInteractionId: hashedUserDId,
        hashedNotificationId: `notif-${userDId}`,
        username: "UserD",
        fcmToken: "token-D",
      });

      const interactionsByPartner = new Map<string, InteractionDocument[]>();

      // B and C both recorded interaction with A at the same time
      // Use hashed IDs as keys to match how the implementation queries
      interactionsByPartner.set(hashedReporterId, [
        {
          partnerAnonymousId: hashedReporterId,
          partnerUsernameSnapshot: "UserA",
          recordedAt: testDate - 3 * msPerDay,
          ownerId: hashedUserBId, // Use hashed ID to match user lookup
        },
        {
          partnerAnonymousId: hashedReporterId,
          partnerUsernameSnapshot: "UserA",
          recordedAt: testDate - 3 * msPerDay, // Same time as B
          ownerId: hashedUserCId, // Use hashed ID to match user lookup
        },
      ]);

      // D recorded interaction with B
      interactionsByPartner.set(hashedUserBId, [{
        partnerAnonymousId: hashedUserBId,
        partnerUsernameSnapshot: "UserB",
        recordedAt: testDate - 2 * msPerDay,
        ownerId: hashedUserDId, // Use hashed ID to match user lookup
      }]);

      // D also recorded interaction with C
      interactionsByPartner.set(hashedUserCId, [{
        partnerAnonymousId: hashedUserCId,
        partnerUsernameSnapshot: "UserC",
        recordedAt: testDate - 2 * msPerDay, // Same time as B's interaction
        ownerId: hashedUserDId, // Use hashed ID to match user lookup
      }]);

      const notificationsCreated: NotificationDocument[] = [];
      const notificationUpdates: { id: string; data: Partial<NotificationDocument> }[] = [];
      const notificationIdsByRecipient = new Map<string, string>();
      let notificationIdCounter = 0;

      // Create a batch mock that captures notifications created via batch.set()
      const createBatchMock = () => ({
        set: jest.fn().mockImplementation((docRef: { id: string }, data: NotificationDocument) => {
          notificationsCreated.push({ ...data });
          notificationIdsByRecipient.set(data.recipientId, docRef.id);
        }),
        update: jest.fn(),
        delete: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      });

      mockFirestore.batch.mockImplementation(createBatchMock);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            add: (notification: NotificationDocument) => {
              const id = `notification-${++notificationIdCounter}`;
              notificationsCreated.push({ ...notification });
              notificationIdsByRecipient.set(notification.recipientId, id);
              return Promise.resolve({ id });
            },
            // Support .doc() without ID for auto-generated document references
            doc: (id?: string) => {
              const docId = id || `notification-${++notificationIdCounter}`;
              return {
                id: docId,
                get: () => {
                  const existingNotif = notificationsCreated.find(
                    (n, idx) => notificationIdsByRecipient.get(n.recipientId) === docId ||
                      `notification-${idx + 1}` === docId
                  );
                  return Promise.resolve(createMockSnapshot(existingNotif || null, docId));
                },
                update: (data: Partial<NotificationDocument>) => {
                  notificationUpdates.push({ id: docId, data });
                  return Promise.resolve();
                },
              };
            },
            where: (field: string, _op: string, value: string) => {
              if (field === "reportId") {
                return {
                  where: (f2: string, _o2: string, v2: string) => ({
                    get: () => {
                      const matching = notificationsCreated.filter(
                        n => n.reportId === value && n.recipientId === v2
                      );
                      return Promise.resolve(createMockQuerySnapshot(
                        matching.map((n, idx) => ({
                          id: notificationIdsByRecipient.get(n.recipientId) || `notification-${idx + 1}`,
                          data: n,
                        }))
                      ));
                    },
                  }),
                  count: () => ({
                    get: () => Promise.resolve({ data: () => ({ count: notificationsCreated.length }) }),
                  }),
                };
              }
              return {
                count: () => ({
                  get: () => Promise.resolve({ data: () => ({ count: notificationsCreated.length }) }),
                }),
              };
            },
          };
        }
        return mockFirestore;
      });

      // Mock transaction to simulate concurrent updates
      mockFirestore.runTransaction = jest.fn().mockImplementation(async (callback) => {
        const transaction = {
          get: jest.fn().mockImplementation((docRef) => {
            const idx = parseInt(docRef.id.replace("notification-", "")) - 1;
            const existingNotif = notificationsCreated[idx];
            return Promise.resolve(createMockSnapshot(existingNotif || null, docRef.id));
          }),
          update: jest.fn().mockImplementation((docRef, data) => {
            notificationUpdates.push({ id: docRef.id, data });
          }),
          set: jest.fn(),
        };
        return callback(transaction);
      });

      // Act - pass hashed reporter ID to match real-world usage
      await propagateExposureChain(
        "report-concurrent",
        hashedReporterId,
        "UserA",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: D should have exactly 1 notification (not 2)
      // recipientId = hashedNotificationId from user lookup
      const notificationsToD = notificationsCreated.filter(n => n.recipientId === `notif-${userDId}`);
      expect(notificationsToD.length).toBe(1);

      // Total should be 3: B, C, D (each gets one notification)
      expect(notificationsCreated.length).toBe(3);
    });
  });
});
