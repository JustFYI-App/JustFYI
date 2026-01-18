/**
 * Query Cache Utility Tests
 * Task 2.1: Tests for caching utilities
 *
 * Tests:
 * 1. Test cache hit/miss behavior for interaction queries
 * 2. Test cache key generation with different window parameters
 * 3. Test user lookup cache with multiple hashedInteractionIds
 * 4. Test cache invalidation and memory bounds
 */

import {
  QueryCache,
  QueryType,
  createQueryCache,
} from "../cache/queryCache";

import {
  createUserLookupCache,
} from "../cache/userLookupCache";

import { UserLookupResult } from "../queries/userQueries";

describe("Query Cache Utilities", () => {
  describe("Test 1: Cache hit/miss behavior for interaction queries", () => {
    it("should return undefined for cache miss and track miss count", () => {
      const cache = createQueryCache<string[]>();

      const result = cache.get("nonexistent-key");

      expect(result).toBeUndefined();
      expect(cache.getStats().misses).toBe(1);
      expect(cache.getStats().hits).toBe(0);
    });

    it("should return cached value for cache hit and track hit count", () => {
      const cache = createQueryCache<string[]>();
      const testData = ["interaction1", "interaction2"];
      const key = "test-key";

      cache.set(key, testData);
      const result = cache.get(key);

      expect(result).toEqual(testData);
      expect(cache.getStats().hits).toBe(1);
      expect(cache.getStats().misses).toBe(0);
    });

    it("should correctly calculate hit rate", () => {
      const cache = createQueryCache<number>();

      // 1 miss
      cache.get("miss1");
      // Set and get (1 hit)
      cache.set("key1", 100);
      cache.get("key1");
      // Another hit
      cache.get("key1");
      // Another miss
      cache.get("miss2");

      // 2 hits, 2 misses = 50% hit rate
      expect(cache.getHitRate()).toBe(0.5);
      expect(cache.getStats().hits).toBe(2);
      expect(cache.getStats().misses).toBe(2);
    });

    it("should track entries correctly", () => {
      const cache = createQueryCache<string>();

      expect(cache.size).toBe(0);

      cache.set("key1", "value1");
      cache.set("key2", "value2");
      cache.set("key3", "value3");

      expect(cache.size).toBe(3);
      expect(cache.getStats().entries).toBe(3);
    });
  });

  describe("Test 2: Cache key generation with different window parameters", () => {
    it("should generate unique keys for different query types", () => {
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
      const key3 = QueryCache.generateKey(
        QueryType.INTERACTIONS,
        partnerId,
        500000, // Different start
        2000000
      );

      expect(key1).not.toBe(key2);
      expect(key1).not.toBe(key3);
      expect(key2).not.toBe(key3);
    });

    it("should support custom query type strings", () => {
      const key = QueryCache.generateKey(
        "custom-query",
        "partner",
        1000,
        2000
      );

      expect(key).toBe("custom-query:partner:1000:2000");
    });
  });

  describe("Test 3: User lookup cache with multiple hashedInteractionIds", () => {
    const createMockUserResult = (id: string): UserLookupResult => ({
      hashedNotificationId: `notification-${id}`,
      hashedInteractionId: id,
      fcmToken: `token-${id}`,
      docRef: { update: jest.fn(), set: jest.fn() } as unknown as UserLookupResult["docRef"],
    });

    it("should cache and retrieve multiple user lookup results", () => {
      const cache = createUserLookupCache();

      const user1 = createMockUserResult("hash1");
      const user2 = createMockUserResult("hash2");
      const user3 = createMockUserResult("hash3");

      cache.set("hash1", user1);
      cache.set("hash2", user2);
      cache.set("hash3", user3);

      expect(cache.get("hash1")).toEqual(user1);
      expect(cache.get("hash2")).toEqual(user2);
      expect(cache.get("hash3")).toEqual(user3);
      expect(cache.size).toBe(3);
    });

    it("should cache null results for users not found", () => {
      const cache = createUserLookupCache();

      cache.setNotFound("nonexistent-hash");

      // Should return null (not undefined) for cached not-found
      expect(cache.get("nonexistent-hash")).toBeNull();
      expect(cache.has("nonexistent-hash")).toBe(true);
      expect(cache.getStats().nullEntries).toBe(1);
    });

    it("should populate cache from batch lookup results", () => {
      const cache = createUserLookupCache();

      const batchResults = new Map<string, UserLookupResult>();
      batchResults.set("hash1", createMockUserResult("hash1"));
      batchResults.set("hash2", createMockUserResult("hash2"));
      batchResults.set("hash3", createMockUserResult("hash3"));

      cache.populateFromBatch(batchResults);

      expect(cache.size).toBe(3);
      expect(cache.get("hash1")?.hashedInteractionId).toBe("hash1");
      expect(cache.get("hash2")?.hashedInteractionId).toBe("hash2");
      expect(cache.get("hash3")?.hashedInteractionId).toBe("hash3");
    });

    it("should identify uncached IDs for batch fetching", () => {
      const cache = createUserLookupCache();

      cache.set("hash1", createMockUserResult("hash1"));
      cache.set("hash2", createMockUserResult("hash2"));
      cache.setNotFound("hash3"); // null is still cached

      const idsToCheck = ["hash1", "hash2", "hash3", "hash4", "hash5"];
      const uncached = cache.getUncachedIds(idsToCheck);

      expect(uncached).toEqual(["hash4", "hash5"]);
    });

    it("should handle setMultipleNotFound correctly", () => {
      const cache = createUserLookupCache();

      cache.setMultipleNotFound(["hash1", "hash2", "hash3"]);

      expect(cache.get("hash1")).toBeNull();
      expect(cache.get("hash2")).toBeNull();
      expect(cache.get("hash3")).toBeNull();
      expect(cache.getStats().nullEntries).toBe(3);
    });
  });

  describe("Test 4: Cache invalidation and memory bounds", () => {
    it("should evict oldest entries when maxEntries is exceeded", () => {
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

    it("should clear all entries on clear()", () => {
      const cache = createQueryCache<string>();

      cache.set("key1", "value1");
      cache.set("key2", "value2");
      cache.get("key1"); // hit
      cache.get("nonexistent"); // miss

      cache.clear();

      expect(cache.size).toBe(0);
      expect(cache.has("key1")).toBe(false);
      // Stats are preserved after clear
      expect(cache.getStats().hits).toBe(1);
      expect(cache.getStats().misses).toBe(1);
    });

    it("should delete specific entries", () => {
      const cache = createQueryCache<string>();

      cache.set("key1", "value1");
      cache.set("key2", "value2");
      cache.set("key3", "value3");

      const deleted = cache.delete("key2");

      expect(deleted).toBe(true);
      expect(cache.size).toBe(2);
      expect(cache.has("key2")).toBe(false);
      expect(cache.has("key1")).toBe(true);
      expect(cache.has("key3")).toBe(true);
    });

    it("should return false when deleting non-existent key", () => {
      const cache = createQueryCache<string>();

      const deleted = cache.delete("nonexistent");

      expect(deleted).toBe(false);
    });

    it("should update value without adding duplicate when key exists", () => {
      const cache = createQueryCache<string>({ maxEntries: 3 });

      cache.set("key1", "value1");
      cache.set("key2", "value2");
      cache.set("key1", "updated-value1"); // Update existing key

      expect(cache.size).toBe(2);
      expect(cache.get("key1")).toBe("updated-value1");
    });

    it("should respect maxEntries in UserLookupCache", () => {
      const cache = createUserLookupCache({ maxEntries: 2 });

      const createMockUser = (id: string): UserLookupResult => ({
        hashedNotificationId: `notification-${id}`,
        hashedInteractionId: id,
        fcmToken: `token-${id}`,
        docRef: { update: jest.fn(), set: jest.fn() } as unknown as UserLookupResult["docRef"],
      });

      cache.set("hash1", createMockUser("hash1"));
      cache.set("hash2", createMockUser("hash2"));
      // This should evict hash1
      cache.set("hash3", createMockUser("hash3"));

      expect(cache.size).toBe(2);
      expect(cache.has("hash1")).toBe(false);
      expect(cache.has("hash2")).toBe(true);
      expect(cache.has("hash3")).toBe(true);
    });

    it("should log stats without errors", () => {
      const cache = createQueryCache<string>();
      const userCache = createUserLookupCache();

      // Set up some data
      cache.set("key1", "value1");
      cache.get("key1");
      cache.get("miss");

      userCache.set("hash1", null);

      // These should not throw
      expect(() => cache.logStats("TestQueryCache")).not.toThrow();
      expect(() => userCache.logStats("TestUserCache")).not.toThrow();
    });
  });
});
