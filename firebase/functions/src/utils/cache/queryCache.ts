/**
 * Query Cache Utility
 *
 * Implements function-scoped Map-based caching for Firestore query results.
 * This cache is designed to be created at the start of a Cloud Function
 * invocation and used throughout that single invocation to avoid duplicate
 * queries for the same data.
 *
 * IMPORTANT: This cache is NOT shared between function invocations.
 * Each Cloud Function creates its own cache instance that is garbage
 * collected when the function completes.
 */

/**
 * Cache statistics for monitoring and logging
 */
export interface CacheStats {
  hits: number;
  misses: number;
  entries: number;
}

/**
 * Query types supported by the cache
 */
export enum QueryType {
  INTERACTIONS = "interactions",
  NOTIFICATIONS = "notifications",
  USERS = "users",
}

/**
 * Options for QueryCache configuration
 */
export interface QueryCacheOptions {
  /**
   * Maximum number of entries before oldest entries are evicted.
   * Default: 1000 (sufficient for most chain propagations)
   */
  maxEntries?: number;
}

/**
 * Generic query result cache for function-scoped caching.
 *
 * Cache key format: `${queryType}:${partnerId}:${windowStart}:${windowEnd}`
 *
 * @template T - The type of cached query results
 */
export class QueryCache<T> {
  private cache: Map<string, T>;
  private insertionOrder: string[];
  private hits: number;
  private misses: number;
  private maxEntries: number;

  constructor(options: QueryCacheOptions = {}) {
    this.cache = new Map<string, T>();
    this.insertionOrder = [];
    this.hits = 0;
    this.misses = 0;
    this.maxEntries = options.maxEntries ?? 1000;
  }

  /**
   * Generate a cache key for an interaction query.
   *
   * @param queryType - Type of query (interactions, notifications, etc.)
   * @param partnerId - The partner ID being queried
   * @param windowStart - Start of the query window
   * @param windowEnd - End of the query window
   * @returns Formatted cache key string
   */
  static generateKey(
    queryType: QueryType | string,
    partnerId: string,
    windowStart: number,
    windowEnd: number,
  ): string {
    return `${queryType}:${partnerId}:${windowStart}:${windowEnd}`;
  }

  /**
   * Get a value from the cache.
   *
   * @param key - The cache key
   * @returns The cached value or undefined if not found
   */
  get(key: string): T | undefined {
    const value = this.cache.get(key);
    if (value !== undefined) {
      this.hits++;
      return value;
    }
    this.misses++;
    return undefined;
  }

  /**
   * Set a value in the cache.
   *
   * If the cache exceeds maxEntries, the oldest entry is evicted.
   *
   * @param key - The cache key
   * @param value - The value to cache
   */
  set(key: string, value: T): void {
    // If key already exists, update value without adding to insertion order
    if (this.cache.has(key)) {
      this.cache.set(key, value);
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
    this.cache.set(key, value);
    this.insertionOrder.push(key);
  }

  /**
   * Check if a key exists in the cache.
   *
   * Note: This does NOT update hit/miss statistics.
   *
   * @param key - The cache key
   * @returns True if the key exists
   */
  has(key: string): boolean {
    return this.cache.has(key);
  }

  /**
   * Delete a specific entry from the cache.
   *
   * @param key - The cache key to delete
   * @returns True if the entry was deleted
   */
  delete(key: string): boolean {
    const deleted = this.cache.delete(key);
    if (deleted) {
      const index = this.insertionOrder.indexOf(key);
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
    // Note: We don't reset stats on clear - they track lifetime stats
  }

  /**
   * Get current cache statistics.
   *
   * @returns Object containing hits, misses, and entry count
   */
  getStats(): CacheStats {
    return {
      hits: this.hits,
      misses: this.misses,
      entries: this.cache.size,
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
  logStats(prefix: string = "QueryCache"): void {
    const stats = this.getStats();
    const hitRate = (this.getHitRate() * 100).toFixed(1);
    console.log(
      `${prefix} stats: ${stats.hits} hits, ${stats.misses} misses ` +
      `(${hitRate}% hit rate), ${stats.entries} entries`
    );
  }
}

/**
 * Factory function to create a new QueryCache instance.
 *
 * @template T - The type of cached query results
 * @param options - Optional configuration options
 * @returns A new QueryCache instance
 */
export function createQueryCache<T>(options?: QueryCacheOptions): QueryCache<T> {
  return new QueryCache<T>(options);
}
