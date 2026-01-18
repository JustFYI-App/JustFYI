/**
 * Rate limiting utility for Cloud Functions
 *
 * Implements simple per-user rate limiting using Firestore to track request counts.
 * Rate limit data is stored in the 'rateLimits' collection with automatic TTL cleanup.
 *
 * OPTIMIZATION (Task 5.6):
 * - Rate limit documents now include an `expiresAt` timestamp field
 * - Firestore TTL policy can auto-delete documents after they expire
 * - expiresAt = windowStart + windowDuration + buffer (1 hour buffer)
 * - This prevents the rateLimits collection from growing unbounded
 */

import { getDb } from "./database";

/**
 * Rate limit types for different operations
 */
export type RateLimitType = "positive_report" | "negative_test" | "data_export" | "account_recovery";

/**
 * Rate limit configuration per type (requests per hour)
 */
const RATE_LIMITS: Record<RateLimitType, number> = {
  positive_report: 5,
  negative_test: 10,
  data_export: 3,
  account_recovery: 5, // Limit recovery attempts to prevent brute-force attacks
};

/**
 * Rate limit document structure
 *
 * OPTIMIZATION (Task 5.6): Added expiresAt field for Firestore TTL cleanup
 */
interface RateLimitDoc {
  count: number;
  windowStart: number;
  /**
   * Timestamp when this document should be auto-deleted by Firestore TTL policy.
   * Set to windowStart + windowDuration + buffer (typically 2 hours from creation).
   */
  expiresAt: number;
}

/**
 * Rate limit window duration in milliseconds (1 hour)
 */
const WINDOW_DURATION_MS = 60 * 60 * 1000;

/**
 * Buffer time after window expires before document is eligible for TTL deletion.
 * This ensures the document persists long enough for any in-flight requests.
 */
const TTL_BUFFER_MS = 60 * 60 * 1000; // 1 hour buffer

/**
 * Calculate the expiresAt timestamp for a rate limit document.
 * Task 5.6: expiresAt = windowStart + windowDuration + buffer
 *
 * @param windowStart - The timestamp when the rate limit window started
 * @returns Timestamp when the document should expire
 */
function calculateExpiresAt(windowStart: number): number {
  return windowStart + WINDOW_DURATION_MS + TTL_BUFFER_MS;
}

/**
 * Check and update rate limit for a user.
 *
 * OPTIMIZATION (Task 5.6):
 * - Now includes expiresAt field in all rate limit documents
 * - Documents can be auto-deleted by Firestore TTL policy
 *
 * @param userId - The user ID to check rate limit for
 * @param limitType - The type of operation being rate limited
 * @returns true if request is allowed, false if rate limited
 */
export async function checkRateLimit(
  userId: string,
  limitType: RateLimitType,
): Promise<boolean> {
  const db = getDb();
  const docId = `${userId}_${limitType}`;
  const docRef = db.collection("rateLimits").doc(docId);

  const maxRequests = RATE_LIMITS[limitType];
  const now = Date.now();

  try {
    const result = await db.runTransaction(async (transaction) => {
      const doc = await transaction.get(docRef);

      if (!doc.exists) {
        // First request - create rate limit entry with expiresAt
        const newDoc: RateLimitDoc = {
          count: 1,
          windowStart: now,
          expiresAt: calculateExpiresAt(now),
        };
        transaction.set(docRef, newDoc);
        return true;
      }

      const data = doc.data() as RateLimitDoc;

      // Check if window has expired
      if (now - data.windowStart > WINDOW_DURATION_MS) {
        // Reset window with new expiresAt
        const resetDoc: RateLimitDoc = {
          count: 1,
          windowStart: now,
          expiresAt: calculateExpiresAt(now),
        };
        transaction.set(docRef, resetDoc);
        return true;
      }

      // Check if limit exceeded
      if (data.count >= maxRequests) {
        return false;
      }

      // Increment counter (expiresAt stays the same since window hasn't reset)
      transaction.update(docRef, {
        count: data.count + 1,
      });
      return true;
    });

    return result;
  } catch (error) {
    // On error, allow the request but log the issue
    console.error(`Rate limit check failed for ${userId}:`, error);
    return true;
  }
}

/**
 * Get remaining requests for a user.
 *
 * @param userId - The user ID to check
 * @param limitType - The type of operation
 * @returns Object with remaining requests and reset time
 */
export async function getRateLimitStatus(
  userId: string,
  limitType: RateLimitType,
): Promise<{ remaining: number; resetAt: number }> {
  const db = getDb();
  const docId = `${userId}_${limitType}`;
  const docRef = db.collection("rateLimits").doc(docId);

  const maxRequests = RATE_LIMITS[limitType];
  const now = Date.now();

  try {
    const doc = await docRef.get();

    if (!doc.exists) {
      return {
        remaining: maxRequests,
        resetAt: now + WINDOW_DURATION_MS,
      };
    }

    const data = doc.data() as RateLimitDoc;

    // Check if window has expired
    if (now - data.windowStart > WINDOW_DURATION_MS) {
      return {
        remaining: maxRequests,
        resetAt: now + WINDOW_DURATION_MS,
      };
    }

    return {
      remaining: Math.max(0, maxRequests - data.count),
      resetAt: data.windowStart + WINDOW_DURATION_MS,
    };
  } catch (error) {
    console.error(`Rate limit status check failed for ${userId}:`, error);
    return {
      remaining: maxRequests,
      resetAt: now + WINDOW_DURATION_MS,
    };
  }
}
