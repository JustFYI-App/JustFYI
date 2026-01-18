/**
 * Exposure window calculation utilities
 *
 * These utilities handle the rolling window model for chain propagation,
 * ensuring epidemiologically accurate exposure tracking through the chain.
 *
 * CACHING SUPPORT (Task 4.2):
 * The getInteractionsAsPartner function supports optional caching to reduce
 * duplicate Firestore queries during chain propagation. When the same user's
 * contacts are queried multiple times (e.g., via different paths in a multi-path
 * scenario), the cache prevents redundant Firestore reads.
 */

import * as admin from "firebase-admin";
import { getDb } from "../database";
import { CONSTANTS } from "../../types";
import { QueryCache, QueryType } from "../cache/queryCache";

/**
 * Type alias for interaction document arrays (used in caching)
 */
export type InteractionDocArray = admin.firestore.QueryDocumentSnapshot[];

/**
 * Calculate the 180-day retention boundary
 *
 * @returns Timestamp for the start of the retention window
 */
export function getRetentionBoundary(): number {
  const now = Date.now();
  const msPerDay = 24 * 60 * 60 * 1000;
  return now - CONSTANTS.RETENTION_DAYS * msPerDay;
}

/**
 * Calculate rolling window for a specific hop based on the interaction date.
 *
 * @param interactionDate - The date of the interaction at this hop
 * @param incubationDays - The STI incubation period in days
 * @returns Object containing windowStart and windowEnd for this hop
 */
export function calculateRollingWindow(
  interactionDate: number,
  incubationDays: number,
): { windowStart: number; windowEnd: number } {
  const msPerDay = 24 * 60 * 60 * 1000;
  const now = Date.now();
  const retentionBoundary = getRetentionBoundary();

  // Window starts from interaction date minus incubation period
  // (looking back from when this contact was exposed)
  const windowStart = Math.max(
    interactionDate - incubationDays * msPerDay,
    retentionBoundary,
  );

  // Window ends at interaction date plus incubation period
  // (looking forward from when this contact was exposed)
  // But cannot exceed current date
  const windowEnd = Math.min(
    interactionDate + incubationDays * msPerDay,
    now,
  );

  return { windowStart, windowEnd };
}

/**
 * Get interactions where a user is the partner (someone recorded them as their interaction partner).
 *
 * SECURITY: This is the ONLY query used for contact discovery in the unidirectional model.
 * A user is found as a contact only if THEY recorded the interaction with the current user.
 *
 * CACHING (Task 4.2):
 * When an optional QueryCache is provided, the function will:
 * 1. Check the cache before executing a Firestore query
 * 2. Store query results in the cache for subsequent calls with the same parameters
 *
 * This optimization is particularly valuable in multi-path scenarios where the same
 * user's interactions may be queried multiple times via different paths.
 *
 * @param partnerId - The HASHED user ID to find as a partner in other users' records
 * @param windowStart - Start of the exposure window
 * @param windowEnd - End of the exposure window
 * @param cache - Optional QueryCache for caching results within a single propagation
 * @returns Interaction documents where the specified user is listed as partner
 */
export async function getInteractionsAsPartner(
  partnerId: string,
  windowStart: number,
  windowEnd: number,
  cache?: QueryCache<InteractionDocArray>,
): Promise<admin.firestore.QueryDocumentSnapshot[]> {
  const retentionBoundary = getRetentionBoundary();

  // Ensure we respect the 180-day boundary
  const effectiveStart = Math.max(windowStart, retentionBoundary);

  // If cache is provided, check for cached results first
  if (cache) {
    const cacheKey = QueryCache.generateKey(
      QueryType.INTERACTIONS,
      partnerId,
      effectiveStart,
      windowEnd,
    );

    const cachedResult = cache.get(cacheKey);
    if (cachedResult !== undefined) {
      // Cache hit - return cached results
      return cachedResult;
    }

    // Cache miss - execute query and cache results
    const db = getDb();
    const snapshot = await db
      .collection(CONSTANTS.COLLECTIONS.INTERACTIONS)
      .where("partnerAnonymousId", "==", partnerId)
      .where("recordedAt", ">=", effectiveStart)
      .where("recordedAt", "<=", windowEnd)
      .get();

    const results = snapshot.docs;
    cache.set(cacheKey, results);
    return results;
  }

  // No cache provided - execute query directly (backward compatible)
  const db = getDb();
  const snapshot = await db
    .collection(CONSTANTS.COLLECTIONS.INTERACTIONS)
    .where("partnerAnonymousId", "==", partnerId)
    .where("recordedAt", ">=", effectiveStart)
    .where("recordedAt", "<=", windowEnd)
    .get();

  return snapshot.docs;
}
