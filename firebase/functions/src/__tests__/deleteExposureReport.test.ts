/**
 * deleteExposureReport Cloud Function Tests
 * Task 1.1: Write 2-6 focused tests for deleteExposureReport function
 *
 * Tests:
 * 1. Test successful deletion marks notifications as deleted
 * 2. Test creates REPORT_DELETED notification for impacted users
 * 3. Test validates reporter ownership before deletion
 * 4. Test handles non-existent report gracefully
 * 5. Test rejects deletion from non-owner user
 */

import * as admin from "firebase-admin";
import * as crypto from "crypto";
import {
  NotificationDocument,
  NotificationType,
  ReportDocument,
  ReportStatus,
  PrivacyLevel,
  TestStatus,
  CONSTANTS,
} from "../types";
import { createMockSnapshot, createMockQuerySnapshot } from "./setup";

/**
 * Hash a UID for reports.reporterId field.
 * Uses "report:" salt prefix to create domain-separated hash.
 * Must match hashForReport() in chainPropagation.ts
 */
function hashForReport(uid: string): string {
  return crypto.createHash("sha256").update("report:" + uid, "utf8").digest("hex");
}

/**
 * Hash a UID for notifications.recipientId field.
 * Uses "notification:" salt prefix for domain separation.
 * Must match hashForNotification() in chainPropagation.ts
 */
function hashForNotification(uid: string): string {
  return crypto.createHash("sha256").update("notification:" + uid, "utf8").digest("hex");
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

// Import the function after mocks are set up
// eslint-disable-next-line @typescript-eslint/no-require-imports, @typescript-eslint/no-var-requires
const { deleteExposureReport } = require("../functions/deleteExposureReport");

/**
 * Helper to create a mock callable context with authenticated user
 */
function createMockContext(uid: string) {
  return {
    auth: {
      uid,
      token: {},
    },
  };
}

describe("deleteExposureReport Cloud Function", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe("Test 1: Successful deletion marks notifications as deleted", () => {
    it("should mark all notifications associated with the report as deleted", async () => {
      // Arrange
      const reporterId = "reporter-user-1";
      const reportId = "test-report-1";
      const contactId1 = "contact-user-1";
      const contactId2 = "contact-user-2";

      const hashedReporterId = hashForReport(reporterId);
      const hashedContact1 = hashForNotification(contactId1);
      const hashedContact2 = hashForNotification(contactId2);

      // Mock report document
      const mockReport: ReportDocument = {
        reporterId: hashedReporterId,
        reporterInteractionHashedId: "hashed-interaction-id",
        stiTypes: "[\"HIV\"]",
        testDate: Date.now() - 7 * 24 * 60 * 60 * 1000,
        privacyLevel: PrivacyLevel.FULL,
        reportedAt: Date.now() - 7 * 24 * 60 * 60 * 1000,
        status: ReportStatus.COMPLETED,
        testResult: TestStatus.POSITIVE,
      };

      // Mock notifications
      const notification1: NotificationDocument = {
        recipientId: hashedContact1,
        type: NotificationType.EXPOSURE,
        stiType: "[\"HIV\"]",
        exposureDate: Date.now() - 7 * 24 * 60 * 60 * 1000,
        chainData: JSON.stringify({ nodes: [] }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: reportId,
        chainPath: [],
      };

      const notification2: NotificationDocument = {
        recipientId: hashedContact2,
        type: NotificationType.EXPOSURE,
        stiType: "[\"HIV\"]",
        exposureDate: Date.now() - 7 * 24 * 60 * 60 * 1000,
        chainData: JSON.stringify({ nodes: [] }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: reportId,
        chainPath: [],
      };

      const updatedNotifications: string[] = [];
      const createdNotifications: NotificationDocument[] = [];

      // Set up mocks
      const mockBatch = {
        update: jest.fn().mockImplementation((ref) => {
          updatedNotifications.push(ref.id);
        }),
        set: jest.fn().mockImplementation((_ref, data: NotificationDocument) => {
          createdNotifications.push(data);
        }),
        commit: jest.fn().mockResolvedValue(undefined),
      };

      mockFirestore.batch.mockReturnValue(mockBatch);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.REPORTS) {
          return {
            doc: (docId: string) => ({
              id: docId,
              get: () => Promise.resolve(createMockSnapshot(mockReport, reportId)),
            }),
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: () => ({
              get: () => Promise.resolve(
                createMockQuerySnapshot([
                  { id: "notification-1", data: notification1 },
                  { id: "notification-2", data: notification2 },
                ])
              ),
            }),
            doc: jest.fn().mockImplementation(() => ({
              id: `new-notification-${createdNotifications.length + 1}`,
            })),
          };
        }
        return mockFirestore;
      });

      // Act: Call the wrapped function
      const result = await deleteExposureReport.run(
        { reportId },
        createMockContext(reporterId)
      );

      // Assert
      expect(result.success).toBe(true);
      expect(result.deletedNotificationsCount).toBe(2);
      expect(mockBatch.update).toHaveBeenCalledTimes(2);
      expect(mockBatch.commit).toHaveBeenCalled();
    });
  });

  describe("Test 2: Marks notifications as deleted for impacted users", () => {
    it("should mark existing notifications as deleted for each unique recipient", async () => {
      // Arrange
      const reporterId = "reporter-user-2";
      const reportId = "test-report-2";
      const contactId = "contact-user-3";

      const hashedReporterId = hashForReport(reporterId);
      const hashedContact = hashForNotification(contactId);

      const mockReport: ReportDocument = {
        reporterId: hashedReporterId,
        reporterInteractionHashedId: "hashed-interaction-id",
        stiTypes: "[\"SYPHILIS\", \"CHLAMYDIA\"]",
        testDate: Date.now() - 10 * 24 * 60 * 60 * 1000,
        privacyLevel: PrivacyLevel.FULL,
        reportedAt: Date.now() - 10 * 24 * 60 * 60 * 1000,
        status: ReportStatus.COMPLETED,
        testResult: TestStatus.POSITIVE,
      };

      const existingNotification: NotificationDocument = {
        recipientId: hashedContact,
        type: NotificationType.EXPOSURE,
        stiType: "[\"SYPHILIS\", \"CHLAMYDIA\"]",
        exposureDate: Date.now() - 10 * 24 * 60 * 60 * 1000,
        chainData: JSON.stringify({ nodes: [] }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: reportId,
        chainPath: [],
      };

      const updatedNotificationRefs: string[] = [];

      const mockBatch = {
        update: jest.fn().mockImplementation((ref: { id: string }) => {
          updatedNotificationRefs.push(ref.id);
        }),
        set: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      };

      mockFirestore.batch.mockReturnValue(mockBatch);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.REPORTS) {
          return {
            doc: (docId: string) => ({
              id: docId,
              get: () => Promise.resolve(createMockSnapshot(mockReport, reportId)),
            }),
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: () => ({
              get: () => Promise.resolve(
                createMockQuerySnapshot([
                  { id: "notification-1", data: existingNotification },
                ])
              ),
            }),
            doc: jest.fn().mockImplementation(() => ({
              id: `notification-${updatedNotificationRefs.length + 1}`,
            })),
          };
        }
        return mockFirestore;
      });

      // Act
      const result = await deleteExposureReport.run(
        { reportId },
        createMockContext(reporterId)
      );

      // Assert: Notifications are marked as deleted (not new notifications created)
      expect(result.success).toBe(true);
      expect(result.deletedNotificationsCount).toBe(1);
      expect(mockBatch.update).toHaveBeenCalledTimes(1);
      expect(mockBatch.commit).toHaveBeenCalled();
    });
  });

  describe("Test 3: Validates reporter ownership before deletion", () => {
    it("should verify the authenticated user is the report owner", async () => {
      // Arrange
      const reporterId = "reporter-user-3";
      const reportId = "test-report-3";
      const hashedReporterId = hashForReport(reporterId);

      const mockReport: ReportDocument = {
        reporterId: hashedReporterId,
        reporterInteractionHashedId: "hashed-interaction-id",
        stiTypes: "[\"HIV\"]",
        testDate: Date.now() - 5 * 24 * 60 * 60 * 1000,
        privacyLevel: PrivacyLevel.ANONYMOUS,
        reportedAt: Date.now() - 5 * 24 * 60 * 60 * 1000,
        status: ReportStatus.COMPLETED,
        testResult: TestStatus.POSITIVE,
      };

      const mockBatch = {
        update: jest.fn(),
        set: jest.fn(),
        commit: jest.fn().mockResolvedValue(undefined),
      };

      mockFirestore.batch.mockReturnValue(mockBatch);

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.REPORTS) {
          return {
            doc: (docId: string) => ({
              id: docId,
              get: () => Promise.resolve(createMockSnapshot(mockReport, reportId)),
            }),
          };
        }
        if (collectionName === CONSTANTS.COLLECTIONS.NOTIFICATIONS) {
          return {
            where: () => ({
              get: () => Promise.resolve(createMockQuerySnapshot([])),
            }),
            doc: jest.fn().mockImplementation(() => ({
              id: "new-notification",
            })),
          };
        }
        return mockFirestore;
      });

      // Act: Call with the correct owner
      const result = await deleteExposureReport.run(
        { reportId },
        createMockContext(reporterId)
      );

      // Assert: Should succeed because user owns the report
      expect(result.success).toBe(true);
      expect(mockBatch.commit).toHaveBeenCalled();
    });
  });

  describe("Test 4: Handles non-existent report gracefully", () => {
    it("should return failure when report does not exist", async () => {
      // Arrange
      const reporterId = "reporter-user-4";
      const reportId = "non-existent-report";

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.REPORTS) {
          return {
            doc: (docId: string) => ({
              id: docId,
              get: () => Promise.resolve({
                exists: false,
                data: () => null,
                id: docId,
              }),
            }),
          };
        }
        return mockFirestore;
      });

      // Act
      const result = await deleteExposureReport.run(
        { reportId },
        createMockContext(reporterId)
      );

      // Assert
      expect(result.success).toBe(false);
      expect(result.error).toBe("Report not found");
      expect(result.deletedNotificationsCount).toBe(0);
    });
  });

  describe("Test 5: Rejects deletion from non-owner user", () => {
    it("should throw permission-denied error when user does not own the report", async () => {
      // Arrange
      const actualOwnerId = "actual-owner-user";
      const attemptingUserId = "different-user";
      const reportId = "test-report-5";
      const hashedActualOwnerId = hashForReport(actualOwnerId);

      const mockReport: ReportDocument = {
        reporterId: hashedActualOwnerId, // Owned by actualOwnerId
        reporterInteractionHashedId: "hashed-interaction-id",
        stiTypes: "[\"GONORRHEA\"]",
        testDate: Date.now() - 3 * 24 * 60 * 60 * 1000,
        privacyLevel: PrivacyLevel.STI_ONLY,
        reportedAt: Date.now() - 3 * 24 * 60 * 60 * 1000,
        status: ReportStatus.COMPLETED,
        testResult: TestStatus.POSITIVE,
      };

      mockFirestore.collection.mockImplementation((collectionName: string) => {
        if (collectionName === CONSTANTS.COLLECTIONS.REPORTS) {
          return {
            doc: (docId: string) => ({
              id: docId,
              get: () => Promise.resolve(createMockSnapshot(mockReport, reportId)),
            }),
          };
        }
        return mockFirestore;
      });

      // Act & Assert: Should throw HttpsError with permission-denied
      await expect(
        deleteExposureReport.run(
          { reportId },
          createMockContext(attemptingUserId) // Different user trying to delete
        )
      ).rejects.toThrow("You do not have permission to delete this report");
    });
  });
});
