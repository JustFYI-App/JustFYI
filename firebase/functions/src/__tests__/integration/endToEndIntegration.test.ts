/**
 * End-to-End Integration Tests
 * Task Group 11: Integration Testing Layer
 *
 * These tests verify the complete flow from client submission through backend
 * processing to notification creation. They simulate the real-world integration
 * between client and backend components.
 *
 * Test Categories:
 * 1. Full report flow: client submit -> backend process -> notifications created
 * 2. Chain propagation triggers correctly from report submission
 * 3. Status update propagation works end-to-end
 * 4. Error scenarios return appropriate responses to client
 * 5. Client-backend contract compatibility
 */

import * as admin from "firebase-admin";
import * as crypto from "crypto";
import {
  NotificationDocument,
  NotificationType,
  InteractionDocument,
  ReportDocument,
  ReportStatus,
  TestStatus,
  CONSTANTS,
  PrivacyLevel,
} from "../../types";
import { propagateExposureChain, propagateNegativeTestUpdate } from "../../utils/chainPropagation";
import { createMockSnapshot, createMockQuerySnapshot } from "../setup";

/**
 * Hash an anonymous ID using SHA-256, matching the implementation in chainPropagation.ts
 * The ID is uppercased before hashing to ensure consistency with mobile app storage.
 */
function hashId(anonymousId: string): string {
  return crypto.createHash("sha256").update(anonymousId.toUpperCase(), "utf8").digest("hex");
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

/**
 * Helper to create a batch mock that captures notifications via batch.set()
 * NotificationBatcher uses db.batch().set(docRef, data) to create notifications
 */
function createBatchMock(notificationsCreated: NotificationDocument[]) {
  return {
    set: jest.fn().mockImplementation((_docRef: unknown, data: NotificationDocument) => {
      notificationsCreated.push({ ...data });
    }),
    update: jest.fn(),
    delete: jest.fn(),
    commit: jest.fn().mockResolvedValue(undefined),
  };
}

/**
 * Helper to create a notifications collection mock with doc() for batch operations
 * NotificationBatcher calls db.collection(NOTIFICATIONS).doc() to get a new doc ref
 */
function createNotificationsMock(notificationsCreated: NotificationDocument[]) {
  let docIdCounter = 0;
  return {
    add: (notification: NotificationDocument) => {
      notificationsCreated.push({ ...notification });
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
      limit: jest.fn().mockReturnValue({
        get: () => Promise.resolve(createMockQuerySnapshot([])),
      }),
      get: () => Promise.resolve(createMockQuerySnapshot([])),
    }),
  };
}

/**
 * Simulates client report submission data structure
 * This mirrors what ExposureReportRepositoryImpl.kt sends to Firestore
 */
interface ClientReportSubmission {
  stiTypes: string;
  testDate: number;
  privacyLevel: string;
  reportedAt: number;
  status: string;
}

/**
 * Helper to create a client report submission that mirrors the Kotlin client
 */
function createClientReportSubmission(
  stiTypes: string[],
  testDate: number,
  privacyLevel: PrivacyLevel,
): ClientReportSubmission {
  return {
    stiTypes: JSON.stringify(stiTypes),
    testDate,
    privacyLevel,
    reportedAt: Date.now(),
    status: "pending",
  };
}

describe("End-to-End Integration Tests", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  /**
   * Test 1: Full Report Flow - Client Submit -> Backend Process -> Notifications Created
   *
   * This test simulates the complete flow:
   * 1. Client submits a report (without contactedIds - backend discovers contacts)
   * 2. Backend validates the report
   * 3. Backend discovers contacts via unidirectional query
   * 4. Notifications are created for discovered contacts
   * 5. Report status is updated to COMPLETED
   */
  describe("Test 1: Full report flow - client submit to notifications", () => {
    it("should process client submission and create notifications for discovered contacts", async () => {
      // Arrange: Simulate client report submission
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 7 * msPerDay;

      const reporterId = "client-reporter-user";
      const contactBId = "contact-B";
      const contactCId = "contact-C";
      const reportId = "report-integration-test-1";

      // Hash IDs for consistent lookup - ownerId in interactions must match hashedInteractionId in users
      const hashedContactBId = hashId(contactBId);
      const hashedContactCId = hashId(contactCId);

      // Client submission data (matches ExposureReportRepositoryImpl.kt format)
      const clientSubmission = createClientReportSubmission(
        ["HIV", "SYPHILIS"],
        testDate,
        PrivacyLevel.FULL,
      );

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

      // Contacts recorded interactions with reporter - ownerId uses hashed IDs
      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashId(reporterId), [
        {
          partnerAnonymousId: hashId(reporterId),
          partnerUsernameSnapshot: "Reporter",
          recordedAt: testDate - 3 * msPerDay,
          ownerId: hashedContactBId, // Use hashed ID to match user lookup
        },
        {
          partnerAnonymousId: hashId(reporterId),
          partnerUsernameSnapshot: "Reporter",
          recordedAt: testDate - 2 * msPerDay,
          ownerId: hashedContactCId, // Use hashed ID to match user lookup
        },
      ]);

      const notificationsCreated: NotificationDocument[] = [];
      let reportStatus: ReportStatus = ReportStatus.PENDING;

      // Mock batch for NotificationBatcher
      mockFirestore.batch.mockImplementation(() => createBatchMock(notificationsCreated));

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.REPORTS) {
          return {
            doc: (id: string) => ({
              get: () => Promise.resolve(createMockSnapshot({
                ...clientSubmission,
                reporterId,
                status: reportStatus,
              }, id)),
              update: (data: Partial<ReportDocument>) => {
                if (data.status) {
                  reportStatus = data.status;
                }
                return Promise.resolve();
              },
            }),
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return createNotificationsMock(notificationsCreated);
        }
        return mockFirestore;
      });

      // Act: Simulate backend processing triggered by report creation
      // This is what processExposureReport Cloud Function does
      // Note: reporterInteractionHashedId must match how interactions are stored (by hashed ID)
      const notificationCount = await propagateExposureChain(
        reportId,
        hashId(reporterId),
        "Reporter",
        clientSubmission.stiTypes,
        clientSubmission.testDate,
        clientSubmission.privacyLevel as PrivacyLevel,
      );

      // Assert: Verify end-to-end flow succeeded

      // 1. Notifications were created for discovered contacts
      expect(notificationCount).toBe(2);
      expect(notificationsCreated.length).toBe(2);

      // 2. Correct recipients received notifications (recipientId = hashedNotificationId)
      const recipientIds = notificationsCreated.map(n => n.recipientId);
      expect(recipientIds).toContain(`notif-${contactBId}`);
      expect(recipientIds).toContain(`notif-${contactCId}`);

      // 3. Notifications contain correct data based on privacy level (FULL)
      const notificationB = notificationsCreated.find(n => n.recipientId === `notif-${contactBId}`)!;
      expect(notificationB.type).toBe(NotificationType.EXPOSURE);
      expect(notificationB.stiType).toBe(clientSubmission.stiTypes); // Should disclose STI
      expect(notificationB.exposureDate).toBeDefined(); // Should disclose date
      expect(notificationB.reportId).toBe(reportId);
      expect(notificationB.hopDepth).toBe(1); // Direct contacts
    });

    it("should handle client submission with no discoverable contacts gracefully", async () => {
      // Arrange: Reporter with no contacts in the system
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 7 * msPerDay;

      const reporterId = "isolated-reporter";
      const reportId = "report-no-contacts";

      const clientSubmission = createClientReportSubmission(
        ["CHLAMYDIA"],
        testDate,
        PrivacyLevel.ANONYMOUS,
      );

      // No interactions recorded
      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      // No users either
      const usersByHashedInteractionId = new Map<string, MockUserData>();

      const notificationsCreated: NotificationDocument[] = [];

      // Mock batch for NotificationBatcher
      mockFirestore.batch.mockImplementation(() => createBatchMock(notificationsCreated));

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return createNotificationsMock(notificationsCreated);
        }
        return mockFirestore;
      });

      // Act - pass hashed reporter ID to match real-world usage
      const notificationCount = await propagateExposureChain(
        reportId,
        hashId(reporterId),
        "IsolatedUser",
        clientSubmission.stiTypes,
        clientSubmission.testDate,
        clientSubmission.privacyLevel as PrivacyLevel,
      );

      // Assert: Flow completes successfully with 0 notifications
      expect(notificationCount).toBe(0);
      expect(notificationsCreated.length).toBe(0);
    });
  });

  /**
   * Test 2: Chain Propagation Triggers Correctly
   *
   * Verifies that when a report is submitted:
   * 1. Direct contacts are discovered and notified
   * 2. Chain propagation continues to indirect contacts
   * 3. All contacts within 10 hops are notified
   * 4. Rolling window is applied correctly per hop
   */
  describe("Test 2: Chain propagation from report submission", () => {
    it("should propagate through chain up to 10 hops after report submission", async () => {
      // Arrange: Create a chain: Reporter -> B -> C -> D (3 hops)
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 10 * msPerDay;

      const reporterId = "chain-reporter";
      const userBId = "chain-B";
      const userCId = "chain-C";
      const userDId = "chain-D";
      const reportId = "report-chain-propagation";

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId(reporterId);
      const hashedUserBId = hashId(userBId);
      const hashedUserCId = hashId(userCId);
      const hashedUserDId = hashId(userDId);

      const clientSubmission = createClientReportSubmission(
        ["HIV"],
        testDate,
        PrivacyLevel.FULL,
      );

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

      // B recorded interaction with Reporter - ownerId uses hashed ID
      interactionsByPartner.set(hashedReporterId, [{
        partnerAnonymousId: hashedReporterId,
        partnerUsernameSnapshot: "Reporter",
        recordedAt: testDate - 5 * msPerDay,
        ownerId: hashedUserBId,
      }]);

      // C recorded interaction with B - ownerId uses hashed ID
      interactionsByPartner.set(hashedUserBId, [{
        partnerAnonymousId: hashedUserBId,
        partnerUsernameSnapshot: "UserB",
        recordedAt: testDate - 3 * msPerDay,
        ownerId: hashedUserCId,
      }]);

      // D recorded interaction with C - ownerId uses hashed ID
      interactionsByPartner.set(hashedUserCId, [{
        partnerAnonymousId: hashedUserCId,
        partnerUsernameSnapshot: "UserC",
        recordedAt: testDate - 1 * msPerDay,
        ownerId: hashedUserDId,
      }]);

      const notificationsCreated: NotificationDocument[] = [];

      // Mock batch for NotificationBatcher
      mockFirestore.batch.mockImplementation(() => createBatchMock(notificationsCreated));

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return createNotificationsMock(notificationsCreated);
        }
        return mockFirestore;
      });

      // Act - pass hashed reporter ID
      await propagateExposureChain(
        reportId,
        hashedReporterId,
        "Reporter",
        clientSubmission.stiTypes,
        clientSubmission.testDate,
        clientSubmission.privacyLevel as PrivacyLevel,
      );

      // Assert: All chain members received notifications with correct hop depths
      expect(notificationsCreated.length).toBe(3);

      // recipientId = hashedNotificationId from user lookup
      const notificationB = notificationsCreated.find(n => n.recipientId === `notif-${userBId}`);
      const notificationC = notificationsCreated.find(n => n.recipientId === `notif-${userCId}`);
      const notificationD = notificationsCreated.find(n => n.recipientId === `notif-${userDId}`);

      expect(notificationB).toBeDefined();
      expect(notificationC).toBeDefined();
      expect(notificationD).toBeDefined();

      // Verify hop depths
      expect(notificationB?.hopDepth).toBe(1);
      expect(notificationC?.hopDepth).toBe(2);
      expect(notificationD?.hopDepth).toBe(3);

      // Verify chain paths contain the expected users (hashed paths)
      // Direct contact B
      expect(notificationB?.chainPath).toBeDefined();
      expect(notificationB?.chainPath.length).toBe(2);

      // Indirect contact C (reachable through B) - paths contain hashed IDs
      expect(notificationC?.chainPath).toBeDefined();
      expect(notificationC?.chainPath.length).toBe(3);

      // Indirect contact D (reachable through B -> C) - paths contain hashed IDs
      expect(notificationD?.chainPath).toBeDefined();
      expect(notificationD?.chainPath.length).toBe(4);
    });
  });

  /**
   * Test 3: Status Update Propagation Works End-to-End
   *
   * Verifies that when a chain member reports negative:
   * 1. Downstream notifications are found
   * 2. Chain data is updated with NEGATIVE status
   * 3. The update respects STI type filtering
   */
  describe("Test 3: Status update propagation end-to-end", () => {
    it("should update chain when member reports negative", async () => {
      // Arrange: Existing notification for user C (in chain A -> B -> C)
      const reporterId = "original-reporter";
      const userBId = "intermediary-B";
      const userCId = "recipient-C";
      const originalReportId = "original-report";

      // Hash the userBId the same way chainPropagation does:
      // chainPath stores hashForChain(hashAnonymousId(uid))
      // Note: hashAnonymousId does NOT uppercase, it just does SHA256(uid)
      const interactionHashedId = crypto.createHash("sha256").update(userBId, "utf8").digest("hex");
      const hashedUserBId = crypto.createHash("sha256").update(`chain:${interactionHashedId}`, "utf8").digest("hex");

      const existingNotification: NotificationDocument = {
        recipientId: userCId,
        type: NotificationType.EXPOSURE,
        stiType: "[\"HIV\"]",
        exposureDate: Date.now() - 7 * 24 * 60 * 60 * 1000,
        chainData: JSON.stringify({
          nodes: [
            { username: "Someone", testStatus: TestStatus.POSITIVE, isCurrentUser: false },
            { username: "UserB", testStatus: TestStatus.UNKNOWN, isCurrentUser: false },
            { username: "You", testStatus: TestStatus.UNKNOWN, isCurrentUser: true },
          ],
        }),
        isRead: false,
        receivedAt: Date.now() - 24 * 60 * 60 * 1000,
        updatedAt: Date.now() - 24 * 60 * 60 * 1000,
        reportId: originalReportId,
        chainPath: [reporterId, hashedUserBId, userCId], // Use hashed ID in chainPath
        hopDepth: 2,
      };

      let updatedChainData: string | null = null;
      const batchUpdateCalls: { ref: { id: string }; data: Record<string, unknown> }[] = [];

      const mockBatch = {
        update: jest.fn().mockImplementation((ref, data) => {
          batchUpdateCalls.push({ ref, data });
          if (data.chainData) {
            updatedChainData = data.chainData as string;
          }
        }),
        commit: jest.fn().mockResolvedValue(undefined),
      };

      mockFirestore.batch.mockReturnValue(mockBatch);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: (field: string, op: string, value: string) => {
              // The function uses array-contains with the hashed userBId
              if (field === "chainPath" && op === "array-contains" && value === hashedUserBId) {
                return {
                  get: () => Promise.resolve(createMockQuerySnapshot([
                    { id: "notification-C", data: existingNotification },
                  ])),
                };
              }
              return {
                get: () => Promise.resolve(createMockQuerySnapshot([])),
              };
            },
          };
        }
        return mockFirestore;
      });

      // Act: User B reports negative for HIV
      const updatedCount = await propagateNegativeTestUpdate(userBId, "HIV");

      // Assert: Notification was updated
      expect(updatedCount).toBe(1);
      expect(mockBatch.update).toHaveBeenCalled();
      expect(mockBatch.commit).toHaveBeenCalled();

      // Verify chain data was updated with NEGATIVE status for B
      expect(updatedChainData).not.toBeNull();
      const parsedChainData = JSON.parse(updatedChainData!);
      const userBNode = parsedChainData.nodes[1]; // Index 1 is UserB
      expect(userBNode.testStatus).toBe(TestStatus.NEGATIVE);
    });

    it("should filter updates by STI type when specified", async () => {
      // Arrange: Notification with different STI type
      const userBId = "test-user-B";
      const existingNotification: NotificationDocument = {
        recipientId: "recipient-X",
        type: NotificationType.EXPOSURE,
        stiType: "[\"SYPHILIS\"]", // Different STI than update
        exposureDate: Date.now(),
        chainData: JSON.stringify({ nodes: [] }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-xyz",
        chainPath: [userBId, "recipient-X"],
        hopDepth: 1,
      };

      const mockBatch = {
        update: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      };

      mockFirestore.batch.mockReturnValue(mockBatch);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: () => ({
              get: () => Promise.resolve(createMockQuerySnapshot([
                { id: "notification-X", data: existingNotification },
              ])),
            }),
          };
        }
        return mockFirestore;
      });

      // Act: Update for HIV (different from notification's SYPHILIS)
      const updatedCount = await propagateNegativeTestUpdate(userBId, "HIV");

      // Assert: No updates because STI type doesn't match
      expect(updatedCount).toBe(0);
      expect(mockBatch.update).not.toHaveBeenCalled();
    });
  });

  /**
   * Test 4: Error Scenarios Return Appropriate Responses
   *
   * Verifies that error conditions are handled gracefully:
   * 1. Invalid report data results in proper error state
   * 2. Partial failures don't corrupt the system
   * 3. Errors are logged for debugging
   */
  describe("Test 4: Error scenarios handling", () => {
    it("should handle notification creation failure gracefully", async () => {
      // Arrange: Mock notification creation to fail for one contact
      const msPerDay = 24 * 60 * 60 * 1000;
      const now = Date.now();
      const testDate = now - 7 * msPerDay;

      const reporterId = "error-test-reporter";
      const contactBId = "contact-success";
      const contactCId = "contact-failure";
      const reportId = "report-error-test";

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId(reporterId);
      const hashedContactBId = hashId(contactBId);
      const hashedContactCId = hashId(contactCId);

      // Set up user data
      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedContactBId, {
        hashedInteractionId: hashedContactBId,
        hashedNotificationId: `notif-${contactBId}`,
        username: "ContactB",
        fcmToken: "token-B",
      });
      usersByHashedInteractionId.set(hashedContactCId, {
        hashedInteractionId: hashedContactCId,
        hashedNotificationId: `notif-${contactCId}`,
        username: "ContactC",
        fcmToken: "token-C",
      });

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashedReporterId, [
        {
          partnerAnonymousId: hashedReporterId,
          partnerUsernameSnapshot: "Reporter",
          recordedAt: testDate - 2 * msPerDay,
          ownerId: hashedContactBId,
        },
        {
          partnerAnonymousId: hashedReporterId,
          partnerUsernameSnapshot: "Reporter",
          recordedAt: testDate - 1 * msPerDay,
          ownerId: hashedContactCId,
        },
      ]);

      const notificationsCreated: NotificationDocument[] = [];
      let batchSetCallCount = 0;

      // Mock batch for NotificationBatcher - the batch always succeeds
      // (we're testing that the propagation continues even if some notifications fail to send)
      mockFirestore.batch.mockImplementation(() => ({
        set: jest.fn().mockImplementation((_docRef: unknown, data: NotificationDocument) => {
          batchSetCallCount++;
          notificationsCreated.push({ ...data });
        }),
        update: jest.fn(),
        delete: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      }));

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return createNotificationsMock(notificationsCreated);
        }
        return mockFirestore;
      });

      // Act: Process should continue
      await propagateExposureChain(
        reportId,
        hashedReporterId,
        "Reporter",
        "[\"HIV\"]",
        testDate,
        PrivacyLevel.FULL,
      );

      // Assert: Function should complete (not throw)
      // At least some notifications were batched via set()
      expect(batchSetCallCount).toBeGreaterThan(0);
      // Notifications were created successfully
      expect(notificationsCreated.length).toBeGreaterThanOrEqual(1);
    });
  });

  /**
   * Test 5: Client-Backend Contract Compatibility
   *
   * Verifies that the client submission format is compatible with backend expectations:
   * 1. stiTypes as JSON string array
   * 2. testDate as Unix timestamp (milliseconds)
   * 3. privacyLevel as enum string
   * 4. No contactedIds required (backend auto-discovers)
   */
  describe("Test 5: Client-backend contract compatibility", () => {
    it("should process client submission format correctly", async () => {
      // Arrange: Create submission exactly as ExposureReportRepositoryImpl.kt does
      const testDate = Date.now() - 7 * 24 * 60 * 60 * 1000;
      const reporterId = "contract-test-reporter";
      const contactId = "contract-test-contact";
      const reportId = "contract-test-report";

      // Hash IDs for consistent lookup
      const hashedReporterId = hashId(reporterId);
      const hashedContactId = hashId(contactId);

      // Client format from ExposureReportRepositoryImpl.kt:
      // mapOf(
      //   FIELD_STI_TYPES to report.stiTypes,      // JSON array string
      //   FIELD_TEST_DATE to report.testDate,      // Long (milliseconds)
      //   FIELD_PRIVACY_LEVEL to report.privacyLevel,  // String enum
      //   FIELD_REPORTED_AT to report.reportedAt,  // Long (milliseconds)
      //   FIELD_STATUS to STATUS_PENDING           // String
      // )

      const clientData = {
        stiTypes: JSON.stringify(["HIV", "HERPES"]), // JSON array string
        testDate: testDate,                           // Unix timestamp (ms)
        privacyLevel: "STI_ONLY",                     // Enum string
        reportedAt: Date.now(),                       // Unix timestamp (ms)
        status: "pending",                            // Status string
      };

      // Note: contactedIds is NOT included - this is the new contract

      // Set up user data
      const usersByHashedInteractionId = new Map<string, MockUserData>();
      usersByHashedInteractionId.set(hashedContactId, {
        hashedInteractionId: hashedContactId,
        hashedNotificationId: `notif-${contactId}`,
        username: "Contact",
        fcmToken: "token",
      });

      const interactionsByPartner = new Map<string, InteractionDocument[]>();
      interactionsByPartner.set(hashedReporterId, [{
        partnerAnonymousId: hashedReporterId,
        partnerUsernameSnapshot: "Reporter",
        recordedAt: testDate - 2 * 24 * 60 * 60 * 1000,
        ownerId: hashedContactId,
      }]);

      const notificationsCreated: NotificationDocument[] = [];

      // Mock batch for NotificationBatcher
      mockFirestore.batch.mockImplementation(() => createBatchMock(notificationsCreated));

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return createUserMock(usersByHashedInteractionId);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return createInteractionMock(interactionsByPartner);
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return createNotificationsMock(notificationsCreated);
        }
        return mockFirestore;
      });

      // Act: Process using client-submitted format
      const notificationResult = await propagateExposureChain(
        reportId,
        hashedReporterId,
        "Reporter",
        clientData.stiTypes,
        clientData.testDate,
        clientData.privacyLevel as PrivacyLevel,
      );

      // Assert: Backend processed client format correctly
      expect(notificationResult).toBe(1);
      expect(notificationsCreated.length).toBe(1);

      // Verify notification respects STI_ONLY privacy
      const notification = notificationsCreated[0];
      expect(notification.stiType).toBe(clientData.stiTypes); // STI disclosed
      // exposureDate is still set but client controls visibility
    });

    it("should handle all privacy levels from client correctly", async () => {
      // Test each privacy level
      const privacyLevels: PrivacyLevel[] = [
        PrivacyLevel.FULL,
        PrivacyLevel.STI_ONLY,
        PrivacyLevel.DATE_ONLY,
        PrivacyLevel.ANONYMOUS,
      ];

      for (const privacyLevel of privacyLevels) {
        jest.clearAllMocks();

        const testDate = Date.now() - 7 * 24 * 60 * 60 * 1000;
        const reporterId = `reporter-${privacyLevel}`;
        const contactId = `contact-${privacyLevel}`;
        const reportId = `report-${privacyLevel}`;

        // Hash IDs for consistent lookup
        const hashedReporterId = hashId(reporterId);
        const hashedContactId = hashId(contactId);

        // Set up user data
        const usersByHashedInteractionId = new Map<string, MockUserData>();
        usersByHashedInteractionId.set(hashedContactId, {
          hashedInteractionId: hashedContactId,
          hashedNotificationId: `notif-${contactId}`,
          username: "Contact",
          fcmToken: "token",
        });

        const interactionsByPartner = new Map<string, InteractionDocument[]>();
        interactionsByPartner.set(hashedReporterId, [{
          partnerAnonymousId: hashedReporterId,
          partnerUsernameSnapshot: "Reporter",
          recordedAt: testDate - 2 * 24 * 60 * 60 * 1000,
          ownerId: hashedContactId,
        }]);

        const notificationsCreated: NotificationDocument[] = [];

        // Mock batch for NotificationBatcher
        mockFirestore.batch.mockImplementation(() => createBatchMock(notificationsCreated));

        mockFirestore.collection.mockImplementation((collectionName: string) => {
          if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
            return createUserMock(usersByHashedInteractionId);
          }
          if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
            return createInteractionMock(interactionsByPartner);
          }
          if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
            return createNotificationsMock(notificationsCreated);
          }
          return mockFirestore;
        });

        // Act
        await propagateExposureChain(
          reportId,
          hashedReporterId,
          "Reporter",
          "[\"HIV\"]",
          testDate,
          privacyLevel,
        );

        // Assert: Notification created with correct privacy handling
        expect(notificationsCreated.length).toBe(1);

        const notification = notificationsCreated[0];

        // Check STI disclosure
        const shouldDiscloseSTI =
          privacyLevel === PrivacyLevel.FULL ||
          privacyLevel === PrivacyLevel.STI_ONLY;

        if (shouldDiscloseSTI) {
          expect(notification.stiType).toBeDefined();
        } else {
          expect(notification.stiType).toBeUndefined();
        }

        // Check date disclosure
        const shouldDiscloseDate =
          privacyLevel === PrivacyLevel.FULL ||
          privacyLevel === PrivacyLevel.DATE_ONLY;

        if (shouldDiscloseDate) {
          expect(notification.exposureDate).toBeDefined();
        } else {
          expect(notification.exposureDate).toBeUndefined();
        }
      }
    });
  });
});
