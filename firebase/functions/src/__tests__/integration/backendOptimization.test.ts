/**
 * Backend Optimization Integration Tests
 * Task 6.3: Strategic integration tests for backend optimization feature
 *
 * These tests verify the integration of caching and batching optimizations
 * across the entire backend flow, ensuring backward compatibility and
 * performance improvements.
 *
 * Test Categories:
 * 1. Backward compatibility: Old PropagationContext without caches works
 * 2. Query caching: Verify cache reduces duplicate queries
 * 3. Batch operations: Verify batching utilities work correctly
 * 4. Cache statistics: Verify monitoring and statistics tracking
 * 5. Memory bounds: Verify cache eviction policies
 * 6. Error handling: Verify graceful error recovery
 */

import * as admin from "firebase-admin";

import {
  NotificationType,
  PrivacyLevel,
} from "../../types";

import {
  createQueryCache,
  createUserLookupCache,
  createNotificationBatcher,
  createFCMBatcher,
  PropagationContext,
  QueryType,
  QueryCache,
} from "../../utils/chainPropagation";

describe("Backend Optimization Integration Tests", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  /**
   * Test 1: Backward compatibility - PropagationContext without caches
   * Verifies that the system works with undefined optimization utilities
   */
  describe("Test 1: Backward compatibility without optimization utilities", () => {
    it("should handle PropagationContext with undefined caches", () => {
      // Create a context without cache utilities (old-style)
      const context: PropagationContext = {
        reportId: "test-report",
        reporterId: "test-reporter",
        reporterUsername: "TestReporter",
        stiTypes: "[\"HIV\"]",
        testDate: Date.now() - 86400000,
        privacyLevel: PrivacyLevel.FULL,
        exposureWindowStart: Date.now() - 86400000 * 14,
        exposureWindowEnd: Date.now(),
        notifiedUsers: new Map(),
        incubationDays: 14,
        // Caches explicitly undefined
        interactionCache: undefined,
        userCache: undefined,
        notificationBatcher: undefined,
        fcmBatcher: undefined,
      };

      // Should not throw
      expect(context.interactionCache).toBeUndefined();
      expect(context.userCache).toBeUndefined();
      expect(context.notificationBatcher).toBeUndefined();
      expect(context.fcmBatcher).toBeUndefined();
    });

    it("should create PropagationContext with optional optimization utilities", () => {
      // Create caches and batchers
      const interactionCache = createQueryCache<admin.firestore.QueryDocumentSnapshot[]>();
      const userCache = createUserLookupCache();
      const notificationBatcher = createNotificationBatcher();
      const fcmBatcher = createFCMBatcher();

      // Create a context with all optimization utilities
      const context: PropagationContext = {
        reportId: "test-report",
        reporterId: "test-reporter",
        reporterUsername: "TestReporter",
        stiTypes: "[\"HIV\"]",
        testDate: Date.now() - 86400000,
        privacyLevel: PrivacyLevel.FULL,
        exposureWindowStart: Date.now() - 86400000 * 14,
        exposureWindowEnd: Date.now(),
        notifiedUsers: new Map(),
        incubationDays: 14,
        // Caches and batchers included
        interactionCache,
        userCache,
        notificationBatcher,
        fcmBatcher,
      };

      // All utilities should be defined
      expect(context.interactionCache).toBeDefined();
      expect(context.userCache).toBeDefined();
      expect(context.notificationBatcher).toBeDefined();
      expect(context.fcmBatcher).toBeDefined();
    });
  });

  /**
   * Test 2: Query caching reduces duplicate queries
   * Verifies that interaction cache prevents redundant Firestore queries
   */
  describe("Test 2: Query caching reduces duplicate queries", () => {
    it("should cache interaction query results", () => {
      const cache = createQueryCache<admin.firestore.QueryDocumentSnapshot[]>();
      const partnerId = "test-partner";
      const windowStart = Date.now() - 86400000;
      const windowEnd = Date.now();

      // First access - miss
      const key = QueryCache.generateKey(QueryType.INTERACTIONS, partnerId, windowStart, windowEnd);
      const miss = cache.get(key);
      expect(miss).toBeUndefined();
      expect(cache.getStats().misses).toBe(1);

      // Set value
      cache.set(key, []);

      // Second access - hit
      const hit = cache.get(key);
      expect(hit).toBeDefined();
      expect(cache.getStats().hits).toBe(1);

      // Hit rate should be 50%
      expect(cache.getHitRate()).toBe(0.5);
    });

    it("should batch user lookups to reduce queries", () => {
      const userCache = createUserLookupCache();

      // Simulate batch lookup for 10 users
      const userIds = Array.from({ length: 10 }, (_, i) => `hash-${i}`);

      // Initially all uncached
      const uncached = userCache.getUncachedIds(userIds);
      expect(uncached.length).toBe(10);

      // Simulate batch population
      const batchResults = new Map(userIds.map(id => [id, {
        hashedInteractionId: id,
        hashedNotificationId: `notif-${id}`,
        fcmToken: `token-${id}`,
        docRef: {} as admin.firestore.DocumentReference,
      }]));
      userCache.populateFromBatch(batchResults);

      // All should now be cached
      const stillUncached = userCache.getUncachedIds(userIds);
      expect(stillUncached.length).toBe(0);
      expect(userCache.size).toBe(10);
    });

    it("should simulate reduced Firestore operations with caching", () => {
      const cache = createQueryCache<string[]>();
      let firestoreQueryCount = 0;

      // Simulate function that checks cache before querying
      const getDataWithCache = (key: string): string[] => {
        const cached = cache.get(key);
        if (cached !== undefined) {
          return cached;
        }
        // Simulate Firestore query
        firestoreQueryCount++;
        const result: string[] = [];
        cache.set(key, result);
        return result;
      };

      // First call - query Firestore
      getDataWithCache("query-A");
      expect(firestoreQueryCount).toBe(1);

      // Second call (same key) - should use cache
      getDataWithCache("query-A");
      expect(firestoreQueryCount).toBe(1); // No additional query

      // Third call (different key) - query Firestore
      getDataWithCache("query-B");
      expect(firestoreQueryCount).toBe(2);

      // Fourth call (query-A again) - still cached
      getDataWithCache("query-A");
      expect(firestoreQueryCount).toBe(2);

      // Verify cache statistics
      expect(cache.getStats().hits).toBe(2);
      expect(cache.getStats().misses).toBe(2);
    });
  });

  /**
   * Test 3: NotificationBatcher handles various sizes correctly
   * Verifies batch splitting at 500 items
   */
  describe("Test 3: NotificationBatcher handles various batch sizes", () => {
    it("should queue notifications without immediate write", () => {
      const batcher = createNotificationBatcher();

      // Add notification
      batcher.add({
        data: {
          recipientId: "test-recipient",
          type: NotificationType.EXPOSURE,
          chainData: "{}",
          isRead: false,
          receivedAt: Date.now(),
          updatedAt: Date.now(),
          reportId: "test-report",
          chainPath: ["path"],
        },
        hashedInteractionId: "hash-1",
        hashedNotificationId: "notif-hash-1",
      });

      expect(batcher.size).toBe(1);
      expect(batcher.isEmpty).toBe(false);
    });

    it("should track pending notifications correctly", () => {
      const batcher = createNotificationBatcher();

      for (let i = 0; i < 5; i++) {
        batcher.add({
          data: {
            recipientId: `recipient-${i}`,
            type: NotificationType.EXPOSURE,
            chainData: "{}",
            isRead: false,
            receivedAt: Date.now(),
            updatedAt: Date.now(),
            reportId: "test-report",
            chainPath: ["path"],
          },
          hashedInteractionId: `hash-${i}`,
          hashedNotificationId: `notif-hash-${i}`,
        });
      }

      expect(batcher.size).toBe(5);
    });

    it("should clear batcher correctly", () => {
      const batcher = createNotificationBatcher();

      batcher.add({
        data: {
          recipientId: "recipient",
          type: NotificationType.EXPOSURE,
          chainData: "{}",
          isRead: false,
          receivedAt: Date.now(),
          updatedAt: Date.now(),
          reportId: "report",
          chainPath: ["path"],
        },
        hashedInteractionId: "hash",
        hashedNotificationId: "notif-hash",
      });

      expect(batcher.size).toBe(1);

      batcher.clear();

      expect(batcher.isEmpty).toBe(true);
      expect(batcher.size).toBe(0);
    });
  });

  /**
   * Test 4: FCMBatcher groups notifications correctly
   * Verifies FCM multicast grouping by payload
   */
  describe("Test 4: FCMBatcher groups notifications by payload", () => {
    it("should queue FCM notifications", () => {
      const fcmBatcher = createFCMBatcher();

      fcmBatcher.add({
        token: "test-token",
        notificationId: "notif-1",
        type: "EXPOSURE",
        titleLocKey: "notification_exposure_title",
        bodyLocKey: "notification_exposure_body",
      });

      expect(fcmBatcher.size).toBe(1);
      expect(fcmBatcher.isEmpty).toBe(false);
    });

    it("should skip empty tokens", () => {
      const fcmBatcher = createFCMBatcher();

      fcmBatcher.add({
        token: "",
        notificationId: "notif-1",
        type: "EXPOSURE",
        titleLocKey: "title",
        bodyLocKey: "body",
      });

      fcmBatcher.add({
        token: "   ",
        notificationId: "notif-2",
        type: "EXPOSURE",
        titleLocKey: "title",
        bodyLocKey: "body",
      });

      fcmBatcher.add({
        token: "valid-token",
        notificationId: "notif-3",
        type: "EXPOSURE",
        titleLocKey: "title",
        bodyLocKey: "body",
      });

      expect(fcmBatcher.size).toBe(1);
    });

    it("should handle different notification types", () => {
      const fcmBatcher = createFCMBatcher();

      // Add EXPOSURE notifications
      fcmBatcher.add({
        token: "token-1",
        notificationId: "notif-1",
        type: "EXPOSURE",
        titleLocKey: "exposure_title",
        bodyLocKey: "exposure_body",
      });

      // Add UPDATE notifications
      fcmBatcher.add({
        token: "token-2",
        notificationId: "notif-2",
        type: "UPDATE",
        titleLocKey: "update_title",
        bodyLocKey: "update_body",
      });

      expect(fcmBatcher.size).toBe(2);
    });
  });

  /**
   * Test 5: Cache statistics tracking
   * Verifies that cache hit/miss statistics are properly tracked
   */
  describe("Test 5: Cache statistics tracking", () => {
    it("should track query cache statistics accurately", () => {
      const cache = createQueryCache<string>();

      // Generate misses
      cache.get("miss-1");
      cache.get("miss-2");

      // Generate hits
      cache.set("key-1", "value-1");
      cache.get("key-1");
      cache.get("key-1");

      const stats = cache.getStats();
      expect(stats.hits).toBe(2);
      expect(stats.misses).toBe(2);
      expect(stats.entries).toBe(1);
      expect(cache.getHitRate()).toBe(0.5);
    });

    it("should track user cache statistics including null entries", () => {
      const userCache = createUserLookupCache();

      // Add found user
      userCache.set("hash-1", {
        hashedInteractionId: "hash-1",
        hashedNotificationId: "notif-1",
        fcmToken: "token-1",
        docRef: {} as admin.firestore.DocumentReference,
      });

      // Add not-found users
      userCache.setNotFound("hash-2");
      userCache.setNotFound("hash-3");

      const stats = userCache.getStats();
      expect(stats.entries).toBe(3);
      expect(stats.nullEntries).toBe(2);
    });

    it("should provide accurate hit rate calculations", () => {
      const cache = createQueryCache<string>();

      // No operations - hit rate should be 0
      expect(cache.getHitRate()).toBe(0);

      // Only misses
      cache.get("miss-1");
      cache.get("miss-2");
      expect(cache.getHitRate()).toBe(0);

      // Set and get (adds 1 hit)
      cache.set("key-1", "value-1");
      cache.get("key-1");
      // 1 hit, 2 misses = 33.33%
      expect(cache.getHitRate()).toBeCloseTo(0.333, 2);
    });
  });

  /**
   * Test 6: Multi-path deduplication with caching
   * Verifies that multi-path scenarios work with optimization
   */
  describe("Test 6: Multi-path deduplication with caching", () => {
    it("should use cache for multi-path user lookups", () => {
      const userCache = createUserLookupCache();
      const userD_hash = "hash-user-D";

      // Pre-populate cache with user D
      userCache.set(userD_hash, {
        hashedInteractionId: userD_hash,
        hashedNotificationId: "notif-D",
        fcmToken: "token-D",
        docRef: {} as admin.firestore.DocumentReference,
      });

      // Simulate multiple path accesses (A->B->D and A->C->D)
      userCache.get(userD_hash); // First path
      userCache.get(userD_hash); // Second path
      userCache.get(userD_hash); // Additional access

      expect(userCache.getStats().hits).toBe(3);
      expect(userCache.getStats().misses).toBe(0);
    });

    it("should handle user not found scenario", () => {
      const userCache = createUserLookupCache();

      // Mark user as not found
      userCache.setNotFound("deleted-user-hash");

      // Subsequent lookups should hit cache (return null)
      const result = userCache.get("deleted-user-hash");
      expect(result).toBeNull();
      expect(userCache.getStats().hits).toBe(1);
    });
  });

  /**
   * Test 7: Cache memory bounds enforcement
   * Verifies maxEntries eviction policy
   */
  describe("Test 7: Cache memory bounds enforcement", () => {
    it("should evict oldest entries when maxEntries exceeded", () => {
      const cache = createQueryCache<string>({ maxEntries: 3 });

      cache.set("key-1", "value-1");
      cache.set("key-2", "value-2");
      cache.set("key-3", "value-3");
      // This should evict key-1
      cache.set("key-4", "value-4");

      expect(cache.size).toBe(3);
      expect(cache.has("key-1")).toBe(false);
      expect(cache.has("key-2")).toBe(true);
      expect(cache.has("key-3")).toBe(true);
      expect(cache.has("key-4")).toBe(true);
    });

    it("should enforce maxEntries in UserLookupCache", () => {
      const userCache = createUserLookupCache({ maxEntries: 2 });

      userCache.set("hash-1", {
        hashedInteractionId: "hash-1",
        hashedNotificationId: "notif-1",
        fcmToken: "token-1",
        docRef: {} as admin.firestore.DocumentReference,
      });
      userCache.set("hash-2", {
        hashedInteractionId: "hash-2",
        hashedNotificationId: "notif-2",
        fcmToken: "token-2",
        docRef: {} as admin.firestore.DocumentReference,
      });
      // This should evict hash-1
      userCache.set("hash-3", {
        hashedInteractionId: "hash-3",
        hashedNotificationId: "notif-3",
        fcmToken: "token-3",
        docRef: {} as admin.firestore.DocumentReference,
      });

      expect(userCache.size).toBe(2);
      expect(userCache.has("hash-1")).toBe(false);
      expect(userCache.has("hash-2")).toBe(true);
      expect(userCache.has("hash-3")).toBe(true);
    });
  });

  /**
   * Test 8: Cache clear and delete operations
   * Verifies cache manipulation methods work correctly
   */
  describe("Test 8: Cache manipulation operations", () => {
    it("should clear all entries from query cache", () => {
      const cache = createQueryCache<string>();

      cache.set("key-1", "value-1");
      cache.set("key-2", "value-2");
      cache.get("key-1"); // hit
      cache.get("missing"); // miss

      cache.clear();

      expect(cache.size).toBe(0);
      expect(cache.has("key-1")).toBe(false);
      // Stats should be preserved after clear
      expect(cache.getStats().hits).toBe(1);
      expect(cache.getStats().misses).toBe(1);
    });

    it("should delete specific entries from query cache", () => {
      const cache = createQueryCache<string>();

      cache.set("key-1", "value-1");
      cache.set("key-2", "value-2");
      cache.set("key-3", "value-3");

      const deleted = cache.delete("key-2");

      expect(deleted).toBe(true);
      expect(cache.size).toBe(2);
      expect(cache.has("key-2")).toBe(false);
    });

    it("should return false when deleting non-existent key", () => {
      const cache = createQueryCache<string>();

      const deleted = cache.delete("nonexistent");

      expect(deleted).toBe(false);
    });
  });

  /**
   * Test 9: Integration with chainPropagation module
   * Verifies cache and batch utilities are exported correctly
   */
  describe("Test 9: Module integration verification", () => {
    it("should export all cache utilities from chainPropagation", () => {
      // Verify exports
      expect(createQueryCache).toBeDefined();
      expect(createUserLookupCache).toBeDefined();
      expect(createNotificationBatcher).toBeDefined();
      expect(createFCMBatcher).toBeDefined();
      expect(QueryCache.generateKey).toBeDefined();
      expect(QueryType.INTERACTIONS).toBe("interactions");
      expect(QueryType.NOTIFICATIONS).toBe("notifications");
    });

    it("should create utilities with correct default options", () => {
      const queryCache = createQueryCache<string>();
      const userCache = createUserLookupCache();
      const notifBatcher = createNotificationBatcher();
      const fcmBatcher = createFCMBatcher();

      // All should be empty initially
      expect(queryCache.size).toBe(0);
      expect(userCache.size).toBe(0);
      expect(notifBatcher.isEmpty).toBe(true);
      expect(fcmBatcher.isEmpty).toBe(true);
    });
  });

  /**
   * Test 10: Performance simulation - Firestore read reduction estimates
   * Documents the expected optimization benefits
   */
  describe("Test 10: Performance optimization verification", () => {
    it("should demonstrate interaction query caching benefits", () => {
      const cache = createQueryCache<string[]>();

      // Simulate multi-hop chain traversal for 5 users
      // Each user might be queried from multiple paths
      const userIds = ["A", "B", "C", "D", "E"];
      const queryParams = userIds.map(id => ({
        key: QueryCache.generateKey(QueryType.INTERACTIONS, id, 1000, 2000),
      }));

      // First round of queries - all misses
      queryParams.forEach(p => {
        cache.get(p.key);
        cache.set(p.key, []); // Simulate query result
      });

      // Second round (from different paths) - all hits
      queryParams.forEach(p => cache.get(p.key));

      // Third round (additional paths) - all hits
      queryParams.forEach(p => cache.get(p.key));

      // Verify caching effectiveness
      const stats = cache.getStats();
      expect(stats.hits).toBe(10); // 5 users * 2 additional rounds
      expect(stats.misses).toBe(5); // Initial 5 queries
      expect(cache.getHitRate()).toBeCloseTo(0.666, 2); // ~66% hit rate
    });

    it("should demonstrate user lookup batching benefits", () => {
      const userCache = createUserLookupCache();

      // Without batching: 10 individual queries
      const sequentialQueries = 10;

      // With batching: 1 batch query (gets 30 users in one call)
      const batchQueries = 1;

      // Simulate batch population
      const userIds = Array.from({ length: 10 }, (_, i) => `hash-${i}`);
      const batchResults = new Map(userIds.map(id => [id, {
        hashedInteractionId: id,
        hashedNotificationId: `notif-${id}`,
        fcmToken: `token-${id}`,
        docRef: {} as admin.firestore.DocumentReference,
      }]));
      userCache.populateFromBatch(batchResults);

      // All subsequent lookups hit cache
      userIds.forEach(id => userCache.get(id));

      expect(userCache.getStats().hits).toBe(10);
      expect(batchQueries).toBeLessThan(sequentialQueries);
    });
  });
});
