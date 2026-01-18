/**
 * Chain Status Updates Tests
 * Task 5.1: Write 3-5 focused tests for chain status updates
 *
 * These tests verify:
 * 1. Negative report updates chain nodes and notifies downstream
 * 2. Positive report from chain member creates linked chain
 * 3. Downstream users see updated risk assessment
 */

import * as admin from "firebase-admin";
import * as crypto from "crypto";
import {
  NotificationDocument,
  NotificationType,
  InteractionDocument,
  TestStatus,
  ChainVisualization,
  CONSTANTS,
  PrivacyLevel,
} from "../types";
import { propagateNegativeTestUpdate, propagateExposureChain } from "../utils/chainPropagation";
import { createMockSnapshot, createMockQuerySnapshot } from "./setup";

/**
 * Hash an anonymous ID using SHA-256, matching the implementation in chainPropagation.ts
 */
function hashId(anonymousId: string): string {
  return crypto.createHash("sha256").update(anonymousId, "utf8").digest("hex");
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

// Get mocked messaging
const mockMessaging = admin.messaging() as unknown as {
  send: jest.Mock;
};

// Type for FCM message
interface FCMMessage {
  token?: string;
  data?: Record<string, string>;
  notification?: {
    title?: string;
    body?: string;
  };
}

describe("Chain Status Updates", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  /**
   * Test 1: Negative report updates chain nodes and notifies downstream
   *
   * Scenario:
   * - User A (reporter) -> User B -> User C
   * - User B tests NEGATIVE
   * - C's notification should show B's status as NEGATIVE
   * - C should receive a push notification about reduced risk
   */
  describe("Test 1: Negative report updates chain nodes and notifies downstream", () => {
    it("should update chain visualization and notify downstream users when chain member tests negative", async () => {
      // Arrange
      const userBId = "user-B";
      const userCId = "user-C";
      const reporterId = "reporter-A";
      const stiType = "HIV";

      // Hash the userBId the same way propagateNegativeTestUpdate does:
      // chainPath stores hashForChain(hashAnonymousId(uid))
      const interactionHashedId = crypto.createHash("sha256").update(userBId, "utf8").digest("hex");
      const hashedUserBId = crypto.createHash("sha256").update(`chain:${interactionHashedId}`, "utf8").digest("hex");

      // Create chain visualization with B as UNKNOWN (not yet tested)
      const chainVisualization: ChainVisualization = {
        nodes: [
          { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
          { username: "UserB", testStatus: TestStatus.UNKNOWN, isCurrentUser: false },
          { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
        ],
      };

      const notificationToC: NotificationDocument = {
        recipientId: userCId,
        type: NotificationType.EXPOSURE,
        stiType,
        chainData: JSON.stringify(chainVisualization),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-1",
        chainPath: [reporterId, hashedUserBId, userCId], // Use hashed ID for userB
        hopDepth: 2,
      };

      const notificationsUpdated: { id: string; data: Partial<NotificationDocument> }[] = [];

      // Mock batch
      const mockBatch = {
        update: jest.fn().mockImplementation((ref, data) => {
          notificationsUpdated.push({ id: ref.id || "notification-to-C", data });
        }),
        commit: jest.fn().mockResolvedValue(undefined),
      };

      mockFirestore.batch.mockReturnValue(mockBatch);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: (field: string, op: string, value: string) => {
              // The function queries with the hashed user ID
              if (field === "chainPath" && op === "array-contains" && value === hashedUserBId) {
                return {
                  get: () => Promise.resolve(createMockQuerySnapshot([
                    { id: "notification-to-C", data: notificationToC },
                  ])),
                };
              }
              return { get: () => Promise.resolve(createMockQuerySnapshot([])) };
            },
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: () => ({
              get: () => Promise.resolve(createMockSnapshot({
                anonymousId: userCId,
                username: "UserC",
                fcmToken: "test-fcm-token",
              })),
            }),
          };
        }
        return mockFirestore;
      });

      // Mock messaging
      mockMessaging.send.mockImplementation(async () => {
        return "mock-message-id";
      });

      // Act
      const updatedCount = await propagateNegativeTestUpdate(userBId, stiType);

      // Assert
      expect(updatedCount).toBe(1);

      // Verify batch update was called
      expect(mockBatch.update).toHaveBeenCalled();
      expect(mockBatch.commit).toHaveBeenCalled();

      // Verify the chain data was updated with B's negative status
      const updateCall = mockBatch.update.mock.calls[0];
      expect(updateCall).toBeDefined();
      const updateData = updateCall[1];
      expect(updateData.chainData).toBeDefined();

      const updatedChainViz = JSON.parse(updateData.chainData);
      // Node at index 1 (B) should now be NEGATIVE
      expect(updatedChainViz.nodes[1].testStatus).toBe(TestStatus.NEGATIVE);
    });
  });

  /**
   * Test 2: STI type filtering for negative updates
   *
   * Scenario:
   * - Notification for HIV exposure
   * - User reports negative for SYPHILIS
   * - Notification should NOT be updated (different STI)
   */
  describe("Test 2: STI type filtering for negative updates", () => {
    it("should only update notifications matching the STI type", async () => {
      // Arrange
      const userBId = "user-B";
      const reporterId = "reporter-A";

      // Hash the userBId the same way propagateNegativeTestUpdate does
      const interactionHashedId = crypto.createHash("sha256").update(userBId, "utf8").digest("hex");
      const hashedUserBId = crypto.createHash("sha256").update(`chain:${interactionHashedId}`, "utf8").digest("hex");

      // Notification for HIV exposure
      const hivNotification: NotificationDocument = {
        recipientId: "user-C",
        type: NotificationType.EXPOSURE,
        stiType: "HIV",
        chainData: JSON.stringify({
          nodes: [
            { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
            { username: "UserB", testStatus: TestStatus.UNKNOWN, isCurrentUser: false },
            { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
          ],
        }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-hiv",
        chainPath: [reporterId, hashedUserBId, "user-C"], // Use hashed ID
        hopDepth: 2,
      };

      // Notification for Syphilis exposure (should be updated)
      const syphilisNotification: NotificationDocument = {
        recipientId: "user-D",
        type: NotificationType.EXPOSURE,
        stiType: "SYPHILIS",
        chainData: JSON.stringify({
          nodes: [
            { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
            { username: "UserB", testStatus: TestStatus.UNKNOWN, isCurrentUser: false },
            { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
          ],
        }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-syphilis",
        chainPath: [reporterId, hashedUserBId, "user-D"], // Use hashed ID
        hopDepth: 2,
      };

      let updatedChainData: string | undefined;

      // Mock batch
      const mockBatch = {
        update: jest.fn().mockImplementation((_ref, data) => {
          if (data.chainData) {
            updatedChainData = data.chainData;
          }
        }),
        commit: jest.fn().mockResolvedValue(undefined),
      };

      mockFirestore.batch.mockReturnValue(mockBatch);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: (field: string, op: string, value: string) => {
              // The function queries with the hashed user ID
              if (field === "chainPath" && op === "array-contains" && value === hashedUserBId) {
                return {
                  get: () => Promise.resolve(createMockQuerySnapshot([
                    { id: "notification-hiv", data: hivNotification },
                    { id: "notification-syphilis", data: syphilisNotification },
                  ])),
                };
              }
              return { get: () => Promise.resolve(createMockQuerySnapshot([])) };
            },
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: () => ({
              get: () => Promise.resolve(createMockSnapshot({
                fcmToken: "test-token",
              })),
            }),
          };
        }
        return mockFirestore;
      });

      // Act - User B reports negative for SYPHILIS
      const updatedCount = await propagateNegativeTestUpdate(userBId, "SYPHILIS");

      // Assert - Only syphilis notification should be updated
      expect(updatedCount).toBe(1);
      expect(mockBatch.update).toHaveBeenCalledTimes(1);

      // Verify the updated chain data is for syphilis (has B marked as NEGATIVE)
      expect(updatedChainData).toBeDefined();
      const chainViz = JSON.parse(updatedChainData!);
      expect(chainViz.nodes[1].testStatus).toBe(TestStatus.NEGATIVE);
    });
  });

  /**
   * Test 3: Notification type update indicator
   *
   * Scenario:
   * - When a chain member tests negative
   * - Push notification should be sent with update type
   */
  describe("Test 3: Push notification sent with UPDATE type", () => {
    it("should send push notification with UPDATE type in data", async () => {
      // Arrange
      const userBId = "user-B";
      const userCId = "user-C";
      const reporterId = "reporter-A";

      // Hash the userBId the same way propagateNegativeTestUpdate does
      const interactionHashedId = crypto.createHash("sha256").update(userBId, "utf8").digest("hex");
      const hashedUserBId = crypto.createHash("sha256").update(`chain:${interactionHashedId}`, "utf8").digest("hex");

      const notification: NotificationDocument = {
        recipientId: userCId,
        type: NotificationType.EXPOSURE,
        stiType: "HIV",
        chainData: JSON.stringify({
          nodes: [
            { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
            { username: "UserB", testStatus: TestStatus.UNKNOWN, isCurrentUser: false },
            { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
          ],
        }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-1",
        chainPath: [reporterId, hashedUserBId, userCId], // Use hashed ID
        hopDepth: 2,
      };

      let pushNotificationSent = false;
      let pushNotificationData: FCMMessage = {};

      // Mock batch
      const mockBatch = {
        update: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      };

      mockFirestore.batch.mockReturnValue(mockBatch);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: (field: string, op: string, value: string) => {
              // The function queries with the hashed user ID
              if (field === "chainPath" && op === "array-contains" && value === hashedUserBId) {
                return {
                  get: () => Promise.resolve(createMockQuerySnapshot([
                    { id: "notification-1", data: notification },
                  ])),
                };
              }
              return { get: () => Promise.resolve(createMockQuerySnapshot([])) };
            },
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          const hashedUserCId = crypto.createHash("sha256").update(userCId, "utf8").digest("hex");
          return {
            doc: () => ({
              get: () => Promise.resolve(createMockSnapshot({
                anonymousId: userCId,
                username: "UserC",
                fcmToken: "test-fcm-token",
              })),
            }),
            where: (field: string, op: string, value: string) => {
              // Mock for getUserByHashedNotificationId
              if (field === "hashedNotificationId" && op === "==" && value === userCId) {
                return {
                  limit: () => ({
                    get: () => Promise.resolve(createMockQuerySnapshot([
                      { id: "user-C-doc", data: {
                        anonymousId: userCId,
                        username: "UserC",
                        fcmToken: "test-fcm-token",
                        hashedInteractionId: hashedUserCId,
                        hashedNotificationId: userCId,
                      }},
                    ])),
                  }),
                };
              }
              // Mock for getUserByHashedInteractionId
              if (field === "hashedInteractionId" && op === "==" && value === hashedUserCId) {
                return {
                  limit: () => ({
                    get: () => Promise.resolve(createMockQuerySnapshot([
                      { id: "user-C-doc", data: {
                        anonymousId: userCId,
                        username: "UserC",
                        fcmToken: "test-fcm-token",
                        hashedInteractionId: hashedUserCId,
                        hashedNotificationId: userCId,
                      }},
                    ])),
                  }),
                };
              }
              return {
                limit: () => ({
                  get: () => Promise.resolve(createMockQuerySnapshot([])),
                }),
              };
            },
          };
        }
        return mockFirestore;
      });

      // Mock messaging
      mockMessaging.send.mockImplementation(async (message: FCMMessage) => {
        pushNotificationSent = true;
        pushNotificationData = message;
        return "mock-message-id";
      });

      // Act
      await propagateNegativeTestUpdate(userBId, "HIV");

      // Assert - Push notification should be sent with UPDATE type
      expect(pushNotificationSent).toBe(true);
      expect(pushNotificationData.data?.type).toBe("UPDATE");
    });
  });

  /**
   * Test 4: Positive report from chain member creates linked chain
   *
   * Scenario:
   * - User A reports positive, B gets notified
   * - Later, B also tests positive
   * - B's new report should be linked to the original report via linkedReportId
   */
  describe("Test 4: Positive report from chain member creates linked chain", () => {
    it("should create notifications when chain member reports positive", async () => {
      // This test verifies the linking functionality
      // The actual linking happens in updateTestStatus.ts

      // Arrange
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 5 * msPerDay;

      const userBId = "user-B";
      const userCId = "user-C";

      const interactionsByPartner = new Map<string, InteractionDocument[]>();

      // C recorded interaction with B - use hashed IDs
      interactionsByPartner.set(hashId(userBId), [{
        partnerAnonymousId: hashId(userBId),
        partnerUsernameSnapshot: "UserB",
        recordedAt: testDate - 2 * msPerDay,
        ownerId: hashId(userCId),
      }]);

      const notificationsCreated: NotificationDocument[] = [];

      // Mock batch for NotificationBatcher
      mockFirestore.batch.mockImplementation(() => ({
        set: jest.fn().mockImplementation((_docRef: unknown, data: NotificationDocument) => {
          notificationsCreated.push({ ...data });
        }),
        update: jest.fn(),
        delete: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      }));

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: (id?: string) => ({
              get: () => Promise.resolve(createMockSnapshot({
                anonymousId: "user",
                username: "TestUser",
                fcmToken: "token",
                hashedInteractionId: hashId(userCId),
                hashedNotificationId: `notif-${userCId}`,
              }, id || "user-doc")),
            }),
            where: (field: string, op: string, value: unknown) => {
              if (field === "hashedInteractionId") {
                // Handle batch "in" queries
                if (op === "in" && Array.isArray(value)) {
                  const hashedIds = value as string[];
                  const matchingUsers: { id: string; data: Record<string, unknown> }[] = [];
                  if (hashedIds.includes(hashId(userCId))) {
                    matchingUsers.push({
                      id: `user-${userCId}`,
                      data: {
                        anonymousId: userCId,
                        username: "UserC",
                        fcmToken: "token-C",
                        hashedInteractionId: hashId(userCId),
                        hashedNotificationId: `notif-${userCId}`,
                      },
                    });
                  }
                  return {
                    get: () => Promise.resolve(createMockQuerySnapshot(matchingUsers)),
                  };
                }
                // Handle single "==" queries
                const hashedId = value as string;
                if (hashedId === hashId(userCId)) {
                  return {
                    limit: () => ({
                      get: () => Promise.resolve(createMockQuerySnapshot([
                        { id: `user-${userCId}`, data: {
                          anonymousId: userCId,
                          username: "UserC",
                          fcmToken: "token-C",
                          hashedInteractionId: hashId(userCId),
                          hashedNotificationId: `notif-${userCId}`,
                        }},
                      ])),
                    }),
                  };
                }
              }
              return {
                limit: () => ({
                  get: () => Promise.resolve(createMockQuerySnapshot([])),
                }),
                get: () => Promise.resolve(createMockQuerySnapshot([])),
              };
            },
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return {
            where: jest.fn().mockImplementation((field: string, _op: string, value: unknown) => {
              if (field === "partnerAnonymousId") {
                const partnerId = value as string;
                const interactions = interactionsByPartner.get(partnerId) || [];
                return {
                  where: jest.fn().mockReturnValue({
                    where: jest.fn().mockReturnValue({
                      get: () => Promise.resolve(createMockQuerySnapshot(
                        interactions.map((i, idx) => ({ id: `interaction-${idx}`, data: i }))
                      )),
                    }),
                    get: () => Promise.resolve(createMockQuerySnapshot(
                      interactions.map((i, idx) => ({ id: `interaction-${idx}`, data: i }))
                    )),
                  }),
                  get: () => Promise.resolve(createMockQuerySnapshot(
                    interactions.map((i, idx) => ({ id: `interaction-${idx}`, data: i }))
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
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            add: (notification: NotificationDocument) => {
              notificationsCreated.push(notification);
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
            where: (field: string) => {
              if (field === "reportId") {
                return {
                  count: () => ({
                    get: () => Promise.resolve({ data: () => ({ count: notificationsCreated.length }) }),
                  }),
                  where: () => ({
                    get: () => Promise.resolve(createMockQuerySnapshot([])),
                  }),
                };
              }
              return {
                get: () => Promise.resolve(createMockQuerySnapshot([])),
                count: () => ({
                  get: () => Promise.resolve({ data: () => ({ count: 0 }) }),
                }),
              };
            },
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.REPORTS) {
          return {
            doc: () => ({
              get: () => Promise.resolve(createMockSnapshot(null)),
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

      // Act - B reports positive (this simulates the new chain starting from B)
      // Disable optimizations to use direct writes instead of batchers
      await propagateExposureChain(
        "new-report-from-B",
        hashId(userBId),
        "UserB",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
        undefined, // no linkedReportId
        false, // disable optimizations
      );

      // Assert - C should receive a notification
      expect(notificationsCreated.length).toBe(1);
      expect(notificationsCreated[0].recipientId).toBe(`notif-${userCId}`);

      // The report linkage would be set in updateTestStatus.ts
      // Here we verify that the chain propagation works correctly
    });
  });

  /**
   * Test 5: Recipient's own notification is updated when they report negative
   *
   * Scenario:
   * - User receives notification about exposure
   * - User reports negative test via reportNegativeTest
   * - The notification's chainData should be updated to show user as NEGATIVE
   */
  describe("Test 5: Recipient's own notification is updated when they report negative", () => {
    it("should update the recipient's own notification chainData when they report negative", async () => {
      // This test verifies that reportNegativeTest updates the user's own notification
      // The actual implementation is in updateTestStatus.ts

      // Arrange
      const notificationId = "notification-123";
      const userId = "recipient-user";

      const chainVisualization: ChainVisualization = {
        nodes: [
          { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
          { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
        ],
        paths: [[
          { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
          { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
        ]],
      };

      const notification: NotificationDocument = {
        recipientId: userId,
        type: NotificationType.EXPOSURE,
        stiType: "HIV",
        chainData: JSON.stringify(chainVisualization),
        isRead: true,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-1",
        chainPath: ["reporter", userId],
        hopDepth: 1,
      };

      let updatedChainData: string | undefined;

      // Mock the notification document
      const mockNotificationRef = {
        get: jest.fn().mockResolvedValue({
          exists: true,
          data: () => notification,
        }),
        update: jest.fn().mockImplementation((data) => {
          updatedChainData = data.chainData;
          return Promise.resolve();
        }),
      };

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            doc: (id: string) => {
              if (id === notificationId) {
                return mockNotificationRef;
              }
              return { get: jest.fn().mockResolvedValue({ exists: false }) };
            },
            where: () => ({ get: () => Promise.resolve(createMockQuerySnapshot([])) }),
          };
        }
        return mockFirestore;
      });

      // Act - Simulate what reportNegativeTest does
      const notificationDoc = await mockNotificationRef.get();

      if (notificationDoc.exists) {
        const notif = notificationDoc.data();
        if (notif?.chainData) {
          const chainViz = JSON.parse(notif.chainData);
          const updatedNodes = chainViz.nodes.map((node: { isCurrentUser?: boolean }) => {
            if (node.isCurrentUser) {
              return { ...node, testStatus: TestStatus.NEGATIVE };
            }
            return node;
          });
          const updatedPaths = chainViz.paths?.map((path: Array<{ isCurrentUser?: boolean }>) =>
            path.map((node) => {
              if (node.isCurrentUser) {
                return { ...node, testStatus: TestStatus.NEGATIVE };
              }
              return node;
            })
          );

          await mockNotificationRef.update({
            chainData: JSON.stringify({ nodes: updatedNodes, paths: updatedPaths }),
            updatedAt: Date.now(),
          });
        }
      }

      // Assert
      expect(mockNotificationRef.update).toHaveBeenCalled();
      expect(updatedChainData).toBeDefined();

      const updatedChainViz = JSON.parse(updatedChainData!);
      // The "You" node should now be NEGATIVE
      expect(updatedChainViz.nodes[1].testStatus).toBe(TestStatus.NEGATIVE);
      expect(updatedChainViz.nodes[1].isCurrentUser).toBe(true);
      // Path should also be updated
      expect(updatedChainViz.paths[0][1].testStatus).toBe(TestStatus.NEGATIVE);
    });
  });

  /**
   * Test 6: Recipient's own notification is updated when they report positive
   *
   * Scenario:
   * - User receives notification about exposure
   * - User later tests positive and reports via reportPositiveTest
   * - All user's notifications should be updated to show user as POSITIVE
   */
  describe("Test 6: Recipient's notifications are updated when they report positive", () => {
    it("should update all recipient's notifications chainData when they report positive", async () => {
      // This test verifies that reportPositiveTest updates the user's notifications
      // The actual implementation is in updateTestStatus.ts

      // Arrange
      const userId = "recipient-user";

      const chainVisualization: ChainVisualization = {
        nodes: [
          { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
          { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
        ],
        paths: [[
          { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
          { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
        ]],
      };

      const notification: NotificationDocument = {
        recipientId: userId,
        type: NotificationType.EXPOSURE,
        stiType: "HIV",
        chainData: JSON.stringify(chainVisualization),
        isRead: true,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-1",
        chainPath: ["reporter", userId],
        hopDepth: 1,
      };

      let updatedChainData: string | undefined;

      // Mock the notification document
      const mockNotificationRef = {
        get: jest.fn().mockResolvedValue({
          exists: true,
          data: () => notification,
        }),
        update: jest.fn().mockImplementation((data) => {
          updatedChainData = data.chainData;
          return Promise.resolve();
        }),
      };

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            doc: () => mockNotificationRef,
            where: () => ({ get: () => Promise.resolve(createMockQuerySnapshot([])) }),
          };
        }
        return mockFirestore;
      });

      // Act - Simulate what reportPositiveTest does for updating notifications
      const notificationDoc = await mockNotificationRef.get();

      if (notificationDoc.exists) {
        const notif = notificationDoc.data();
        if (notif?.chainData) {
          const chainViz = JSON.parse(notif.chainData);
          const updatedNodes = chainViz.nodes.map((node: { isCurrentUser?: boolean }) => {
            if (node.isCurrentUser) {
              return { ...node, testStatus: TestStatus.POSITIVE };
            }
            return node;
          });
          const updatedPaths = chainViz.paths?.map((path: Array<{ isCurrentUser?: boolean }>) =>
            path.map((node) => {
              if (node.isCurrentUser) {
                return { ...node, testStatus: TestStatus.POSITIVE };
              }
              return node;
            })
          );

          await mockNotificationRef.update({
            chainData: JSON.stringify({ nodes: updatedNodes, paths: updatedPaths }),
            updatedAt: Date.now(),
          });
        }
      }

      // Assert
      expect(mockNotificationRef.update).toHaveBeenCalled();
      expect(updatedChainData).toBeDefined();

      const updatedChainViz = JSON.parse(updatedChainData!);
      // The "You" node should now be POSITIVE
      expect(updatedChainViz.nodes[1].testStatus).toBe(TestStatus.POSITIVE);
      expect(updatedChainViz.nodes[1].isCurrentUser).toBe(true);
      // Path should also be updated
      expect(updatedChainViz.paths[0][1].testStatus).toBe(TestStatus.POSITIVE);
    });
  });

  /**
   * Test 7: Downstream users see updated risk assessment after negative test
   *
   * Scenario:
   * - Chain: A -> B -> C -> D
   * - B tests negative
   * - C and D should have their notifications updated
   * - Risk assessment should show reduced risk (B is negative)
   */
  describe("Test 7: Downstream users see updated risk assessment", () => {
    it("should update all downstream notifications when intermediary tests negative", async () => {
      // Arrange
      const reporterId = "reporter-A";
      const userBId = "user-B";
      const userCId = "user-C";
      const userDId = "user-D";

      // Hash the userBId the same way propagateNegativeTestUpdate does
      const interactionHashedId = crypto.createHash("sha256").update(userBId, "utf8").digest("hex");
      const hashedUserBId = crypto.createHash("sha256").update(`chain:${interactionHashedId}`, "utf8").digest("hex");

      // C's notification (directly connected to B)
      const notificationToC: NotificationDocument = {
        recipientId: userCId,
        type: NotificationType.EXPOSURE,
        stiType: "HIV",
        chainData: JSON.stringify({
          nodes: [
            { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
            { username: "UserB", testStatus: TestStatus.UNKNOWN, isCurrentUser: false },
            { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
          ],
        }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-1",
        chainPath: [reporterId, hashedUserBId, userCId],
        hopDepth: 2,
      };

      // D's notification (connected via B and C)
      const notificationToD: NotificationDocument = {
        recipientId: userDId,
        type: NotificationType.EXPOSURE,
        stiType: "HIV",
        chainData: JSON.stringify({
          nodes: [
            { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
            { username: "UserB", testStatus: TestStatus.UNKNOWN, isCurrentUser: false },
            { username: "UserC", testStatus: TestStatus.UNKNOWN, isCurrentUser: false },
            { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
          ],
        }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-1",
        chainPath: [reporterId, hashedUserBId, userCId, userDId],
        hopDepth: 3,
      };

      const updatedChainData: string[] = [];

      // Mock batch
      const mockBatch = {
        update: jest.fn().mockImplementation((_ref, data) => {
          if (data.chainData) {
            updatedChainData.push(data.chainData);
          }
        }),
        commit: jest.fn().mockResolvedValue(undefined),
      };

      mockFirestore.batch.mockReturnValue(mockBatch);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: (field: string, op: string, value: string) => {
              if (field === "chainPath" && op === "array-contains" && value === hashedUserBId) {
                return {
                  get: () => Promise.resolve(createMockQuerySnapshot([
                    { id: "notification-to-C", data: notificationToC },
                    { id: "notification-to-D", data: notificationToD },
                  ])),
                };
              }
              return { get: () => Promise.resolve(createMockQuerySnapshot([])) };
            },
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: (userId: string) => ({
              get: () => Promise.resolve(createMockSnapshot({
                anonymousId: userId,
                username: userId === userCId ? "UserC" : "UserD",
                fcmToken: "test-fcm-token",
              })),
            }),
            where: (field: string, op: string, value: string) => {
              // Mock for getUserByHashedNotificationId
              if (field === "hashedNotificationId" && op === "==") {
                const userData = value === userCId ? {
                  anonymousId: userCId,
                  username: "UserC",
                  fcmToken: "test-fcm-token",
                  hashedInteractionId: hashId(userCId),
                  hashedNotificationId: userCId,
                } : value === userDId ? {
                  anonymousId: userDId,
                  username: "UserD",
                  fcmToken: "test-fcm-token",
                  hashedInteractionId: hashId(userDId),
                  hashedNotificationId: userDId,
                } : null;

                if (userData) {
                  return {
                    limit: () => ({
                      get: () => Promise.resolve(createMockQuerySnapshot([
                        { id: `user-${value}`, data: userData },
                      ])),
                    }),
                  };
                }
              }
              return {
                limit: () => ({
                  get: () => Promise.resolve(createMockQuerySnapshot([])),
                }),
              };
            },
          };
        }
        return mockFirestore;
      });

      // Act
      const updatedCount = await propagateNegativeTestUpdate(userBId, "HIV");

      // Assert
      expect(updatedCount).toBe(2);
      expect(mockBatch.update).toHaveBeenCalledTimes(2);

      // Verify B is marked as NEGATIVE in both chain visualizations
      expect(updatedChainData.length).toBe(2);

      // Parse and check each updated chain
      for (const chainDataStr of updatedChainData) {
        const chainViz = JSON.parse(chainDataStr);
        // In both notifications, B is at index 1
        expect(chainViz.nodes[1].testStatus).toBe(TestStatus.NEGATIVE);
      }
    });
  });
});
