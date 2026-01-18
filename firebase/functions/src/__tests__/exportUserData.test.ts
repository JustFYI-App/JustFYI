/**
 * exportUserData Cloud Function Tests
 * Task 1.1: Write 4-6 focused tests for exportUserData Cloud Function
 *
 * Tests:
 * 1. Test authenticated user can export their data
 * 2. Test unauthenticated request returns HttpsError("unauthenticated")
 * 3. Test fcmToken is excluded from user data response
 * 4. Test data filtering by userId for each collection
 * 5. Test chainData remains in raw JSON string format
 * 6. Test response structure matches specification
 */

import * as admin from "firebase-admin";
import * as functionsV1 from "firebase-functions/v1";
import {
  UserDocument,
  InteractionDocument,
  NotificationDocument,
  ReportDocument,
  NotificationType,
  ReportStatus,
  PrivacyLevel,
  TestStatus,
  CONSTANTS,
} from "../types";
import { createMockSnapshot, createMockQuerySnapshot } from "./setup";
import {
  hashAnonymousId,
  hashForNotification,
  hashForReport,
} from "../utils/chainPropagation";

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
 * Helper to create a mock CallableContext with proper auth data
 */
function createMockContext(userId?: string): functionsV1.https.CallableContext {
  if (!userId) {
    return {
      auth: undefined,
      rawRequest: {} as functionsV1.https.Request,
    };
  }
  return {
    auth: {
      uid: userId,
      token: {} as admin.auth.DecodedIdToken,
      rawToken: "mock-raw-token",
    },
    rawRequest: {} as functionsV1.https.Request,
  };
}

/**
 * Helper to create a mock for rateLimits collection
 * This is used by the checkRateLimit function
 */
function createRateLimitMock() {
  return {
    doc: () => ({
      get: () => Promise.resolve(createMockSnapshot(null)),
      set: jest.fn().mockResolvedValue(undefined),
      update: jest.fn().mockResolvedValue(undefined),
    }),
  };
}

// Mock the exportUserData function for testing
// We import the actual implementation after setting up mocks
let exportUserDataHandler: (data: unknown, context: functionsV1.https.CallableContext) => Promise<unknown>;

beforeAll(async () => {
  // Dynamically import the function after mocks are set up
  const module = await import("../functions/exportUserData");
  // Get the handler from the wrapped function
  exportUserDataHandler = (module.exportUserData as unknown as { run: typeof exportUserDataHandler }).run;
});

describe("exportUserData Cloud Function", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe("Test 1: Authenticated user can export their data", () => {
    it("should return user data for authenticated requests", async () => {
      // Arrange
      const userId = "test-user-123";
      const mockUser: UserDocument = {
        anonymousId: userId,
        username: "TestUser",
        createdAt: Date.now(),
        fcmToken: "test-fcm-token",
      };

      const mockInteraction: InteractionDocument = {
        partnerAnonymousId: "partner-123",
        partnerUsernameSnapshot: "Partner",
        recordedAt: Date.now(),
        ownerId: userId,
      };

      const mockNotification: NotificationDocument = {
        recipientId: userId,
        type: NotificationType.EXPOSURE,
        stiType: "HIV",
        chainData: JSON.stringify({ nodes: [] }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-1",
        chainPath: [userId],
      };

      const mockReport: ReportDocument = {
        reporterId: userId,
        reporterInteractionHashedId: "hashed-id",
        stiTypes: "[\"HIV\"]",
        testDate: Date.now(),
        privacyLevel: PrivacyLevel.FULL,
        reportedAt: Date.now(),
        status: ReportStatus.COMPLETED,
        testResult: TestStatus.POSITIVE,
      };

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: () => ({
              get: () => Promise.resolve(createMockSnapshot(mockUser)),
            }),
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.INTERACTIONS) {
          return {
            where: () => ({
              get: () => Promise.resolve(
                createMockQuerySnapshot([{ id: "interaction-1", data: mockInteraction }])
              ),
            }),
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: () => ({
              get: () => Promise.resolve(
                createMockQuerySnapshot([{ id: "notification-1", data: mockNotification }])
              ),
            }),
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.REPORTS) {
          return {
            where: () => ({
              get: () => Promise.resolve(
                createMockQuerySnapshot([{ id: "report-1", data: mockReport }])
              ),
            }),
          };
        }
        return mockFirestore;
      });

      const context = createMockContext(userId);

      // Act
      const result = await exportUserDataHandler({}, context) as {
        user: unknown;
        interactions: unknown[];
        notifications: unknown[];
        reports: unknown[];
      };

      // Assert
      expect(result).toBeDefined();
      expect(result.user).toBeDefined();
      expect(result.interactions).toBeDefined();
      expect(result.notifications).toBeDefined();
      expect(result.reports).toBeDefined();
      expect(Array.isArray(result.interactions)).toBe(true);
      expect(Array.isArray(result.notifications)).toBe(true);
      expect(Array.isArray(result.reports)).toBe(true);
    });
  });

  describe("Test 2: Unauthenticated request returns HttpsError", () => {
    it("should throw unauthenticated error when no auth context", async () => {
      // Arrange
      const context = createMockContext();

      // Act & Assert
      await expect(exportUserDataHandler({}, context)).rejects.toThrow();

      try {
        await exportUserDataHandler({}, context);
      } catch (error) {
        expect((error as functionsV1.https.HttpsError).code).toBe("unauthenticated");
      }
    });
  });

  describe("Test 3: FCM token is excluded from user data response", () => {
    it("should not include fcmToken in exported user data", async () => {
      // Arrange
      const userId = "test-user-456";
      const mockUser: UserDocument = {
        anonymousId: userId,
        username: "TestUser",
        createdAt: Date.now(),
        fcmToken: "secret-fcm-token-should-not-appear",
      };

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: () => ({
              get: () => Promise.resolve(createMockSnapshot(mockUser)),
            }),
          };
        }
        if (collectionName === "rateLimits") {
          return createRateLimitMock();
        }
        // Return empty results for other collections
        return {
          where: () => ({
            get: () => Promise.resolve(createMockQuerySnapshot([])),
          }),
        };
      });

      const context = createMockContext(userId);

      // Act
      const result = await exportUserDataHandler({}, context) as {
        user: Record<string, unknown>;
      };

      // Assert
      expect(result.user).toBeDefined();
      expect(result.user.fcmToken).toBeUndefined();
      expect(result.user.username).toBe("TestUser");
      expect(result.user.anonymousId).toBe(userId);
    });
  });

  describe("Test 4: Data filtering by userId for each collection", () => {
    it("should query collections with correct userId filters", async () => {
      // Arrange
      const userId = "filter-test-user";
      const mockUser: UserDocument = {
        anonymousId: userId,
        username: "FilterTestUser",
        createdAt: Date.now(),
      };

      // Track which queries were made
      const queriedFilters: { collection: string; field: string; value: string }[] = [];

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: (docId: string) => {
              queriedFilters.push({ collection: collectionName, field: "docId", value: docId });
              return {
                get: () => Promise.resolve(createMockSnapshot(mockUser)),
              };
            },
          };
        }
        if (collectionName === "rateLimits") {
          return createRateLimitMock();
        }
        return {
          where: (field: string, _op: string, value: string) => {
            queriedFilters.push({ collection: collectionName, field, value });
            return {
              get: () => Promise.resolve(createMockQuerySnapshot([])),
            };
          },
        };
      });

      const context = createMockContext(userId);

      // Act
      await exportUserDataHandler({}, context);

      // Assert: Verify each collection was queried with the correct field
      // Users collection uses raw userId as document ID
      expect(queriedFilters).toContainEqual({
        collection: CONSTANTS.COLLECTIONS.USERS,
        field: "docId",
        value: userId,
      });
      // Other collections use domain-separated hashed IDs
      expect(queriedFilters).toContainEqual({
        collection: CONSTANTS.COLLECTIONS.INTERACTIONS,
        field: "ownerId",
        value: hashAnonymousId(userId),
      });
      expect(queriedFilters).toContainEqual({
        collection: CONSTANTS.COLLECTIONS.NOTIFICATIONS,
        field: "recipientId",
        value: hashForNotification(userId),
      });
      expect(queriedFilters).toContainEqual({
        collection: CONSTANTS.COLLECTIONS.REPORTS,
        field: "reporterId",
        value: hashForReport(userId),
      });
    });
  });

  describe("Test 5: chainData remains in raw JSON string format", () => {
    it("should not parse or transform chainData field", async () => {
      // Arrange
      const userId = "chain-test-user";
      const mockUser: UserDocument = {
        anonymousId: userId,
        username: "ChainTestUser",
        createdAt: Date.now(),
      };

      const chainDataString = JSON.stringify({
        nodes: [
          { username: "User1", testStatus: "POSITIVE", isCurrentUser: false },
          { username: "You", testStatus: "UNKNOWN", isCurrentUser: true },
        ],
      });

      const mockNotification: NotificationDocument = {
        recipientId: userId,
        type: NotificationType.EXPOSURE,
        stiType: "HIV",
        chainData: chainDataString,
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-1",
        chainPath: ["user1", userId],
      };

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: () => ({
              get: () => Promise.resolve(createMockSnapshot(mockUser)),
            }),
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: () => ({
              get: () => Promise.resolve(
                createMockQuerySnapshot([{ id: "notification-1", data: mockNotification }])
              ),
            }),
          };
        }
        if (collectionName === "rateLimits") {
          return createRateLimitMock();
        }
        return {
          where: () => ({
            get: () => Promise.resolve(createMockQuerySnapshot([])),
          }),
        };
      });

      const context = createMockContext(userId);

      // Act
      const result = await exportUserDataHandler({}, context) as {
        notifications: Array<{ chainData: unknown }>;
      };

      // Assert: chainData should be a string, not parsed
      expect(result.notifications.length).toBe(1);
      expect(typeof result.notifications[0].chainData).toBe("string");
      expect(result.notifications[0].chainData).toBe(chainDataString);
    });
  });

  describe("Test 6: Response structure matches specification", () => {
    it("should return correct structure with all 4 properties", async () => {
      // Arrange
      const userId = "structure-test-user";
      const mockUser: UserDocument = {
        anonymousId: userId,
        username: "StructureTestUser",
        createdAt: Date.now(),
      };

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: () => ({
              get: () => Promise.resolve(createMockSnapshot(mockUser)),
            }),
          };
        }
        if (collectionName === "rateLimits") {
          return createRateLimitMock();
        }
        return {
          where: () => ({
            get: () => Promise.resolve(createMockQuerySnapshot([])),
          }),
        };
      });

      const context = createMockContext(userId);

      // Act
      const result = await exportUserDataHandler({}, context) as Record<string, unknown>;

      // Assert: Verify structure matches { user: {...}, interactions: [...], notifications: [...], reports: [...] }
      const keys = Object.keys(result);
      expect(keys).toContain("user");
      expect(keys).toContain("interactions");
      expect(keys).toContain("notifications");
      expect(keys).toContain("reports");
      expect(keys.length).toBe(4); // Exactly 4 properties

      // Verify types
      expect(typeof result.user).toBe("object");
      expect(Array.isArray(result.interactions)).toBe(true);
      expect(Array.isArray(result.notifications)).toBe(true);
      expect(Array.isArray(result.reports)).toBe(true);
    });

    it("should handle missing user document gracefully", async () => {
      // Arrange
      const userId = "missing-user";

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.USERS) {
          return {
            doc: () => ({
              get: () => Promise.resolve(createMockSnapshot(null)),
            }),
          };
        }
        if (collectionName === "rateLimits") {
          return createRateLimitMock();
        }
        return {
          where: () => ({
            get: () => Promise.resolve(createMockQuerySnapshot([])),
          }),
        };
      });

      const context = createMockContext(userId);

      // Act
      const result = await exportUserDataHandler({}, context) as {
        user: unknown;
        interactions: unknown[];
        notifications: unknown[];
        reports: unknown[];
      };

      // Assert: Should return null for user but still have the structure
      expect(result.user).toBeNull();
      expect(Array.isArray(result.interactions)).toBe(true);
      expect(Array.isArray(result.notifications)).toBe(true);
      expect(Array.isArray(result.reports)).toBe(true);
    });
  });
});
