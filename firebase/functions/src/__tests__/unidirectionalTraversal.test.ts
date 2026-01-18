/**
 * Unidirectional Graph Traversal Tests
 * Task 2.1: Write 4-6 focused tests for unidirectional traversal
 *
 * These tests verify that the chain propagation uses ONLY unidirectional traversal,
 * meaning contacts are discovered only via `partnerAnonymousId == userId` queries.
 * This is a critical security feature that prevents false reporting.
 *
 * Security Model:
 * - A user can only be notified if THEY recorded the interaction
 * - User A cannot falsely claim interaction with User B
 * - Only B can prove they interacted with A (by having a record where B is owner and A is partner)
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
 * Track which query types were used during a test
 */
interface QueryTracker {
  partnerAnonymousIdQueries: string[];  // Track queries using partnerAnonymousId
  ownerIdQueries: string[];              // Track queries using ownerId
}

/**
 * Helper to create a mock user query that supports:
 * - .doc().get() pattern
 * - .where(field, "==", value).limit().get() pattern
 * - .where(field, "in", [...values]).get() pattern (for batch lookups)
 */
function createUserMock(usersByHashedInteractionId: Map<string, MockUserData>) {
  return {
    doc: (userId: string) => ({
      get: () => {
        // Find user by document ID if it matches hashedInteractionId
        const userData = usersByHashedInteractionId.get(userId);
        return Promise.resolve(createMockSnapshot(userData || null, userId));
      },
    }),
    where: jest.fn().mockImplementation((field: string, op: string, value: unknown) => {
      if (field === "hashedInteractionId") {
        // Handle batch "in" queries for getUsersByHashedInteractionIds
        if (op === "in" && Array.isArray(value)) {
          const hashedIds = value as string[];
          const matchedUsers = hashedIds
            .map(hashedId => {
              const userData = usersByHashedInteractionId.get(hashedId);
              return userData ? { id: `user-${hashedId.substring(0, 8)}`, data: userData } : null;
            })
            .filter((u): u is { id: string; data: MockUserData } => u !== null);
          return {
            get: () => Promise.resolve(createMockQuerySnapshot(matchedUsers)),
          };
        }
        // Handle single "==" queries for getUserByHashedInteractionId
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
        // Handle batch "in" queries for getUsersByHashedNotificationIds
        if (op === "in" && Array.isArray(value)) {
          const hashedNotifIds = value as string[];
          const matchedUsers: { id: string; data: MockUserData }[] = [];
          for (const hashedNotifId of hashedNotifIds) {
            for (const userData of usersByHashedInteractionId.values()) {
              if (userData.hashedNotificationId === hashedNotifId) {
                matchedUsers.push({ id: `user-${hashedNotifId.substring(0, 8)}`, data: userData });
                break;
              }
            }
          }
          return {
            get: () => Promise.resolve(createMockQuerySnapshot(matchedUsers)),
          };
        }
        // Handle single "==" queries
        const hashedNotifId = value as string;
        // Find user by hashedNotificationId
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

/**
 * Helper to create a mock interaction query chain that properly handles date window queries
 */
function createInteractionMock(
  interactionsByPartner: Map<string, InteractionDocument[]>,
  queryTracker?: QueryTracker,
) {
  return {
    where: jest.fn().mockImplementation((field: string, _op: string, value: unknown) => {
      // Track the query type
      if (queryTracker) {
        if (field === "partnerAnonymousId") {
          queryTracker.partnerAnonymousIdQueries.push(value as string);
        } else if (field === "ownerId") {
          queryTracker.ownerIdQueries.push(value as string);
        }
      }

      // For partnerAnonymousId queries, return matching interactions
      if (field === "partnerAnonymousId") {
        const partnerId = value as string;
        const interactions = interactionsByPartner.get(partnerId) || [];
        return {
          where: jest.fn().mockReturnThis(),
          get: () => Promise.resolve(createMockQuerySnapshot(
            interactions.map((i, idx) => ({ id: `interaction-${idx}`, data: i }))
          )),
        };
      }

      // For date window queries (recordedAt), just pass through
      return {
        where: jest.fn().mockReturnThis(),
        get: () => Promise.resolve(createMockQuerySnapshot([])),
      };
    }),
  };
}

describe("Unidirectional Graph Traversal", () => {
  let queryTracker: QueryTracker;

  beforeEach(() => {
    jest.clearAllMocks();
    queryTracker = {
      partnerAnonymousIdQueries: [],
      ownerIdQueries: [],
    };
  });

  /**
   * Test 1: Verify only partnerAnonymousId queries are used for contact discovery
   *
   * This is the core test for the unidirectional security model.
   * When propagating exposure chains, we should ONLY query where partnerAnonymousId == userId,
   * which finds users who recorded interacting WITH the current user.
   */
  describe("Test 1: Only partnerAnonymousId queries are used for contact discovery", () => {
    it("should only use partnerAnonymousId queries to find contacts", async () => {
      // Arrange
      const reporterId = "reporter-user";
      const testDate = Date.now() - 7 * 24 * 60 * 60 * 1000;
      const contactId = "contact-who-recorded";

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId(reporterId);
      const hashedContactId = hashId(contactId);

      // Set up user data with proper hash fields
      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedContactId, {
        hashedInteractionId: hashedContactId,
        hashedNotificationId: `notif-${contactId}`,
        username: "Contact",
        fcmToken: "token",
      });

      // Contact recorded interaction with reporter - ownerId uses hashed ID
      const contactInteraction: InteractionDocument = {
        partnerAnonymousId: hashedReporterId,
        partnerUsernameSnapshot: "Reporter",
        recordedAt: testDate - 2 * 24 * 60 * 60 * 1000,
        ownerId: hashedContactId, // Use hashed ID to match user lookup
      };

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashedReporterId, [contactInteraction]);

      const notificationsCreated: NotificationDocument[] = [];

      // Mock batch for NotificationBatcher
      let pendingBatchNotifications: NotificationDocument[] = [];
      mockFirestore.batch.mockImplementation(() => ({
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
      }));

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner, queryTracker);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push({ ...notification });
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `notification-${notificationsCreated.length + 1}`;
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
              limit: jest.fn().mockReturnValue({
                get: () => Promise.resolve(createMockQuerySnapshot([])),
              }),
              get: () => Promise.resolve(createMockQuerySnapshot([])),
            }),
          };
        }
        return mockFirestore;
      });

      // Act - pass hashed reporter ID
      await propagateExposureChain(
        "report-1",
        hashedReporterId,
        "Reporter",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: partnerAnonymousId queries should have been made
      expect(queryTracker.partnerAnonymousIdQueries.length).toBeGreaterThan(0);
      expect(queryTracker.partnerAnonymousIdQueries).toContain(hashedReporterId);

      // Assert: ownerId queries should NOT be used for contact discovery
      expect(queryTracker.ownerIdQueries.length).toBe(0);
    });
  });

  /**
   * Test 2: Verify ownerId queries are NOT used for contact discovery
   *
   * The old bidirectional approach used both:
   * - partnerAnonymousId == userId (find who recorded you as partner)
   * - ownerId == userId (find who you recorded)
   *
   * The new unidirectional approach should ONLY use partnerAnonymousId queries.
   */
  describe("Test 2: ownerId queries are NOT used for contact discovery", () => {
    it("should NOT query ownerId to discover contacts", async () => {
      // Arrange
      const reporterId = "reporter-for-owner-test";
      const testDate = Date.now() - 5 * 24 * 60 * 60 * 1000;

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId(reporterId);

      // Empty interactions - no one recorded the reporter
      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      const usersByHashedInteractionId = new Map<string, MockUserData>();

      const notificationsCreated: NotificationDocument[] = [];

      // Mock batch for NotificationBatcher
      let pendingBatchNotifications: NotificationDocument[] = [];
      mockFirestore.batch.mockImplementation(() => ({
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
      }));

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner, queryTracker);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push({ ...notification });
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `notification-${notificationsCreated.length + 1}`;
              return {
                id: docId,
                get: () => Promise.resolve(createMockSnapshot(null, docId)),
                set: jest.fn().mockResolvedValue(undefined),
                update: jest.fn().mockResolvedValue(undefined),
              };
            },
            where: () => ({
              count: () => ({
                get: () => Promise.resolve({ data: () => ({ count: 0 }) }),
              }),
              limit: jest.fn().mockReturnValue({
                get: () => Promise.resolve(createMockQuerySnapshot([])),
              }),
              get: () => Promise.resolve(createMockQuerySnapshot([])),
            }),
          };
        }
        return mockFirestore;
      });

      // Act - pass hashed reporter ID
      await propagateExposureChain(
        "report-owner-test",
        hashedReporterId,
        "Reporter",
        "[\"SYPHILIS\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: No ownerId queries should have been made
      expect(queryTracker.ownerIdQueries.length).toBe(0);
    });
  });

  /**
   * Test 3: Basic chain - A reports, B recorded interaction with A, B gets notified
   *
   * This test verifies the fundamental unidirectional flow:
   * - User A reports positive
   * - User B previously recorded that they interacted with A
   * - User B gets notified because THEY have a record of the interaction
   */
  describe("Test 3: Basic unidirectional chain notification", () => {
    it("should notify B when B recorded interaction with reporter A", async () => {
      // Arrange
      const reporterId = "user-A-reporter";
      const contactBId = "user-B-contact";
      const testDate = Date.now() - 7 * 24 * 60 * 60 * 1000;
      const interactionDate = testDate - 3 * 24 * 60 * 60 * 1000;

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId(reporterId);
      const hashedContactBId = hashId(contactBId);

      // Set up user data with proper hash fields
      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedContactBId, {
        hashedInteractionId: hashedContactBId,
        hashedNotificationId: `notif-${contactBId}`,
        username: "UserB",
        fcmToken: "token-B",
      });

      // B recorded an interaction with A (A is the partner in B's record) - ownerId uses hashed ID
      const bRecordedInteractionWithA: InteractionDocument = {
        partnerAnonymousId: hashedReporterId, // A is B's partner (hashed)
        partnerUsernameSnapshot: "UserA",
        recordedAt: interactionDate,
        ownerId: hashedContactBId, // B owns this record - use hashed ID
      };

      const notificationsCreated: NotificationDocument[] = [];

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashedReporterId, [bRecordedInteractionWithA]);

      // Mock batch for NotificationBatcher
      let pendingBatchNotifications: NotificationDocument[] = [];
      mockFirestore.batch.mockImplementation(() => ({
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
      }));

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
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `notification-${notificationsCreated.length + 1}`;
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
              limit: jest.fn().mockReturnValue({
                get: () => Promise.resolve(createMockQuerySnapshot([])),
              }),
              get: () => Promise.resolve(createMockQuerySnapshot([])),
            }),
          };
        }
        return mockFirestore;
      });

      // Act - pass hashed reporter ID
      await propagateExposureChain(
        "report-basic-chain",
        hashedReporterId,
        "UserA",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: B should receive a notification (recipientId = hashedNotificationId)
      expect(notificationsCreated.length).toBe(1);
      expect(notificationsCreated[0].recipientId).toBe(`notif-${contactBId}`);
    });
  });

  /**
   * Test 4: False reporting prevention - A cannot claim interaction with B if B didn't record it
   *
   * This is the key security test. In the old bidirectional model, if A claims to have
   * interacted with B, B would get notified. In the new unidirectional model, B only
   * gets notified if B has a record of the interaction.
   *
   * Scenario:
   * - A reports positive
   * - A claims they interacted with B (A recorded B as partner)
   * - BUT B never recorded any interaction with A
   * - B should NOT be notified (prevents malicious false reporting)
   */
  describe("Test 4: False reporting prevention", () => {
    it("should NOT notify B if A claims interaction but B has no record", async () => {
      // Arrange
      const reporterId = "malicious-reporter-A";
      const victimBId = "innocent-user-B";
      const testDate = Date.now() - 7 * 24 * 60 * 60 * 1000;

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId(reporterId);

      // Empty - B never recorded any interaction with A
      // In the old bidirectional model, A's claim would have been enough
      // But in unidirectional model, we only query partnerAnonymousId == A
      // which would not find B (since B didn't record A)
      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      // No interactions recorded where A is the partner
      const usersByHashedInteractionId = new Map<string, MockUserData>();

      const notificationsCreated: NotificationDocument[] = [];

      // Mock batch for NotificationBatcher
      let pendingBatchNotifications: NotificationDocument[] = [];
      mockFirestore.batch.mockImplementation(() => ({
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
      }));

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
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `notification-${notificationsCreated.length + 1}`;
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
              limit: jest.fn().mockReturnValue({
                get: () => Promise.resolve(createMockQuerySnapshot([])),
              }),
              get: () => Promise.resolve(createMockQuerySnapshot([])),
            }),
          };
        }
        return mockFirestore;
      });

      // Act - pass hashed reporter ID
      await propagateExposureChain(
        "report-false-claim",
        hashedReporterId,
        "MaliciousA",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: B should NOT receive any notification
      expect(notificationsCreated.length).toBe(0);

      // Verify no notification was sent to the innocent user B
      const notificationToB = notificationsCreated.find(n => n.recipientId === `notif-${victimBId}`);
      expect(notificationToB).toBeUndefined();
    });
  });

  /**
   * Test 5: Backend auto-discovers contacts without client-provided contactIds
   *
   * In the new model, the client no longer sends contactedIds.
   * The backend should automatically discover contacts by querying
   * partnerAnonymousId == reporterId.
   */
  describe("Test 5: Backend auto-discovers contacts", () => {
    it("should discover contacts without client providing contactIds", async () => {
      // Arrange
      const reporterId = "auto-discover-reporter";
      const discoveredContactId = "auto-discovered-contact";
      const testDate = Date.now() - 7 * 24 * 60 * 60 * 1000;

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId(reporterId);
      const hashedContactId = hashId(discoveredContactId);

      // Set up user data with proper hash fields
      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedContactId, {
        hashedInteractionId: hashedContactId,
        hashedNotificationId: `notif-${discoveredContactId}`,
        username: "DiscoveredContact",
        fcmToken: "token",
      });

      // Contact recorded interaction with reporter - ownerId uses hashed ID
      const contactInteraction: InteractionDocument = {
        partnerAnonymousId: hashedReporterId,
        partnerUsernameSnapshot: "Reporter",
        recordedAt: testDate - 2 * 24 * 60 * 60 * 1000,
        ownerId: hashedContactId, // Use hashed ID to match user lookup
      };

      const notificationsCreated: NotificationDocument[] = [];

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashedReporterId, [contactInteraction]);

      // Mock batch for NotificationBatcher
      let pendingBatchNotifications: NotificationDocument[] = [];
      mockFirestore.batch.mockImplementation(() => ({
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
      }));

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
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `notification-${notificationsCreated.length + 1}`;
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
              limit: jest.fn().mockReturnValue({
                get: () => Promise.resolve(createMockQuerySnapshot([])),
              }),
              get: () => Promise.resolve(createMockQuerySnapshot([])),
            }),
          };
        }
        return mockFirestore;
      });

      // Act: Call WITHOUT providing contactIds (the new API) - pass hashed reporter ID
      await propagateExposureChain(
        "report-auto-discover",
        hashedReporterId,
        "Reporter",
        "[\"CHLAMYDIA\"]",
        testDate,
        PrivacyLevel.FULL,
        // No contactIds parameter - backend should auto-discover
      );

      // Assert: Contact should be discovered and notified (recipientId = hashedNotificationId)
      expect(notificationsCreated.length).toBe(1);
      expect(notificationsCreated[0].recipientId).toBe(`notif-${discoveredContactId}`);
    });
  });

  /**
   * Test 6: Chain propagation continues unidirectionally
   *
   * When propagating beyond the first hop, the same unidirectional rule applies:
   * - A reports -> B is discovered (B recorded A)
   * - B's contacts are discovered by finding who recorded B
   * - C is notified because C recorded B (not because B recorded C)
   */
  describe("Test 6: Chain propagation maintains unidirectional rule", () => {
    it("should maintain unidirectional discovery through multiple hops", async () => {
      // Arrange
      const reporterAId = "chain-reporter-A";
      const contactBId = "chain-contact-B";
      const contactCId = "chain-contact-C";
      const testDate = Date.now() - 10 * 24 * 60 * 60 * 1000;
      const interactionDateAB = testDate - 5 * 24 * 60 * 60 * 1000;
      const interactionDateBC = testDate - 3 * 24 * 60 * 60 * 1000;

      // Hash IDs for consistent lookup
      const hashedReporterAId = hashId(reporterAId);
      const hashedContactBId = hashId(contactBId);
      const hashedContactCId = hashId(contactCId);

      // Set up user data with proper hash fields
      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedContactBId, {
        hashedInteractionId: hashedContactBId,
        hashedNotificationId: `notif-${contactBId}`,
        username: "UserB",
        fcmToken: "token-B",
      });
      usersByHashedInteractionId.set(hashedContactCId, {
        hashedInteractionId: hashedContactCId,
        hashedNotificationId: `notif-${contactCId}`,
        username: "UserC",
        fcmToken: "token-C",
      });

      // B recorded interaction with A - ownerId uses hashed ID
      const bRecordedA: InteractionDocument = {
        partnerAnonymousId: hashedReporterAId,
        partnerUsernameSnapshot: "UserA",
        recordedAt: interactionDateAB,
        ownerId: hashedContactBId, // Use hashed ID to match user lookup
      };

      // C recorded interaction with B - ownerId uses hashed ID
      const cRecordedB: InteractionDocument = {
        partnerAnonymousId: hashedContactBId,
        partnerUsernameSnapshot: "UserB",
        recordedAt: interactionDateBC,
        ownerId: hashedContactCId, // Use hashed ID to match user lookup
      };

      const notificationsCreated: NotificationDocument[] = [];

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashedReporterAId, [bRecordedA]);
      interactionsByPartner.set(hashedContactBId, [cRecordedB]);

      // Mock batch for NotificationBatcher
      let pendingBatchNotifications: NotificationDocument[] = [];
      mockFirestore.batch.mockImplementation(() => ({
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
      }));

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
              return Promise.resolve({ id: `notification-${notificationsCreated.length}` });
            },
            doc: (id?: string) => {
              const docId = id || `notification-${notificationsCreated.length + 1}`;
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
              limit: jest.fn().mockReturnValue({
                get: () => Promise.resolve(createMockQuerySnapshot([])),
              }),
              get: () => Promise.resolve(createMockQuerySnapshot([])),
            }),
          };
        }
        return mockFirestore;
      });

      // Act - pass hashed reporter ID
      await propagateExposureChain(
        "report-chain-test",
        hashedReporterAId,
        "UserA",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: Both B and C should be notified (recipientId = hashedNotificationId)
      expect(notificationsCreated.length).toBe(2);

      const notificationToB = notificationsCreated.find(n => n.recipientId === `notif-${contactBId}`);
      const notificationToC = notificationsCreated.find(n => n.recipientId === `notif-${contactCId}`);

      expect(notificationToB).toBeDefined();
      expect(notificationToC).toBeDefined();
    });
  });
});
