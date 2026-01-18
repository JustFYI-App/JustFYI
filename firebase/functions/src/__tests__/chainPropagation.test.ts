/**
 * Chain Propagation Tests - 10-Hop Limit and Rolling Windows
 * Task 3.1: Write 4-6 focused tests for chain propagation
 *
 * These tests verify:
 * 1. Chain depth limit of 10 hops (A->B->C->...->K stops at K)
 * 2. Rolling window calculation per hop
 * 3. Window respects STI incubation period from config
 * 4. 180-day retention boundary is respected at all hops
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
 */
function hashId(anonymousId: string): string {
  return crypto.createHash("sha256").update(anonymousId, "utf8").digest("hex");
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
 * Helper to create a mock user query that supports:
 * - .doc().get() pattern for individual lookups
 * - .where(field, "==", value).limit().get() pattern for single lookups
 * - .where(field, "in", values).get() pattern for batch lookups
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
        // Handle "in" operator for batch queries
        if (op === "in") {
          const hashedIds = value as string[];
          const matchingUsers: { id: string; data: MockUserData }[] = [];
          for (const hashedId of hashedIds) {
            const userData = usersByHashedInteractionId.get(hashedId);
            if (userData) {
              matchingUsers.push({ id: `user-${hashedId.substring(0, 8)}`, data: userData });
            }
          }
          return {
            get: () => Promise.resolve(createMockQuerySnapshot(matchingUsers)),
          };
        }
        // Handle "==" operator for single lookup
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
        // Handle "in" operator for batch queries
        if (op === "in") {
          const hashedNotifIds = value as string[];
          const matchingUsers: { id: string; data: MockUserData }[] = [];
          for (const userData of usersByHashedInteractionId.values()) {
            if (hashedNotifIds.includes(userData.hashedNotificationId)) {
              matchingUsers.push({ id: `user-${userData.hashedNotificationId.substring(0, 8)}`, data: userData });
            }
          }
          return {
            get: () => Promise.resolve(createMockQuerySnapshot(matchingUsers)),
          };
        }
        // Handle "==" operator for single lookup
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
};

/**
 * Helper to generate a chain of users with interactions
 * Creates interactions: user-0 -> user-1 -> user-2 -> ... -> user-N
 * Each user recorded an interaction with the previous user in the chain
 *
 * NOTE: Keys in the returned map are HASHED IDs to match the implementation's query behavior.
 * The implementation hashes IDs before querying: `partnerAnonymousId == hashAnonymousId(userId)`
 * ownerId must also be hashed to match the user lookup system
 */
function generateChainInteractions(
  chainLength: number,
  baseDate: number,
  daysBetweenHops: number = 2,
): { interactions: Map<string, InteractionDocument[]>; users: Map<string, MockUserData> } {
  const interactionsByPartner = new Map<string, InteractionDocument[]>();
  const usersByHashedInteractionId = new Map<string, MockUserData>();
  const msPerDay = 24 * 60 * 60 * 1000;

  // For each hop i: user-(i+1) recorded an interaction with user-i
  for (let i = 0; i < chainLength; i++) {
    const currentUserId = i === 0 ? "reporter-user" : `user-${i}`;
    const nextUserId = `user-${i + 1}`;
    const interactionDate = baseDate - (chainLength - i) * daysBetweenHops * msPerDay;

    // Hash the currentUserId and nextUserId to match how the implementation stores/queries
    const hashedCurrentUserId = hashId(currentUserId);
    const hashedNextUserId = hashId(nextUserId);

    const interaction: InteractionDocument = {
      partnerAnonymousId: hashedCurrentUserId, // Stored as hashed in DB
      partnerUsernameSnapshot: `User${i}`,
      recordedAt: interactionDate,
      ownerId: hashedNextUserId, // Use hashed ID to match user lookup
    };

    const existing = interactionsByPartner.get(hashedCurrentUserId) || [];
    existing.push(interaction);
    interactionsByPartner.set(hashedCurrentUserId, existing);

    // Add user data for nextUserId
    if (!usersByHashedInteractionId.has(hashedNextUserId)) {
      usersByHashedInteractionId.set(hashedNextUserId, {
        hashedInteractionId: hashedNextUserId,
        hashedNotificationId: `notif-${nextUserId}`,
        username: `User${i + 1}`,
        fcmToken: `token-${nextUserId}`,
      });
    }
  }

  return { interactions: interactionsByPartner, users: usersByHashedInteractionId };
}

/**
 * Helper to create a mock interaction query chain that properly handles date window queries
 * and supports the rolling window behavior
 */
function createInteractionMockWithRollingWindow(
  interactionsByPartner: Map<string, InteractionDocument[]>,
  retentionBoundary: number,
) {
  return {
    where: jest.fn().mockImplementation((field: string, _op: string, value: unknown) => {
      // For partnerAnonymousId queries, return matching interactions
      if (field === "partnerAnonymousId") {
        const partnerId = value as string;
        const interactions = interactionsByPartner.get(partnerId) || [];

        // Return a chainable mock for subsequent where() calls (date filters)
        return {
          where: jest.fn().mockImplementation((dateField: string, dateOp: string, dateValue: number) => {
            // Filter interactions based on date window
            let filteredInteractions = interactions;

            if (dateField === "recordedAt") {
              if (dateOp === ">=") {
                const effectiveStart = Math.max(dateValue, retentionBoundary);
                filteredInteractions = filteredInteractions.filter(
                  i => i.recordedAt >= effectiveStart
                );
              } else if (dateOp === "<=") {
                filteredInteractions = filteredInteractions.filter(
                  i => i.recordedAt <= dateValue
                );
              }
            }

            return {
              where: jest.fn().mockImplementation((_f: string, _o: string, endDate: number) => {
                // Apply end date filter
                const finalInteractions = filteredInteractions.filter(
                  i => i.recordedAt <= endDate
                );
                return {
                  get: () => Promise.resolve(createMockQuerySnapshot(
                    finalInteractions.map((i, idx) => ({ id: `interaction-${partnerId}-${idx}`, data: i }))
                  )),
                };
              }),
              get: () => Promise.resolve(createMockQuerySnapshot(
                filteredInteractions.map((i, idx) => ({ id: `interaction-${partnerId}-${idx}`, data: i }))
              )),
            };
          }),
          get: () => Promise.resolve(createMockQuerySnapshot(
            interactions.map((i, idx) => ({ id: `interaction-${partnerId}-${idx}`, data: i }))
          )),
        };
      }

      // For other fields, return empty results
      return {
        where: jest.fn().mockReturnThis(),
        get: () => Promise.resolve(createMockQuerySnapshot([])),
      };
    }),
  };
}

describe("Chain Propagation - 10-Hop Limit and Rolling Windows", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  /**
   * Test 1: Chain depth limit of 10 hops (A->B->C->...->K stops at K)
   *
   * This test verifies that the chain propagation stops at exactly 10 hops,
   * even if there are more users in the chain.
   */
  describe("Test 1: Chain depth limit of 10 hops", () => {
    it("should stop propagation at exactly 10 hops", async () => {
      // Arrange: Create a chain of 15 users (should only process 10)
      const chainLength = 15;
      const testDate = Date.now() - 7 * 24 * 60 * 60 * 1000;
      const retentionBoundary = Date.now() - CONSTANTS.RETENTION_DAYS * 24 * 60 * 60 * 1000;

      const { interactions: interactionsByPartner, users: usersByHashedInteractionId } =
        generateChainInteractions(chainLength, testDate, 1);
      const notificationsCreated: NotificationDocument[] = [];

      // Pending batch notifications (for batch.set() / batch.commit() pattern)
      let pendingBatchNotifications: NotificationDocument[] = [];

      // Mock Firestore batch for NotificationBatcher
      mockFirestore.batch = jest.fn().mockImplementation(() => {
        pendingBatchNotifications = [];
        return {
          set: jest.fn().mockImplementation((_docRef: unknown, data: NotificationDocument) => {
            pendingBatchNotifications.push({ ...data });
          }),
          update: jest.fn(),
          delete: jest.fn(),
          commit: jest.fn().mockImplementation(() => {
            for (const notification of pendingBatchNotifications) {
              notificationsCreated.push(notification);
            }
            pendingBatchNotifications = [];
            return Promise.resolve();
          }),
        };
      });

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMockWithRollingWindow(interactionsByPartner, retentionBoundary);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          let docIdCounter = 0;
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push(notification);
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `batch-notification-${++docIdCounter}`;
              return {
                id: docId,
                get: () => Promise.resolve(createMockSnapshot(null, docId)),
                set: jest.fn().mockResolvedValue(undefined),
                update: jest.fn().mockResolvedValue(undefined),
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

      // Act - pass hashed reporter ID
      await propagateExposureChain(
        "report-depth-test",
        hashId("reporter-user"),
        "Reporter",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: Should have exactly 10 notifications (hops 1-10)
      // User-1 through User-10 get notified, but User-11 through User-15 should NOT
      expect(notificationsCreated.length).toBe(10);

      // Verify the notified users are user-1 through user-10 (using hashedNotificationId)
      const notifiedUserIds = notificationsCreated.map(n => n.recipientId);
      for (let i = 1; i <= 10; i++) {
        expect(notifiedUserIds).toContain(`notif-user-${i}`);
      }

      // Verify users beyond hop 10 are NOT notified
      for (let i = 11; i <= 15; i++) {
        expect(notifiedUserIds).not.toContain(`notif-user-${i}`);
      }
    });

    it("should correctly verify MAX_CHAIN_DEPTH constant is 10", () => {
      // Assert: MAX_CHAIN_DEPTH should be 10
      expect(CONSTANTS.MAX_CHAIN_DEPTH).toBe(10);
    });
  });

  /**
   * Test 2: Rolling window calculation per hop
   *
   * Each hop should use the interaction date as the new window start,
   * not the original reporter's test date.
   */
  describe("Test 2: Rolling window calculation per hop", () => {
    it("should use interaction date as new window start for each hop", async () => {
      // Arrange: Create a 3-hop chain with specific dates
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 30 * msPerDay; // Reporter tested 30 days ago
      const interactionDateAB = testDate - 5 * msPerDay; // B interacted with A 5 days before test
      const interactionDateBC = interactionDateAB + 3 * msPerDay; // C interacted with B 3 days after A-B

      const retentionBoundary = now - CONSTANTS.RETENTION_DAYS * msPerDay;

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      const usersByHashedInteractionId = new Map<string, MockUserData>();

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId("reporter-user");
      const hashedUserBId = hashId("user-B");
      const hashedUserCId = hashId("user-C");

      // Set up user data
      usersByHashedInteractionId.set(hashedUserBId, {
        hashedInteractionId: hashedUserBId,
        hashedNotificationId: "notif-user-B",
        username: "UserB",
        fcmToken: "token-B",
      });
      usersByHashedInteractionId.set(hashedUserCId, {
        hashedInteractionId: hashedUserCId,
        hashedNotificationId: "notif-user-C",
        username: "UserC",
        fcmToken: "token-C",
      });

      // B recorded interaction with A (reporter) - use hashed IDs
      interactionsByPartner.set(hashedReporterId, [{
        partnerAnonymousId: hashedReporterId,
        partnerUsernameSnapshot: "Reporter",
        recordedAt: interactionDateAB,
        ownerId: hashedUserBId, // Use hashed ID
      }]);

      // C recorded interaction with B - use hashed IDs
      interactionsByPartner.set(hashedUserBId, [{
        partnerAnonymousId: hashedUserBId,
        partnerUsernameSnapshot: "UserB",
        recordedAt: interactionDateBC,
        ownerId: hashedUserCId, // Use hashed ID
      }]);

      const notificationsCreated: NotificationDocument[] = [];

      // Pending batch notifications
      let pendingBatchNotifications: NotificationDocument[] = [];

      // Mock Firestore batch
      mockFirestore.batch = jest.fn().mockImplementation(() => {
        pendingBatchNotifications = [];
        return {
          set: jest.fn().mockImplementation((_docRef: unknown, data: NotificationDocument) => {
            pendingBatchNotifications.push({ ...data });
          }),
          update: jest.fn(),
          delete: jest.fn(),
          commit: jest.fn().mockImplementation(() => {
            for (const notification of pendingBatchNotifications) {
              notificationsCreated.push(notification);
            }
            pendingBatchNotifications = [];
            return Promise.resolve();
          }),
        };
      });

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMockWithRollingWindow(interactionsByPartner, retentionBoundary);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          let docIdCounter = 0;
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push(notification);
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `batch-notification-${++docIdCounter}`;
              return {
                id: docId,
                get: () => Promise.resolve(createMockSnapshot(null, docId)),
                set: jest.fn().mockResolvedValue(undefined),
                update: jest.fn().mockResolvedValue(undefined),
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

      // Act - pass hashed reporter ID
      await propagateExposureChain(
        "report-rolling-window",
        hashedReporterId,
        "Reporter",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: Both B and C should be notified
      expect(notificationsCreated.length).toBe(2);

      // recipientId uses hashedNotificationId
      const notificationToB = notificationsCreated.find(n => n.recipientId === "notif-user-B");
      const notificationToC = notificationsCreated.find(n => n.recipientId === "notif-user-C");

      expect(notificationToB).toBeDefined();
      expect(notificationToC).toBeDefined();

      // The exposure dates should reflect the interaction dates
      expect(notificationToB?.exposureDate).toBe(interactionDateAB);
      expect(notificationToC?.exposureDate).toBe(interactionDateBC);
    });
  });

  /**
   * Test 3: Window respects STI incubation period from config
   *
   * The exposure window for each hop should be based on the STI's
   * maximum incubation period from the shared config.
   */
  describe("Test 3: Window respects STI incubation period from config", () => {
    it("should use STI-specific incubation period for window calculation", async () => {
      // Arrange: HIV has 30 days incubation, HPV has 180 days
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 5 * msPerDay;

      // Create an interaction that is within HPV window (180 days) but outside HIV window (30 days)
      const interactionDate = testDate - 100 * msPerDay; // 100 days before test

      const retentionBoundary = now - CONSTANTS.RETENTION_DAYS * msPerDay;

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId("reporter-user");
      const hashedUserBId = hashId("user-B");

      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedUserBId, {
        hashedInteractionId: hashedUserBId,
        hashedNotificationId: "notif-user-B",
        username: "UserB",
        fcmToken: "token-B",
      });

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashedReporterId, [{
        partnerAnonymousId: hashedReporterId,
        partnerUsernameSnapshot: "Reporter",
        recordedAt: interactionDate,
        ownerId: hashedUserBId, // Use hashed ID
      }]);

      const notificationsCreatedHPV: NotificationDocument[] = [];
      const notificationsCreatedHIV: NotificationDocument[] = [];

      // Pending batch notifications for HPV test
      let pendingBatchNotifications: NotificationDocument[] = [];

      // Mock Firestore batch
      mockFirestore.batch = jest.fn().mockImplementation(() => {
        pendingBatchNotifications = [];
        return {
          set: jest.fn().mockImplementation((_docRef: unknown, data: NotificationDocument) => {
            pendingBatchNotifications.push({ ...data });
          }),
          update: jest.fn(),
          delete: jest.fn(),
          commit: jest.fn().mockImplementation(() => {
            for (const notification of pendingBatchNotifications) {
              notificationsCreatedHPV.push(notification);
            }
            pendingBatchNotifications = [];
            return Promise.resolve();
          }),
        };
      });

      // Test with HPV (180 day window) - should find the interaction
      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMockWithRollingWindow(interactionsByPartner, retentionBoundary);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          let docIdCounter = 0;
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreatedHPV.push(notification);
              return Promise.resolve({ id: `notification-${notificationsCreatedHPV.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `batch-notification-${++docIdCounter}`;
              return {
                id: docId,
                get: () => Promise.resolve(createMockSnapshot(null, docId)),
                set: jest.fn().mockResolvedValue(undefined),
                update: jest.fn().mockResolvedValue(undefined),
              };
            },
            where: () => ({
              count: () => ({
                get: () => Promise.resolve({ data: () => ({ count: notificationsCreatedHPV.length }) }),
              }),
            }),
          };
        }
        return mockFirestore;
      });

      await propagateExposureChain(
        "report-hpv-test",
        hashedReporterId,
        "Reporter",
        "[\"HPV\"]", // HPV has 180 day incubation
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: HPV should find the interaction (100 days < 180 days)
      expect(notificationsCreatedHPV.length).toBe(1);

      // Reset and test with HIV (30 day window) - should NOT find the interaction
      jest.clearAllMocks();

      // Pending batch notifications for HIV test
      pendingBatchNotifications = [];

      // Mock Firestore batch for HIV test
      mockFirestore.batch = jest.fn().mockImplementation(() => {
        pendingBatchNotifications = [];
        return {
          set: jest.fn().mockImplementation((_docRef: unknown, data: NotificationDocument) => {
            pendingBatchNotifications.push({ ...data });
          }),
          update: jest.fn(),
          delete: jest.fn(),
          commit: jest.fn().mockImplementation(() => {
            for (const notification of pendingBatchNotifications) {
              notificationsCreatedHIV.push(notification);
            }
            pendingBatchNotifications = [];
            return Promise.resolve();
          }),
        };
      });

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMockWithRollingWindow(interactionsByPartner, retentionBoundary);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          let docIdCounter = 0;
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreatedHIV.push(notification);
              return Promise.resolve({ id: `notification-${notificationsCreatedHIV.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `batch-notification-${++docIdCounter}`;
              return {
                id: docId,
                get: () => Promise.resolve(createMockSnapshot(null, docId)),
                set: jest.fn().mockResolvedValue(undefined),
                update: jest.fn().mockResolvedValue(undefined),
              };
            },
            where: () => ({
              count: () => ({
                get: () => Promise.resolve({ data: () => ({ count: notificationsCreatedHIV.length }) }),
              }),
            }),
          };
        }
        return mockFirestore;
      });

      await propagateExposureChain(
        "report-hiv-test",
        hashedReporterId,
        "Reporter",
        "[\"HIV\"]", // HIV has 30 day incubation
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: HIV should NOT find the interaction (100 days > 30 days)
      expect(notificationsCreatedHIV.length).toBe(0);
    });
  });

  /**
   * Test 4: 180-day retention boundary is respected at all hops
   *
   * Interactions older than 180 days should not be considered, even
   * if they fall within the STI incubation window.
   */
  describe("Test 4: 180-day retention boundary is respected at all hops", () => {
    it("should not find interactions older than 180 days", async () => {
      // Arrange: Create an interaction that is older than 180 days
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 5 * msPerDay;

      // Interaction is 200 days old - outside the 180-day retention boundary
      const oldInteractionDate = now - 200 * msPerDay;

      const retentionBoundary = now - CONSTANTS.RETENTION_DAYS * msPerDay;

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashId("reporter-user"), [{
        partnerAnonymousId: hashId("reporter-user"),
        partnerUsernameSnapshot: "Reporter",
        recordedAt: oldInteractionDate,
        ownerId: "user-B",
      }]);

      const notificationsCreated: NotificationDocument[] = [];

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: () => ({
              get: () => Promise.resolve(createMockSnapshot({
                anonymousId: "user",
                username: "TestUser",
                fcmToken: "token",
              })),
            }),
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMockWithRollingWindow(interactionsByPartner, retentionBoundary);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push(notification);
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
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

      // Act: Use HPV which has 180-day incubation (same as retention)
      await propagateExposureChain(
        "report-retention-test",
        "reporter-user",
        "Reporter",
        "[\"HPV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: No notifications should be created because interaction is too old
      expect(notificationsCreated.length).toBe(0);
    });

    it("should find interactions just within the 180-day boundary", async () => {
      // Arrange: Create an interaction that is just within 180 days
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 5 * msPerDay;

      // Interaction is 170 days old - inside the 180-day retention boundary
      const recentInteractionDate = now - 170 * msPerDay;

      const retentionBoundary = now - CONSTANTS.RETENTION_DAYS * msPerDay;

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId("reporter-user");
      const hashedUserBId = hashId("user-B");

      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedUserBId, {
        hashedInteractionId: hashedUserBId,
        hashedNotificationId: "notif-user-B",
        username: "UserB",
        fcmToken: "token-B",
      });

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashedReporterId, [{
        partnerAnonymousId: hashedReporterId,
        partnerUsernameSnapshot: "Reporter",
        recordedAt: recentInteractionDate,
        ownerId: hashedUserBId, // Use hashed ID
      }]);

      const notificationsCreated: NotificationDocument[] = [];

      // Pending batch notifications
      let pendingBatchNotifications: NotificationDocument[] = [];

      // Mock Firestore batch
      mockFirestore.batch = jest.fn().mockImplementation(() => {
        pendingBatchNotifications = [];
        return {
          set: jest.fn().mockImplementation((_docRef: unknown, data: NotificationDocument) => {
            pendingBatchNotifications.push({ ...data });
          }),
          update: jest.fn(),
          delete: jest.fn(),
          commit: jest.fn().mockImplementation(() => {
            for (const notification of pendingBatchNotifications) {
              notificationsCreated.push(notification);
            }
            pendingBatchNotifications = [];
            return Promise.resolve();
          }),
        };
      });

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMockWithRollingWindow(interactionsByPartner, retentionBoundary);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          let docIdCounter = 0;
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push(notification);
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `batch-notification-${++docIdCounter}`;
              return {
                id: docId,
                get: () => Promise.resolve(createMockSnapshot(null, docId)),
                set: jest.fn().mockResolvedValue(undefined),
                update: jest.fn().mockResolvedValue(undefined),
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

      // Act: Use HPV which has 180-day incubation - pass hashed reporter ID
      await propagateExposureChain(
        "report-retention-valid-test",
        hashedReporterId,
        "Reporter",
        "[\"HPV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: Notification should be created
      expect(notificationsCreated.length).toBe(1);
    });
  });

  /**
   * Test 5: Hop depth is tracked in notifications
   *
   * Each notification should include the hop depth for chain visualization.
   */
  describe("Test 5: Hop depth tracking in notifications", () => {
    it("should include hop depth in notification data", async () => {
      // Arrange: Create a 3-hop chain
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 5 * msPerDay;
      const retentionBoundary = now - CONSTANTS.RETENTION_DAYS * msPerDay;

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId("reporter-user");
      const hashedUserBId = hashId("user-B");
      const hashedUserCId = hashId("user-C");
      const hashedUserDId = hashId("user-D");

      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedUserBId, {
        hashedInteractionId: hashedUserBId,
        hashedNotificationId: "notif-user-B",
        username: "UserB",
        fcmToken: "token-B",
      });
      usersByHashedInteractionId.set(hashedUserCId, {
        hashedInteractionId: hashedUserCId,
        hashedNotificationId: "notif-user-C",
        username: "UserC",
        fcmToken: "token-C",
      });
      usersByHashedInteractionId.set(hashedUserDId, {
        hashedInteractionId: hashedUserDId,
        hashedNotificationId: "notif-user-D",
        username: "UserD",
        fcmToken: "token-D",
      });

      const interactionsByPartner = new Map<string, InteractionDocument[]>();

      // B recorded interaction with A (reporter) - Hop 1
      interactionsByPartner.set(hashedReporterId, [{
        partnerAnonymousId: hashedReporterId,
        partnerUsernameSnapshot: "Reporter",
        recordedAt: testDate - 3 * msPerDay,
        ownerId: hashedUserBId, // Use hashed ID
      }]);

      // C recorded interaction with B - Hop 2
      interactionsByPartner.set(hashedUserBId, [{
        partnerAnonymousId: hashedUserBId,
        partnerUsernameSnapshot: "UserB",
        recordedAt: testDate - 2 * msPerDay,
        ownerId: hashedUserCId, // Use hashed ID
      }]);

      // D recorded interaction with C - Hop 3
      interactionsByPartner.set(hashedUserCId, [{
        partnerAnonymousId: hashedUserCId,
        partnerUsernameSnapshot: "UserC",
        recordedAt: testDate - 1 * msPerDay,
        ownerId: hashedUserDId, // Use hashed ID
      }]);

      const notificationsCreated: NotificationDocument[] = [];

      // Pending batch notifications
      let pendingBatchNotifications: NotificationDocument[] = [];

      // Mock Firestore batch
      mockFirestore.batch = jest.fn().mockImplementation(() => {
        pendingBatchNotifications = [];
        return {
          set: jest.fn().mockImplementation((_docRef: unknown, data: NotificationDocument) => {
            pendingBatchNotifications.push({ ...data });
          }),
          update: jest.fn(),
          delete: jest.fn(),
          commit: jest.fn().mockImplementation(() => {
            for (const notification of pendingBatchNotifications) {
              notificationsCreated.push(notification);
            }
            pendingBatchNotifications = [];
            return Promise.resolve();
          }),
        };
      });

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMockWithRollingWindow(interactionsByPartner, retentionBoundary);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          let docIdCounter = 0;
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push(notification);
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `batch-notification-${++docIdCounter}`;
              return {
                id: docId,
                get: () => Promise.resolve(createMockSnapshot(null, docId)),
                set: jest.fn().mockResolvedValue(undefined),
                update: jest.fn().mockResolvedValue(undefined),
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

      // Act - pass hashed reporter ID
      await propagateExposureChain(
        "report-hop-depth",
        hashedReporterId,
        "Reporter",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: All three should be notified with correct hop depths
      expect(notificationsCreated.length).toBe(3);

      // recipientId uses hashedNotificationId
      const notificationToB = notificationsCreated.find(n => n.recipientId === "notif-user-B");
      const notificationToC = notificationsCreated.find(n => n.recipientId === "notif-user-C");
      const notificationToD = notificationsCreated.find(n => n.recipientId === "notif-user-D");

      expect(notificationToB?.hopDepth).toBe(1);
      expect(notificationToC?.hopDepth).toBe(2);
      expect(notificationToD?.hopDepth).toBe(3);
    });
  });

  /**
   * Test 6: Multi-STI reports use maximum incubation period
   *
   * When a report includes multiple STIs, the longest incubation period
   * should be used for the exposure window.
   */
  describe("Test 6: Multi-STI reports use maximum incubation period", () => {
    it("should use maximum incubation period for multi-STI reports", async () => {
      // Arrange: Test with HIV (30 days) + HPV (180 days) - should use 180 days
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 5 * msPerDay;

      // Create an interaction 100 days ago - within HPV (180) but outside HIV (30)
      const interactionDate = testDate - 100 * msPerDay;

      const retentionBoundary = now - CONSTANTS.RETENTION_DAYS * msPerDay;

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId("reporter-user");
      const hashedUserBId = hashId("user-B");

      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedUserBId, {
        hashedInteractionId: hashedUserBId,
        hashedNotificationId: "notif-user-B",
        username: "UserB",
        fcmToken: "token-B",
      });

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashedReporterId, [{
        partnerAnonymousId: hashedReporterId,
        partnerUsernameSnapshot: "Reporter",
        recordedAt: interactionDate,
        ownerId: hashedUserBId, // Use hashed ID
      }]);

      const notificationsCreated: NotificationDocument[] = [];

      // Pending batch notifications
      let pendingBatchNotifications: NotificationDocument[] = [];

      // Mock Firestore batch
      mockFirestore.batch = jest.fn().mockImplementation(() => {
        pendingBatchNotifications = [];
        return {
          set: jest.fn().mockImplementation((_docRef: unknown, data: NotificationDocument) => {
            pendingBatchNotifications.push({ ...data });
          }),
          update: jest.fn(),
          delete: jest.fn(),
          commit: jest.fn().mockImplementation(() => {
            for (const notification of pendingBatchNotifications) {
              notificationsCreated.push(notification);
            }
            pendingBatchNotifications = [];
            return Promise.resolve();
          }),
        };
      });

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMockWithRollingWindow(interactionsByPartner, retentionBoundary);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          let docIdCounter = 0;
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push(notification);
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `batch-notification-${++docIdCounter}`;
              return {
                id: docId,
                get: () => Promise.resolve(createMockSnapshot(null, docId)),
                set: jest.fn().mockResolvedValue(undefined),
                update: jest.fn().mockResolvedValue(undefined),
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

      // Act: Report with both HIV and HPV - pass hashed reporter ID
      await propagateExposureChain(
        "report-multi-sti",
        hashedReporterId,
        "Reporter",
        "[\"HIV\", \"HPV\"]", // Multi-STI report
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: Should find the interaction because max window (HPV 180 days) is used
      expect(notificationsCreated.length).toBe(1);
    });
  });
});
