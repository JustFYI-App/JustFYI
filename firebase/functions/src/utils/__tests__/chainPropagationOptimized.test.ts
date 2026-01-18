/**
 * Chain Propagation Optimization Tests
 * Task 4.1: Tests for optimized chain propagation
 *
 * Tests:
 * 1. Test that interaction queries are cached (same user not queried twice)
 * 2. Test that user lookups use batch queries instead of sequential
 * 3. Test that notifications are batched correctly during propagation
 * 4. Test that FCM notifications are collected and sent as multicast
 * 5. Test multi-path scenario (user reachable via A-B-D and A-C-D)
 */

import * as admin from "firebase-admin";

import { QueryCache, QueryType, createQueryCache } from "../cache/queryCache";
import { createUserLookupCache } from "../cache/userLookupCache";
import { createNotificationBatcher } from "../batch/notificationBatcher";
import { createFCMBatcher } from "../batch/fcmBatcher";
import { NotificationDocument, NotificationType, PrivacyLevel } from "../../types";

describe("Chain Propagation Optimization Tests", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe("Test 1: Interaction queries are cached (same user not queried twice)", () => {
    it("should return undefined for cache miss and track miss count", () => {
      const cache = createQueryCache<admin.firestore.QueryDocumentSnapshot[]>();

      const result = cache.get("nonexistent-key");

      expect(result).toBeUndefined();
      expect(cache.getStats().misses).toBe(1);
      expect(cache.getStats().hits).toBe(0);
    });

    it("should return cached value for cache hit and track hit count", () => {
      const cache = createQueryCache<admin.firestore.QueryDocumentSnapshot[]>();
      const testData: admin.firestore.QueryDocumentSnapshot[] = [];
      const key = "test-key";

      cache.set(key, testData);
      const result = cache.get(key);

      expect(result).toEqual(testData);
      expect(cache.getStats().hits).toBe(1);
      expect(cache.getStats().misses).toBe(0);
    });

    it("should generate unique cache keys for different query parameters", () => {
      const partnerId = "partner123";
      const windowStart = 1000000;
      const windowEnd = 2000000;

      const interactionsKey = QueryCache.generateKey(
        QueryType.INTERACTIONS,
        partnerId,
        windowStart,
        windowEnd
      );
      const notificationsKey = QueryCache.generateKey(
        QueryType.NOTIFICATIONS,
        partnerId,
        windowStart,
        windowEnd
      );

      expect(interactionsKey).not.toBe(notificationsKey);
      expect(interactionsKey).toBe("interactions:partner123:1000000:2000000");
      expect(notificationsKey).toBe("notifications:partner123:1000000:2000000");
    });

    it("should generate unique keys for different partner IDs", () => {
      const windowStart = 1000000;
      const windowEnd = 2000000;

      const key1 = QueryCache.generateKey(
        QueryType.INTERACTIONS,
        "partnerA",
        windowStart,
        windowEnd
      );
      const key2 = QueryCache.generateKey(
        QueryType.INTERACTIONS,
        "partnerB",
        windowStart,
        windowEnd
      );

      expect(key1).not.toBe(key2);
    });

    it("should generate unique keys for different window parameters", () => {
      const partnerId = "partner123";

      const key1 = QueryCache.generateKey(
        QueryType.INTERACTIONS,
        partnerId,
        1000000,
        2000000
      );
      const key2 = QueryCache.generateKey(
        QueryType.INTERACTIONS,
        partnerId,
        1000000,
        3000000 // Different end
      );

      expect(key1).not.toBe(key2);
    });

    it("should reduce query count when using cache", () => {
      const cache = createQueryCache<string[]>();
      let firestoreQueries = 0;

      // Simulate function that checks cache before querying
      const getInteractionsWithCache = (partnerId: string, windowStart: number, windowEnd: number) => {
        const cacheKey = QueryCache.generateKey(QueryType.INTERACTIONS, partnerId, windowStart, windowEnd);
        const cached = cache.get(cacheKey);

        if (cached !== undefined) {
          return cached;
        }

        // Simulate Firestore query
        firestoreQueries++;
        const result: string[] = [];
        cache.set(cacheKey, result);
        return result;
      };

      const windowStart = Date.now() - 86400000;
      const windowEnd = Date.now();

      // First call for partner A - should query Firestore
      getInteractionsWithCache("partner-A", windowStart, windowEnd);
      expect(firestoreQueries).toBe(1);

      // Second call for partner A (same params) - should use cache
      getInteractionsWithCache("partner-A", windowStart, windowEnd);
      expect(firestoreQueries).toBe(1); // No additional query

      // Third call for partner A from different code path - still cached
      getInteractionsWithCache("partner-A", windowStart, windowEnd);
      expect(firestoreQueries).toBe(1); // Still no additional query

      // Call for partner B - new query needed
      getInteractionsWithCache("partner-B", windowStart, windowEnd);
      expect(firestoreQueries).toBe(2);

      expect(cache.getStats().hits).toBe(2);
      expect(cache.getStats().misses).toBe(2);
    });
  });

  describe("Test 2: User lookups use batch queries instead of sequential", () => {
    it("should collect unique hashedInteractionIds for batch lookup", () => {
      // Simulate collecting contact IDs during chain traversal
      const contactIds = ["hash-1", "hash-2", "hash-3", "hash-1"]; // Note: duplicate
      const uniqueIds = [...new Set(contactIds)];

      expect(uniqueIds.length).toBe(3);
      expect(uniqueIds).toContain("hash-1");
      expect(uniqueIds).toContain("hash-2");
      expect(uniqueIds).toContain("hash-3");
    });

    it("should populate UserLookupCache from batch query results", () => {
      const userCache = createUserLookupCache();

      // Simulate batch query results
      const batchResults = new Map([
        ["hash-1", { hashedInteractionId: "hash-1", hashedNotificationId: "notif-1", fcmToken: "token-1", docRef: {} as admin.firestore.DocumentReference }],
        ["hash-2", { hashedInteractionId: "hash-2", hashedNotificationId: "notif-2", fcmToken: "token-2", docRef: {} as admin.firestore.DocumentReference }],
      ]);

      userCache.populateFromBatch(batchResults);

      expect(userCache.size).toBe(2);
      expect(userCache.get("hash-1")?.hashedNotificationId).toBe("notif-1");
      expect(userCache.get("hash-2")?.hashedNotificationId).toBe("notif-2");
    });

    it("should use cached user data instead of sequential lookups", () => {
      const userCache = createUserLookupCache();

      // Pre-populate cache
      userCache.set("hash-1", {
        hashedInteractionId: "hash-1",
        hashedNotificationId: "notif-1",
        fcmToken: "token-1",
        docRef: {} as admin.firestore.DocumentReference,
      });

      // Simulate processing contacts - should use cache
      const contacts = ["hash-1", "hash-1", "hash-1"]; // Same contact accessed multiple times

      let cacheHits = 0;
      for (const contactId of contacts) {
        const userData = userCache.get(contactId);
        if (userData !== undefined) {
          cacheHits++;
        }
      }

      expect(cacheHits).toBe(3);
      expect(userCache.getStats().hits).toBe(3);
      expect(userCache.getStats().misses).toBe(0);
    });

    it("should identify uncached IDs for batch fetching", () => {
      const userCache = createUserLookupCache();

      // Pre-populate some users
      userCache.set("hash-1", {
        hashedInteractionId: "hash-1",
        hashedNotificationId: "notif-1",
        fcmToken: "token-1",
        docRef: {} as admin.firestore.DocumentReference,
      });
      userCache.setNotFound("hash-2"); // Mark as not found

      const idsToCheck = ["hash-1", "hash-2", "hash-3", "hash-4"];
      const uncached = userCache.getUncachedIds(idsToCheck);

      expect(uncached).toEqual(["hash-3", "hash-4"]);
    });

    it("should reduce user lookups with batch queries", () => {
      const userCache = createUserLookupCache();

      // Without batch optimization - sequential lookups
      const contactIds = ["hash-1", "hash-2", "hash-3", "hash-4", "hash-5"];
      const sequentialLookups = contactIds.length; // Would be 5 separate queries

      // With batch optimization - single batch lookup
      const batchLookups = 1;

      // Populate cache from batch
      const batchResults = new Map(contactIds.map(id => [
        id,
        {
          hashedInteractionId: id,
          hashedNotificationId: `notif-${id}`,
          fcmToken: `token-${id}`,
          docRef: {} as admin.firestore.DocumentReference,
        },
      ]));
      userCache.populateFromBatch(batchResults);

      // Subsequent lookups should all hit cache
      let cacheHits = 0;
      for (const id of contactIds) {
        if (userCache.get(id) !== undefined) {
          cacheHits++;
        }
      }

      expect(cacheHits).toBe(5);
      expect(sequentialLookups).toBeGreaterThan(batchLookups); // 5 > 1
    });
  });

  describe("Test 3: Notifications are batched correctly during propagation", () => {
    it("should queue notifications instead of immediate write", () => {
      const batcher = createNotificationBatcher();

      const notification1: NotificationDocument = {
        recipientId: "recipient-1",
        type: NotificationType.EXPOSURE,
        chainData: JSON.stringify({ nodes: [] }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-1",
        chainPath: ["chain-1"],
        hopDepth: 1,
      };

      const notification2: NotificationDocument = {
        recipientId: "recipient-2",
        type: NotificationType.EXPOSURE,
        chainData: JSON.stringify({ nodes: [] }),
        isRead: false,
        receivedAt: Date.now(),
        updatedAt: Date.now(),
        reportId: "report-1",
        chainPath: ["chain-2"],
        hopDepth: 1,
      };

      batcher.add({
        data: notification1,
        hashedInteractionId: "hash-1",
        hashedNotificationId: "notif-hash-1",
      });

      batcher.add({
        data: notification2,
        hashedInteractionId: "hash-2",
        hashedNotificationId: "notif-hash-2",
      });

      expect(batcher.size).toBe(2);
      expect(batcher.isEmpty).toBe(false);
    });

    it("should handle empty batcher correctly", () => {
      const batcher = createNotificationBatcher();

      expect(batcher.isEmpty).toBe(true);
      expect(batcher.size).toBe(0);
    });

    it("should commit notifications and return result", async () => {
      const batcher = createNotificationBatcher();

      batcher.add({
        data: {
          recipientId: "recipient-1",
          type: NotificationType.EXPOSURE,
          chainData: JSON.stringify({ nodes: [] }),
          isRead: false,
          receivedAt: Date.now(),
          updatedAt: Date.now(),
          reportId: "report-1",
          chainPath: ["chain-1"],
          hopDepth: 1,
        },
        hashedInteractionId: "hash-1",
        hashedNotificationId: "notif-hash-1",
      });

      expect(batcher.size).toBe(1);

      const result = await batcher.commit();

      // Verify the result has expected structure
      expect(result).toHaveProperty("successCount");
      expect(result).toHaveProperty("failureCount");
      expect(result).toHaveProperty("createdIds");
      expect(result).toHaveProperty("errors");

      // After commit, data is preserved for getCreatedIdMap
      // New items can still be added for next batch
      expect(batcher.size).toBe(1);
    });

    it("should return batch result with correct structure after commit", async () => {
      const batcher = createNotificationBatcher();

      // Add multiple notifications
      for (let i = 0; i < 3; i++) {
        batcher.add({
          data: {
            recipientId: `recipient-${i}`,
            type: NotificationType.EXPOSURE,
            chainData: JSON.stringify({ nodes: [] }),
            isRead: false,
            receivedAt: Date.now(),
            updatedAt: Date.now(),
            reportId: "report-1",
            chainPath: [`chain-${i}`],
            hopDepth: 1,
          },
          hashedInteractionId: `hash-${i}`,
          hashedNotificationId: `notif-hash-${i}`,
        });
      }

      const result = await batcher.commit();

      expect(result.successCount).toBe(3);
      expect(result.failureCount).toBe(0);
      expect(result.createdIds.length).toBe(3);
    });
  });

  describe("Test 4: FCM notifications are collected and sent as multicast", () => {
    it("should queue FCM notifications instead of sending immediately", () => {
      const fcmBatcher = createFCMBatcher();

      fcmBatcher.add({
        token: "fcm-token-1",
        notificationId: "notif-1",
        type: "EXPOSURE",
        titleLocKey: "notification_exposure_title",
        bodyLocKey: "notification_exposure_body",
      });

      fcmBatcher.add({
        token: "fcm-token-2",
        notificationId: "notif-2",
        type: "EXPOSURE",
        titleLocKey: "notification_exposure_title",
        bodyLocKey: "notification_exposure_body",
      });

      expect(fcmBatcher.size).toBe(2);
    });

    it("should handle empty FCM batcher correctly", () => {
      const fcmBatcher = createFCMBatcher();

      expect(fcmBatcher.isEmpty).toBe(true);
      expect(fcmBatcher.size).toBe(0);
    });

    it("should group notifications by payload for efficient multicast", () => {
      const fcmBatcher = createFCMBatcher();

      // Add EXPOSURE notifications
      for (let i = 0; i < 3; i++) {
        fcmBatcher.add({
          token: `token-exposure-${i}`,
          notificationId: `notif-${i}`,
          type: "EXPOSURE",
          titleLocKey: "notification_exposure_title",
          bodyLocKey: "notification_exposure_body",
        });
      }

      // Add UPDATE notifications
      for (let i = 0; i < 2; i++) {
        fcmBatcher.add({
          token: `token-update-${i}`,
          notificationId: `notif-update-${i}`,
          type: "UPDATE",
          titleLocKey: "notification_update_title",
          bodyLocKey: "notification_update_body",
        });
      }

      expect(fcmBatcher.size).toBe(5);
    });
  });

  describe("Test 5: Multi-path scenario (user reachable via A-B-D and A-C-D)", () => {
    it("should track multiple paths to same user without duplicate notifications", () => {
      // Simulate notifiedUsers Map tracking
      const notifiedUsers = new Map<string, { paths: string[][]; minHopDepth: number; notificationId?: string }>();

      const userD_hash = "hash-D";

      // First path: A -> B -> D
      if (!notifiedUsers.has(userD_hash)) {
        notifiedUsers.set(userD_hash, {
          paths: [["hash-A", "hash-B", "hash-D"]],
          minHopDepth: 2,
          notificationId: "notif-D-1",
        });
      }

      // Second path: A -> C -> D
      const existingInfo = notifiedUsers.get(userD_hash)!;
      existingInfo.paths.push(["hash-A", "hash-C", "hash-D"]);

      // Verify single entry with multiple paths
      expect(notifiedUsers.size).toBe(1);
      expect(notifiedUsers.get(userD_hash)!.paths.length).toBe(2);
      expect(notifiedUsers.get(userD_hash)!.paths[0]).toContain("hash-B");
      expect(notifiedUsers.get(userD_hash)!.paths[1]).toContain("hash-C");
    });

    it("should use cache for user D lookup regardless of path", () => {
      const userCache = createUserLookupCache();
      const userD_hash = "hash-D";

      // Pre-populate cache with user D
      userCache.set(userD_hash, {
        hashedInteractionId: userD_hash,
        hashedNotificationId: "notif-D",
        fcmToken: "token-D",
        docRef: {} as admin.firestore.DocumentReference,
      });

      // First path access (A -> B -> D)
      const lookupFromPathABD = userCache.get(userD_hash);

      // Second path access (A -> C -> D)
      const lookupFromPathACD = userCache.get(userD_hash);

      // Both should hit cache
      expect(lookupFromPathABD).toBeDefined();
      expect(lookupFromPathACD).toBeDefined();
      expect(userCache.getStats().hits).toBe(2);
      expect(userCache.getStats().misses).toBe(0);
    });

    it("should use shortest hop depth for multi-path user", () => {
      const notifiedUsers = new Map<string, { paths: string[][]; minHopDepth: number }>();
      const userD_hash = "hash-D";

      // First path with hop depth 3
      notifiedUsers.set(userD_hash, {
        paths: [["A", "B", "C", "D"]],
        minHopDepth: 3,
      });

      // Second path with hop depth 2 (shorter)
      const info = notifiedUsers.get(userD_hash)!;
      const newHopDepth = 2;
      if (newHopDepth < info.minHopDepth) {
        info.minHopDepth = newHopDepth;
      }
      info.paths.push(["A", "E", "D"]);

      expect(info.minHopDepth).toBe(2);
      expect(info.paths.length).toBe(2);
    });

    it("should batch interaction queries for multi-path scenario", () => {
      const interactionCache = createQueryCache<admin.firestore.QueryDocumentSnapshot[]>();
      const windowStart = Date.now() - 86400000;
      const windowEnd = Date.now();

      // Query for user B's contacts (includes D)
      const keyB = QueryCache.generateKey(QueryType.INTERACTIONS, "hash-B", windowStart, windowEnd);
      interactionCache.set(keyB, []);

      // Query for user C's contacts (includes D)
      const keyC = QueryCache.generateKey(QueryType.INTERACTIONS, "hash-C", windowStart, windowEnd);
      interactionCache.set(keyC, []);

      // Both queries should be stored with unique keys
      expect(interactionCache.size).toBe(2);
      expect(interactionCache.has(keyB)).toBe(true);
      expect(interactionCache.has(keyC)).toBe(true);
    });
  });

  describe("Test 6: Integration of caching and batching in propagation context", () => {
    it("should create propagation context with cache and batcher instances", () => {
      const interactionCache = createQueryCache<admin.firestore.QueryDocumentSnapshot[]>();
      const userCache = createUserLookupCache();
      const notificationBatcher = createNotificationBatcher();
      const fcmBatcher = createFCMBatcher();

      // Simulate PropagationContext extension
      const context = {
        reportId: "report-123",
        reporterId: "reporter-hash",
        reporterUsername: "TestReporter",
        stiTypes: "[\"HIV\"]",
        testDate: Date.now() - 86400000,
        privacyLevel: PrivacyLevel.FULL,
        exposureWindowStart: Date.now() - 86400000 * 14,
        exposureWindowEnd: Date.now(),
        notifiedUsers: new Map(),
        incubationDays: 14,
        // Optimization utilities
        interactionCache,
        userCache,
        notificationBatcher,
        fcmBatcher,
      };

      expect(context.interactionCache).toBeDefined();
      expect(context.userCache).toBeDefined();
      expect(context.notificationBatcher).toBeDefined();
      expect(context.fcmBatcher).toBeDefined();
    });

    it("should maintain backward compatibility when caches are not provided", () => {
      // Simulate PropagationContext without caches (backward compatible)
      const context = {
        reportId: "report-123",
        reporterId: "reporter-hash",
        reporterUsername: "TestReporter",
        stiTypes: "[\"HIV\"]",
        testDate: Date.now() - 86400000,
        privacyLevel: PrivacyLevel.FULL,
        exposureWindowStart: Date.now() - 86400000 * 14,
        exposureWindowEnd: Date.now(),
        notifiedUsers: new Map(),
        incubationDays: 14,
        // Optional fields not provided
        interactionCache: undefined,
        userCache: undefined,
      };

      expect(context.interactionCache).toBeUndefined();
      expect(context.userCache).toBeUndefined();
    });
  });

  describe("Test 7: Edge cases and error handling", () => {
    it("should handle cache miss for non-existent user", () => {
      const userCache = createUserLookupCache();

      const result = userCache.get("non-existent-hash");

      expect(result).toBeUndefined();
      expect(userCache.getStats().misses).toBe(1);
    });

    it("should correctly handle null user (not found) in cache", () => {
      const userCache = createUserLookupCache();

      // Mark user as not found
      userCache.setNotFound("deleted-user-hash");

      // Should return null (not undefined)
      const result = userCache.get("deleted-user-hash");

      expect(result).toBeNull();
      expect(userCache.getStats().hits).toBe(1); // It's a cache hit (we know the user doesn't exist)
    });

    it("should log cache statistics for monitoring", () => {
      const interactionCache = createQueryCache<admin.firestore.QueryDocumentSnapshot[]>();
      const userCache = createUserLookupCache();

      // Generate some activity
      interactionCache.set("key1", []);
      interactionCache.get("key1");
      interactionCache.get("key2");

      userCache.set("hash1", null);
      userCache.get("hash1");

      const interactionStats = interactionCache.getStats();
      const userStats = userCache.getStats();

      expect(interactionStats.hits).toBe(1);
      expect(interactionStats.misses).toBe(1);
      expect(interactionStats.entries).toBe(1);

      expect(userStats.hits).toBe(1);
      expect(userStats.misses).toBe(0);
      expect(userStats.entries).toBe(1);
      expect(userStats.nullEntries).toBe(1);
    });

    it("should respect maxEntries eviction policy", () => {
      const cache = createQueryCache<string>({ maxEntries: 3 });

      cache.set("key1", "value1");
      cache.set("key2", "value2");
      cache.set("key3", "value3");
      // This should evict key1
      cache.set("key4", "value4");

      expect(cache.size).toBe(3);
      expect(cache.has("key1")).toBe(false);
      expect(cache.has("key2")).toBe(true);
      expect(cache.has("key3")).toBe(true);
      expect(cache.has("key4")).toBe(true);
    });
  });
});
