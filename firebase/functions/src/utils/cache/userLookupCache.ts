/**
 * User Lookup Cache Utility
 *
 * Specialized cache for user document lookups by hashed interaction ID.
 * Designed to work with getUserByHashedInteractionId and
 * getUsersByHashedInteractionIds functions to minimize Firestore reads
 * during chain propagation.
 *
 * IMPORTANT: This cache is function-scoped and NOT shared between invocations.
 */

import { UserLookupResult } from "../queries/userQueries";

/**
 * Cache statistics for monitoring
 */
export interface UserCacheStats {
  hits: number;
  misses: number;
  entries: number;
  nullEntries: number;
}

/**
 * Options for UserLookupCache configuration
 */
export interface UserLookupCacheOptions {
  /**
   * Maximum number of entries before oldest entries are evicted.
   * Default: 500 (sufficient for most chain propagations)
   */
  maxEntries?: number;
}

/**
 * Cache entry that can hold either a user result or null (not found).
 * We distinguish between "not in cache" and "cached as not found".
 */
interface CacheEntry {
  value: UserLookupResult | null;
  timestamp: number;
}

/**
 * User lookup cache keyed by hashedInteractionId.
 *
 * Caches both successful lookups AND null results (user not found)
 * to avoid repeated queries for non-existent users.
 */
export class UserLookupCache {
  private cache: Map<string, CacheEntry>;
  private insertionOrder: string[];
  private hits: number;
  private misses: number;
  private maxEntries: number;

  constructor(options: UserLookupCacheOptions = {}) {
    this.cache = new Map<string, CacheEntry>();
    this.insertionOrder = [];
    this.hits = 0;
    this.misses = 0;
    this.maxEntries = options.maxEntries ?? 500;
  }

  /**
   * Get a user lookup result from the cache.
   *
   * @param hashedInteractionId - The hashed interaction ID to look up
   * @returns The cached UserLookupResult, null if cached as not found,
   *          or undefined if not in cache
   */
  get(hashedInteractionId: string): UserLookupResult | null | undefined {
    const entry = this.cache.get(hashedInteractionId);
    if (entry !== undefined) {
      this.hits++;
      return entry.value;
    }
    this.misses++;
    return undefined;
  }

  /**
   * Check if a hashedInteractionId is in the cache.
   *
   * Note: This returns true even if the cached value is null (not found).
   * This does NOT update hit/miss statistics.
   *
   * @param hashedInteractionId - The hashed interaction ID to check
   * @returns True if the ID is cached (including cached nulls)
   */
  has(hashedInteractionId: string): boolean {
    return this.cache.has(hashedInteractionId);
  }

  /**
   * Set a user lookup result in the cache.
   *
   * @param hashedInteractionId - The hashed interaction ID
   * @param result - The user lookup result (or null if not found)
   */
  set(hashedInteractionId: string, result: UserLookupResult | null): void {
    // If key already exists, update value without adding to insertion order
    if (this.cache.has(hashedInteractionId)) {
      this.cache.set(hashedInteractionId, {
        value: result,
        timestamp: Date.now(),
      });
      return;
    }

    // Evict oldest entries if at capacity
    while (this.cache.size >= this.maxEntries && this.insertionOrder.length > 0) {
      const oldestKey = this.insertionOrder.shift();
      if (oldestKey) {
        this.cache.delete(oldestKey);
      }
    }

    // Add new entry
    this.cache.set(hashedInteractionId, {
      value: result,
      timestamp: Date.now(),
    });
    this.insertionOrder.push(hashedInteractionId);
  }

  /**
   * Populate the cache with results from a batch lookup.
   *
   * This is designed to work with getUsersByHashedInteractionIds.
   * Note: This only populates entries that were found. Use setNotFound()
   * or explicitly set null for IDs that were queried but not found.
   *
   * @param results - Map of hashedInteractionId to UserLookupResult
   */
  populateFromBatch(results: Map<string, UserLookupResult>): void {
    for (const [hashedId, result] of results) {
      this.set(hashedId, result);
    }
  }

  /**
   * Mark a hashedInteractionId as not found in the cache.
   *
   * @param hashedInteractionId - The hashed interaction ID that was not found
   */
  setNotFound(hashedInteractionId: string): void {
    this.set(hashedInteractionId, null);
  }

  /**
   * Mark multiple hashedInteractionIds as not found.
   *
   * Useful after a batch query to cache the IDs that weren't found.
   *
   * @param hashedInteractionIds - Array of IDs that were not found
   */
  setMultipleNotFound(hashedInteractionIds: string[]): void {
    for (const id of hashedInteractionIds) {
      this.setNotFound(id);
    }
  }

  /**
   * Delete a specific entry from the cache.
   *
   * @param hashedInteractionId - The hashed interaction ID to delete
   * @returns True if the entry was deleted
   */
  delete(hashedInteractionId: string): boolean {
    const deleted = this.cache.delete(hashedInteractionId);
    if (deleted) {
      const index = this.insertionOrder.indexOf(hashedInteractionId);
      if (index > -1) {
        this.insertionOrder.splice(index, 1);
      }
    }
    return deleted;
  }

  /**
   * Clear all entries from the cache.
   */
  clear(): void {
    this.cache.clear();
    this.insertionOrder = [];
  }

  /**
   * Get IDs that are not yet in the cache from a list.
   *
   * Useful for determining which IDs need to be fetched from Firestore.
   *
   * @param hashedInteractionIds - Array of IDs to check
   * @returns Array of IDs that are not in the cache
   */
  getUncachedIds(hashedInteractionIds: string[]): string[] {
    return hashedInteractionIds.filter(id => !this.cache.has(id));
  }

  /**
   * Get current cache statistics.
   *
   * @returns Object containing hits, misses, entries, and null entries count
   */
  getStats(): UserCacheStats {
    let nullEntries = 0;
    for (const entry of this.cache.values()) {
      if (entry.value === null) {
        nullEntries++;
      }
    }

    return {
      hits: this.hits,
      misses: this.misses,
      entries: this.cache.size,
      nullEntries,
    };
  }

  /**
   * Get the current number of entries in the cache.
   */
  get size(): number {
    return this.cache.size;
  }

  /**
   * Calculate the cache hit rate.
   *
   * @returns Hit rate as a decimal (0-1), or 0 if no queries yet
   */
  getHitRate(): number {
    const total = this.hits + this.misses;
    return total > 0 ? this.hits / total : 0;
  }

  /**
   * Log cache statistics for debugging/monitoring.
   *
   * @param prefix - Optional prefix for the log message
   */
  logStats(prefix: string = "UserLookupCache"): void {
    const stats = this.getStats();
    const hitRate = (this.getHitRate() * 100).toFixed(1);
    console.log(
      `${prefix} stats: ${stats.hits} hits, ${stats.misses} misses ` +
      `(${hitRate}% hit rate), ${stats.entries} entries (${stats.nullEntries} nulls)`
    );
  }
}

/**
 * Factory function to create a new UserLookupCache instance.
 *
 * @param options - Optional configuration options
 * @returns A new UserLookupCache instance
 */
export function createUserLookupCache(options?: UserLookupCacheOptions): UserLookupCache {
  return new UserLookupCache(options);
}
