/**
 * Cache utilities for optimizing Firestore queries
 *
 * These utilities implement function-scoped caching to reduce
 * duplicate Firestore reads within a single Cloud Function invocation.
 */

export {
  QueryCache,
  QueryType,
  CacheStats,
  QueryCacheOptions,
  createQueryCache,
} from "./queryCache";

export {
  UserLookupCache,
  UserCacheStats,
  UserLookupCacheOptions,
  createUserLookupCache,
} from "./userLookupCache";
