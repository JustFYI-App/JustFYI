/**
 * Chain propagation utilities for exposure notification chains
 *
 * MODULE STRUCTURE:
 * =================
 * This module orchestrates chain propagation using extracted utilities:
 * - utils/crypto/hashing.ts - Domain-separated hash functions
 * - utils/queries/userQueries.ts - User lookup by hashed ID
 * - utils/chains/pathUtils.ts - Path comparison and normalization
 * - utils/chains/windowCalculation.ts - Exposure window calculations
 * - utils/chains/stiUtils.ts - STI type parsing and matching
 * - utils/chains/chainUpdates.ts - Negative test propagation and chain linking
 * - utils/cache/ - Query result caching utilities
 * - utils/batch/ - Batch operation utilities (Task Group 3)
 *
 * SECURITY MODEL: Unidirectional Graph Traversal
 * =============================================
 * This module implements a unidirectional traversal model for exposure notifications.
 * Contacts are discovered ONLY by querying `partnerAnonymousId == userId`.
 *
 * This means a user is notified only if THEY recorded the interaction, not if someone
 * else claims to have interacted with them. This prevents malicious false reporting.
 *
 * ROLLING WINDOW MODEL:
 * =====================
 * Each hop in the chain uses the interaction date as the new window start.
 * This creates a "rolling window" effect where:
 * - A tested on Dec 20, window = Dec 20 - incubation period
 * - B interacted with A on Dec 10, B's window starts from Dec 10
 * - C interacted with B on Dec 15, C's window starts from Dec 15
 *
 * MULTI-PATH DEDUPLICATION:
 * =========================
 * When a user is reachable via multiple paths (e.g., A->B->D and A->C->D),
 * they receive exactly ONE notification. The implementation:
 * - Uses a Map<string, PathInfo> instead of Set<string> to track paths
 * - Stores all paths leading to each user
 * - Uses the shortest path for hop depth calculation
 * - Merges additional paths via Firestore transactions
 *
 * OPTIMIZATION (Task Group 4):
 * ============================
 * The PropagationContext supports optional caching and batching utilities to reduce
 * Firestore operations during a single chain propagation:
 * - interactionCache: Caches interaction query results by partnerId + window
 * - userCache: Caches user lookup results by hashedInteractionId
 * - notificationBatcher: Batches notification writes (max 500 per batch)
 * - fcmBatcher: Batches FCM multicast sends (max 500 per multicast)
 * These are optional for backward compatibility with existing code.
 */

import { getDb } from "./database";
import {
  ChainNode,
  ChainVisualization,
  InteractionDocument,
  NotificationDocument,
  NotificationType,
  PathInfo,
  PrivacyLevel,
  TestStatus,
  CONSTANTS,
} from "../types";
import { sendExposureNotification } from "./pushNotification";
import { getMaxIncubationDaysFromJson } from "./stiConfig";
import { logInfo } from "./logger";

// Import cache utilities
import { QueryCache, createQueryCache } from "./cache/queryCache";
import { UserLookupCache, createUserLookupCache } from "./cache/userLookupCache";

// Import batch utilities (Task Group 3)
import { NotificationBatcher, createNotificationBatcher } from "./batch/notificationBatcher";
import { FCMBatcher, createFCMBatcher } from "./batch/fcmBatcher";

// Re-export cache types for consumers
export { QueryCache, UserLookupCache };
export { QueryType, CacheStats, createQueryCache } from "./cache/queryCache";
export { UserCacheStats, createUserLookupCache } from "./cache/userLookupCache";

// Re-export batch types for consumers
export { NotificationBatcher, FCMBatcher, createNotificationBatcher, createFCMBatcher };
export { PendingNotification, PendingFCM, BatchResult, FCMBatchResult } from "./batch/types";

// Re-export from extracted modules for backward compatibility
export {
  hashAnonymousId,
  hashForNotification,
  hashForChain,
  hashForReport,
} from "./crypto/hashing";

export {
  propagateNegativeTestUpdate,
  propagatePositiveTestUpdate,
  findLinkedReportId,
} from "./chains/chainUpdates";

// Import from extracted modules
import {
  hashForChain,
} from "./crypto/hashing";

import {
  getUserByHashedInteractionId,
  getUsersByHashedInteractionIds,
  UserLookupResult,
} from "./queries/userQueries";

import {
  arePathsEquivalent,
} from "./chains/pathUtils";

import {
  getRetentionBoundary,
  calculateRollingWindow,
  getInteractionsAsPartner,
  InteractionDocArray,
} from "./chains/windowCalculation";

import { parseStiTypes } from "./chains/stiUtils";

/**
 * Type alias for interaction document arrays (used in caching)
 */
export type { InteractionDocArray };

/**
 * Context for chain propagation
 *
 * CACHING SUPPORT:
 * The optional interactionCache and userCache fields enable query result
 * caching to reduce duplicate Firestore reads during chain propagation.
 * When provided, the propagation logic will:
 * 1. Check the cache before executing Firestore queries
 * 2. Store query results in the cache after fetching
 *
 * BATCHING SUPPORT (Task Group 4):
 * The optional notificationBatcher and fcmBatcher fields enable batched
 * operations to reduce the number of individual Firestore writes and FCM sends.
 * When provided:
 * - Notifications are queued during traversal and committed in batches
 * - FCM notifications are collected and sent as multicast
 *
 * For backward compatibility, these fields are optional and default to
 * undefined, preserving existing behavior when utilities are not provided.
 */
export interface PropagationContext {
  reportId: string;
  reporterId: string;
  reporterUsername: string;
  stiTypes: string;
  testDate: number;
  privacyLevel: PrivacyLevel;
  exposureWindowStart: number;
  exposureWindowEnd: number;
  notifiedUsers: Map<string, PathInfo>;
  incubationDays: number;

  /**
   * Optional cache for interaction query results.
   * Key format: `interactions:${partnerId}:${windowStart}:${windowEnd}`
   * Value: Array of interaction document snapshots
   *
   * When provided, getInteractionsAsPartner results will be cached
   * and reused for the same query parameters within a single propagation.
   */
  interactionCache?: QueryCache<InteractionDocArray>;

  /**
   * Optional cache for user lookup results.
   * Key: hashedInteractionId
   * Value: UserLookupResult or null (if user not found)
   *
   * When provided, getUserByHashedInteractionId results will be cached
   * and reused for the same hashedInteractionId within a single propagation.
   */
  userCache?: UserLookupCache;

  /**
   * Optional batcher for notification writes.
   * When provided, notifications will be queued during traversal
   * and committed in batches at the end of each propagation level.
   */
  notificationBatcher?: NotificationBatcher;

  /**
   * Optional batcher for FCM push notifications.
   * When provided, push notifications will be collected during traversal
   * and sent as multicast at the end of propagation.
   */
  fcmBatcher?: FCMBatcher;
}

/**
 * Look up user with caching support.
 * Uses userCache if provided, otherwise falls back to direct query.
 *
 * @param hashedInteractionId - The hashed interaction ID to look up
 * @param userCache - Optional cache for user lookups
 * @returns UserLookupResult or null if not found
 */
async function getUserWithCache(
  hashedInteractionId: string,
  userCache?: UserLookupCache,
): Promise<UserLookupResult | null> {
  // If cache is provided, check it first
  if (userCache) {
    // Check if already in cache
    if (userCache.has(hashedInteractionId)) {
      const cached = userCache.get(hashedInteractionId);
      // cached could be null (user not found) or UserLookupResult
      return cached === undefined ? null : cached;
    }

    // Not in cache - query and cache the result
    const userData = await getUserByHashedInteractionId(hashedInteractionId);
    userCache.set(hashedInteractionId, userData);
    return userData;
  }

  // No cache - direct query
  return getUserByHashedInteractionId(hashedInteractionId);
}

/**
 * Batch lookup users and populate cache (Task 4.3).
 * Collects unique hashedInteractionIds and queries them in batches.
 *
 * @param hashedInteractionIds - Array of hashed interaction IDs to look up
 * @param userCache - Cache to populate with results
 */
async function batchLookupUsersAndPopulateCache(
  hashedInteractionIds: string[],
  userCache: UserLookupCache,
): Promise<void> {
  // Get IDs that are not yet cached
  const uncachedIds = userCache.getUncachedIds(hashedInteractionIds);

  if (uncachedIds.length === 0) {
    return; // All IDs already cached
  }

  // Batch query for uncached users
  const batchResults = await getUsersByHashedInteractionIds(uncachedIds);

  // Populate cache with found users
  userCache.populateFromBatch(batchResults);

  // Mark not-found users in cache to avoid re-querying
  const foundIds = new Set(batchResults.keys());
  const notFoundIds = uncachedIds.filter(id => !foundIds.has(id));
  userCache.setMultipleNotFound(notFoundIds);

  if (notFoundIds.length > 0) {
    console.log(`Batch user lookup: ${batchResults.size} found, ${notFoundIds.length} not found`);
  }
}

/**
 * Merge a new path into an existing notification using a Firestore transaction.
 */
async function mergePathIntoNotification(
  notificationId: string,
  newPath: string[],
  newHopDepth: number,
  newChainNodes: ChainNode[],
  _context: PropagationContext,
): Promise<void> {
  const db = getDb();
  const notificationRef = db
    .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
    .doc(notificationId);

  try {
    await db.runTransaction(async (transaction) => {
      const notificationDoc = await transaction.get(notificationRef);

      if (!notificationDoc.exists) {
        console.log(`Notification ${notificationId} not found for path merge`);
        return;
      }

      const notification = notificationDoc.data() as NotificationDocument;
      const now = Date.now();

      let existingPaths: string[][];
      try {
        existingPaths = typeof notification.chainPaths === "string"
          ? JSON.parse(notification.chainPaths)
          : (notification.chainPaths || [notification.chainPath]);
      } catch {
        existingPaths = [notification.chainPath];
      }

      const hasEquivalentPath = existingPaths.some(
        (existingPath) => arePathsEquivalent(existingPath, newPath)
      );

      if (hasEquivalentPath) {
        console.log(`Equivalent path already exists in notification ${notificationId} (group event dedup)`);
        return;
      }

      const updatedPaths = [...existingPaths, newPath];
      const currentHopDepth = notification.hopDepth || notification.chainPath.length;
      const shouldUpdatePrimary = newHopDepth < currentHopDepth;

      let chainVisualization: ChainVisualization;
      try {
        chainVisualization = JSON.parse(notification.chainData);
      } catch {
        chainVisualization = { nodes: [] };
      }

      const existingVisualizationPaths = chainVisualization.paths || [chainVisualization.nodes];
      const updatedVisualizationPaths = [...existingVisualizationPaths, newChainNodes];

      const updatedChainVisualization: ChainVisualization = {
        nodes: shouldUpdatePrimary ? newChainNodes : chainVisualization.nodes,
        paths: updatedVisualizationPaths,
      };

      const updateData: Partial<NotificationDocument> = {
        chainPaths: JSON.stringify(updatedPaths),
        chainData: JSON.stringify(updatedChainVisualization),
        updatedAt: now,
      };

      if (shouldUpdatePrimary) {
        updateData.chainPath = newPath;
        updateData.hopDepth = newHopDepth;
      }

      transaction.update(notificationRef, updateData);
      console.log(`Merged path into notification ${notificationId}. Total paths: ${updatedPaths.length}`);
    });
  } catch (error) {
    console.error(`Error merging path into notification ${notificationId}:`, error);
  }
}

/**
 * Build a notification document for a user.
 * Returns the document data and metadata for batching.
 */
function buildNotificationData(
  hashedInteractionId: string,
  chain: ChainNode[],
  context: PropagationContext,
  exposureDate: number,
  hopDepth: number,
  currentPath: string[],
  hashedNotificationId: string,
): NotificationDocument {
  const shouldDiscloseSTI =
    context.privacyLevel === PrivacyLevel.FULL ||
    context.privacyLevel === PrivacyLevel.STI_ONLY;

  const shouldDiscloseDate =
    context.privacyLevel === PrivacyLevel.FULL ||
    context.privacyLevel === PrivacyLevel.DATE_ONLY;

  // Build display chain: show anonymous placeholder for upstream nodes, but preserve direct contact's username
  // Direct contact = last node in chain (the one right before "You")
  // Use @@chain_someone@@ marker for client-side localization
  const displayChain: ChainNode[] = chain.map((node, index) => {
    const isDirectContact = index === chain.length - 1;
    return {
      // Direct contact's username comes from recipient's interaction record (partnerUsernameSnapshot)
      // Upstream nodes use localization marker for privacy (user doesn't know them)
      username: isDirectContact ? node.username : "@@chain_someone@@",
      testStatus: node.testStatus,
      date: shouldDiscloseDate ? node.date : undefined,
      isCurrentUser: node.isCurrentUser,
    };
  });

  displayChain.push({
    username: "@@chain_you@@",
    testStatus: TestStatus.UNKNOWN,
    date: shouldDiscloseDate ? exposureDate : undefined,
    isCurrentUser: true,
  });

  const chainVisualization: ChainVisualization = {
    nodes: displayChain,
    paths: [displayChain],
  };

  const chainPath = [...currentPath, hashedInteractionId].map(id => hashForChain(id));
  const now = Date.now();

  return {
    recipientId: hashedNotificationId,
    type: NotificationType.EXPOSURE,
    stiType: shouldDiscloseSTI ? context.stiTypes : undefined,
    exposureDate: shouldDiscloseDate ? exposureDate : undefined,
    chainData: JSON.stringify(chainVisualization),
    isRead: false,
    receivedAt: now,
    updatedAt: now,
    reportId: context.reportId,
    chainPath,
    hopDepth,
    chainPaths: JSON.stringify([chainPath]),
  };
}

/**
 * Create a notification document for a user (with batching support - Task 4.4).
 * If notificationBatcher is provided, queues the notification; otherwise writes immediately.
 */
async function createNotification(
  hashedInteractionId: string,
  chain: ChainNode[],
  context: PropagationContext,
  exposureDate: number,
  hopDepth: number,
  currentPath: string[],
  hashedNotificationId: string,
  fcmToken?: string,
): Promise<string | null> {
  const notificationData = buildNotificationData(
    hashedInteractionId,
    chain,
    context,
    exposureDate,
    hopDepth,
    currentPath,
    hashedNotificationId,
  );

  // If batching is enabled, queue instead of immediate write (Task 4.4)
  if (context.notificationBatcher) {
    context.notificationBatcher.add({
      data: notificationData,
      hashedInteractionId,
      hashedNotificationId,
    });

    // Also queue FCM notification if batcher is available (Task 4.5)
    if (context.fcmBatcher && fcmToken) {
      const shouldDiscloseSTI =
        context.privacyLevel === PrivacyLevel.FULL ||
        context.privacyLevel === PrivacyLevel.STI_ONLY;

      context.fcmBatcher.add({
        token: fcmToken,
        notificationId: "", // Will be updated after batch commit
        type: "EXPOSURE",
        titleLocKey: "notification_exposure_title",
        bodyLocKey: "notification_exposure_body",
        data: shouldDiscloseSTI ? { stiType: context.stiTypes } : undefined,
      });
    }

    // Return placeholder - actual ID will be set after batch commit
    return `pending:${hashedInteractionId}`;
  }

  // No batching - immediate write (backward compatible)
  const db = getDb();

  try {
    const docRef = await db
      .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
      .add(notificationData);

    console.log(
      `Created notification ${docRef.id} for user ` +
      `(hashedInteractionId: ${hashedInteractionId}) at hop depth ${hopDepth}`
    );
    return docRef.id;
  } catch (error) {
    console.error(`Error creating notification for hashedInteractionId ${hashedInteractionId}:`, error);
    return null;
  }
}

/**
 * Check if a user has already been notified and handle multi-path logic.
 */
async function handleMultiPathUser(
  userId: string,
  currentPath: string[],
  hopDepth: number,
  chain: ChainNode[],
  context: PropagationContext,
): Promise<boolean> {
  const existingPathInfo = context.notifiedUsers.get(userId);

  if (!existingPathInfo) {
    return true;
  }

  const fullPath = [...currentPath, userId];

  const hasEquivalentPath = existingPathInfo.paths.some(
    (existingPath) => arePathsEquivalent(existingPath, fullPath)
  );

  if (hasEquivalentPath) {
    console.log(`Skipping equivalent path for user ${userId} (group event dedup)`);
    return false;
  }

  existingPathInfo.paths.push(fullPath);

  if (hopDepth < existingPathInfo.minHopDepth) {
    existingPathInfo.minHopDepth = hopDepth;
  }

  if (existingPathInfo.notificationId) {
    // Same logic as createNotification: preserve direct contact's username, hide upstream
    const displayChain: ChainNode[] = chain.map((node, index) => {
      const isDirectContact = index === chain.length - 1;
      return {
        username: isDirectContact ? node.username : "@@chain_someone@@",
        testStatus: node.testStatus,
        date: node.date,
        isCurrentUser: node.isCurrentUser,
      };
    });

    displayChain.push({
      username: "@@chain_you@@",
      testStatus: TestStatus.UNKNOWN,
      isCurrentUser: true,
    });

    const hashedPath = fullPath.map(uid => hashForChain(uid));

    await mergePathIntoNotification(
      existingPathInfo.notificationId,
      hashedPath,
      hopDepth,
      displayChain,
      context,
    );
  }

  return false;
}

/**
 * Recursively propagate notifications through the chain using UNIDIRECTIONAL traversal.
 * Integrates caching for interaction queries (Task 4.2) and user lookups (Task 4.3).
 */
async function propagateChainRecursive(
  currentUserId: string,
  currentChain: ChainNode[],
  context: PropagationContext,
  depth: number,
  currentHopExposureDate: number,
  currentPath: string[],
): Promise<void> {
  if (depth >= CONSTANTS.MAX_CHAIN_DEPTH) {
    console.log(`Reached max chain depth (${CONSTANTS.MAX_CHAIN_DEPTH}) for report ${context.reportId}`);
    return;
  }

  const retentionBoundary = getRetentionBoundary();

  const { windowStart, windowEnd } = calculateRollingWindow(
    currentHopExposureDate,
    context.incubationDays,
  );

  // currentUserId is already hashed (from interaction.ownerId which stores hashed IDs)
  // DO NOT hash again - that would create a double-hash that never matches
  const hashedCurrentUserId = currentUserId;

  // Use cached interaction query if cache is available (Task 4.2)
  const interactionsAsPartner = await getInteractionsAsPartner(
    hashedCurrentUserId,
    windowStart,
    windowEnd,
    context.interactionCache, // Pass cache if available
  );

  const potentialContacts = new Map<string, { userId: string; username: string; date: number }>();

  for (const doc of interactionsAsPartner) {
    const interaction = doc.data() as InteractionDocument;
    const ownerId = interaction.ownerId;

    if (
      ownerId === context.reporterId ||
      currentPath.includes(ownerId)
    ) {
      continue;
    }

    if (interaction.recordedAt < retentionBoundary) {
      continue;
    }

    const existing = potentialContacts.get(ownerId);
    if (!existing || interaction.recordedAt > existing.date) {
      potentialContacts.set(ownerId, {
        userId: ownerId,
        username: interaction.partnerUsernameSnapshot || "Unknown",
        date: interaction.recordedAt,
      });
    }
  }

  // Task 4.3: Batch lookup users if cache is available
  if (context.userCache && potentialContacts.size > 0) {
    const contactIds = Array.from(potentialContacts.keys());
    await batchLookupUsersAndPopulateCache(contactIds, context.userCache);
  }

  for (const contact of potentialContacts.values()) {
    const hashedContactId = contact.userId;

    // Use cached user lookup (Task 4.3)
    const userData = await getUserWithCache(hashedContactId, context.userCache);
    if (!userData) {
      console.log(`Skipping indirect contact with hashedId ${hashedContactId} - user not found`);
      continue;
    }

    const extendedChain: ChainNode[] = [
      ...currentChain,
      {
        username: contact.username,
        testStatus: TestStatus.UNKNOWN,
        date: contact.date,
        isCurrentUser: false,
        userId: currentUserId,
      },
    ];

    const hopDepth = depth + 1;
    // currentPath already includes currentUserId (added by caller at lines 608/460)
    // DO NOT add it again - that creates duplicate entries causing chainPath/chainData length mismatch
    const extendedPath = currentPath;

    const isNewUser = await handleMultiPathUser(
      hashedContactId,
      extendedPath,
      hopDepth,
      extendedChain,
      context,
    );

    if (isNewUser) {
      const notificationId = await createNotification(
        hashedContactId,
        extendedChain,
        context,
        contact.date,
        hopDepth,
        extendedPath,
        userData.hashedNotificationId,
        userData.fcmToken, // Pass FCM token for batching
      );

      context.notifiedUsers.set(hashedContactId, {
        paths: [[...extendedPath, hashedContactId]],
        minHopDepth: hopDepth,
        notificationId: notificationId || undefined,
      });

      // If not batching, send FCM immediately (backward compatible)
      if (!context.notificationBatcher && notificationId) {
        const shouldDiscloseSTI =
          context.privacyLevel === PrivacyLevel.FULL ||
          context.privacyLevel === PrivacyLevel.STI_ONLY;

        await sendExposureNotification(
          hashedContactId,
          notificationId,
          shouldDiscloseSTI ? context.stiTypes : undefined,
        );
      }
    }

    await propagateChainRecursive(
      hashedContactId,
      extendedChain,
      context,
      depth + 1,
      contact.date,
      [...extendedPath, hashedContactId],
    );
  }
}

/**
 * Start chain propagation from the reporter.
 *
 * OPTIMIZATION (Task Group 4):
 * This function now supports optional caching and batching utilities:
 * - Creates caches for interaction queries and user lookups
 * - Creates batchers for notifications and FCM
 * - Commits batches and sends multicast at the end
 *
 * @param reportId - The report document ID
 * @param reporterInteractionHashedId - The interaction-compatible hash: SHA256(uid.uppercase())
 * @param reporterUsername - Display name of the reporter
 * @param stiTypes - JSON array of STI types (e.g., '["HIV", "SYPHILIS"]')
 * @param testDate - Timestamp of the test date
 * @param privacyLevel - Privacy level for the notification
 * @param linkedReportId - Optional linked report ID (when chain member reports positive)
 *                         Users who already have notifications from this report will be skipped
 * @param enableOptimizations - Optional flag to enable caching/batching (default: true)
 * @returns Number of notifications created
 */
export async function propagateExposureChain(
  reportId: string,
  reporterInteractionHashedId: string,
  reporterUsername: string,
  stiTypes: string,
  testDate: number,
  privacyLevel: PrivacyLevel,
  linkedReportId?: string,
  enableOptimizations: boolean = true,
): Promise<number> {
  const db = getDb();
  let totalNotifications = 0;

  const msPerDay = 24 * 60 * 60 * 1000;
  const incubationDays = getMaxIncubationDaysFromJson(stiTypes);
  const now = Date.now();

  const retentionBoundary = getRetentionBoundary();
  const exposureWindowStart = Math.max(
    testDate - incubationDays * msPerDay,
    retentionBoundary,
  );
  const exposureWindowEnd = now;

  // Initialize optimization utilities if enabled
  const interactionCache = enableOptimizations ? createQueryCache<InteractionDocArray>() : undefined;
  const userCache = enableOptimizations ? createUserLookupCache() : undefined;
  const notificationBatcher = enableOptimizations ? createNotificationBatcher() : undefined;
  const fcmBatcher = enableOptimizations ? createFCMBatcher() : undefined;

  const context: PropagationContext = {
    reportId,
    reporterId: reporterInteractionHashedId,
    reporterUsername,
    stiTypes,
    testDate,
    privacyLevel,
    exposureWindowStart,
    exposureWindowEnd,
    notifiedUsers: new Map<string, PathInfo>(),
    incubationDays,
    // Optimization utilities (Task Group 4)
    interactionCache,
    userCache,
    notificationBatcher,
    fcmBatcher,
  };

  context.notifiedUsers.set(reporterInteractionHashedId, {
    paths: [[reporterInteractionHashedId]],
    minHopDepth: 0,
  });

  // CHAIN LINKING: If this is a linked report (chain member reporting positive),
  // we need to check if the new report has STI types not covered by the linked report.
  // - If all new STI types are in the linked report: skip users (just update existing)
  // - If new STI types exist: allow new notifications to be created
  //
  // Example: linked report has [A,B,C], new report has [B,C,D]
  // D is new, so users should get both:
  // 1. Updated existing notification (for B,C overlap) - done by propagatePositiveTestUpdate
  // 2. New notification for [B,C,D] chain starting from new reporter
  if (linkedReportId) {
    // Get the linked report to compare STI types
    const linkedReportDoc = await db
      .collection(CONSTANTS.COLLECTIONS.REPORTS)
      .doc(linkedReportId)
      .get();

    let shouldSkipExistingUsers = true;

    if (linkedReportDoc.exists) {
      const linkedReportData = linkedReportDoc.data();
      const linkedStiTypes = parseStiTypes(linkedReportData?.stiTypes || "[]");
      const newStiTypes = parseStiTypes(stiTypes);

      // Check if new report has any STI types not in linked report
      const newStiTypesNotInLinked = newStiTypes.filter(
        sti => !linkedStiTypes.map(s => s.toUpperCase()).includes(sti.toUpperCase())
      );

      if (newStiTypesNotInLinked.length > 0) {
        // New STI types exist - allow new notifications to be created
        shouldSkipExistingUsers = false;
        console.log(
          `Chain linking: New report has STI types [${newStiTypesNotInLinked.join(", ")}] ` +
          `not in linked report [${linkedStiTypes.join(", ")}] - will create new notifications`
        );
      }
    }

    if (shouldSkipExistingUsers) {
      const existingNotifications = await db
        .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
        .where("reportId", "==", linkedReportId)
        .get();

      let skippedCount = 0;
      for (const doc of existingNotifications.docs) {
        const notification = doc.data();
        const recipientId = notification.recipientId;
        if (recipientId) {
          // Look up user by notification hash to get their interaction hash
          const userQuery = await db
            .collection(CONSTANTS.COLLECTIONS.USERS)
            .where("hashedNotificationId", "==", recipientId)
            .limit(1)
            .get();

          if (!userQuery.empty) {
            const userData = userQuery.docs[0].data();
            const hashedInteractionId = userData.hashedInteractionId;
            if (hashedInteractionId && !context.notifiedUsers.has(hashedInteractionId)) {
              context.notifiedUsers.set(hashedInteractionId, {
                paths: [], // Existing notification, paths not relevant
                minHopDepth: notification.hopDepth || 1,
                notificationId: doc.id,
              });
              skippedCount++;

              // Pre-populate user cache with found user (Task 4.3)
              if (userCache) {
                userCache.set(hashedInteractionId, {
                  hashedInteractionId,
                  hashedNotificationId: recipientId,
                  fcmToken: userData.fcmToken,
                  docRef: userQuery.docs[0].ref,
                });
              }
            }
          }
        }
      }
      console.log(
        `Chain linking: Pre-populated ${skippedCount} users from linked report ${linkedReportId} ` +
        "(will skip creating duplicate notifications)"
      );
    }
  }

  const initialChain: ChainNode[] = [
    {
      username: context.reporterUsername,
      testStatus: TestStatus.POSITIVE,
      date: testDate,
      isCurrentUser: false,
      userId: reporterInteractionHashedId,
    },
  ];

  // Use cached interaction query (Task 4.2)
  const contactInteractions = await getInteractionsAsPartner(
    reporterInteractionHashedId,
    exposureWindowStart,
    exposureWindowEnd,
    interactionCache, // Pass cache if available
  );

  logInfo(`Found ${contactInteractions.length} contacts for report ${reportId}`);

  // Deduplicate contacts: if same user has multiple interactions, use the most recent one
  // This ensures we get the latest partnerUsernameSnapshot
  const directContacts = new Map<
    string,
    { interaction: InteractionDocument; doc: FirebaseFirestore.QueryDocumentSnapshot }
  >();
  for (const doc of contactInteractions) {
    const interaction = doc.data() as InteractionDocument;
    const hashedContactId = interaction.ownerId;

    const existing = directContacts.get(hashedContactId);
    if (!existing || interaction.recordedAt > existing.interaction.recordedAt) {
      directContacts.set(hashedContactId, { interaction, doc });
    }
  }

  // Task 4.3: Batch lookup all direct contacts to populate cache
  if (userCache && directContacts.size > 0) {
    const contactIds = Array.from(directContacts.keys());
    await batchLookupUsersAndPopulateCache(contactIds, userCache);
  }

  for (const [hashedContactId, { interaction }] of directContacts) {
    if (context.notifiedUsers.has(hashedContactId)) {
      continue;
    }

    // Use cached user lookup (Task 4.3)
    const userData = await getUserWithCache(hashedContactId, userCache);
    if (!userData) {
      console.log(`Skipping contact with hashedId ${hashedContactId} - user not found`);
      continue;
    }

    const exposureDate = interaction.recordedAt;
    const hopDepth = 1;
    const contactPath = [reporterInteractionHashedId, hashedContactId];

    // For direct contacts (hop=1), use the username from their most recent interaction record
    // This shows the reporter's name as the recipient knows them (partnerUsernameSnapshot)
    // Falls back to localization marker if no snapshot available
    const directContactUsername = interaction.partnerUsernameSnapshot || "@@chain_someone@@";
    const chainForRecipient: ChainNode[] = [
      {
        ...initialChain[0],
        username: directContactUsername,
      },
    ];

    const notificationId = await createNotification(
      hashedContactId,
      chainForRecipient,
      context,
      exposureDate,
      hopDepth,
      [reporterInteractionHashedId],
      userData.hashedNotificationId,
      userData.fcmToken, // Pass FCM token for batching
    );

    context.notifiedUsers.set(hashedContactId, {
      paths: [contactPath],
      minHopDepth: hopDepth,
      notificationId: notificationId || undefined,
    });

    // If not batching, send FCM immediately and track count (backward compatible)
    if (!notificationBatcher && notificationId) {
      totalNotifications++;

      const shouldDiscloseSTI =
        privacyLevel === PrivacyLevel.FULL ||
        privacyLevel === PrivacyLevel.STI_ONLY;

      await sendExposureNotification(
        hashedContactId,
        notificationId,
        shouldDiscloseSTI ? stiTypes : undefined,
      );
    }

    await propagateChainRecursive(
      hashedContactId,
      initialChain,
      context,
      1,
      exposureDate,
      [reporterInteractionHashedId, hashedContactId],
    );
  }

  // Commit batched notifications and send FCM multicast (Tasks 4.4 & 4.5)
  if (notificationBatcher && !notificationBatcher.isEmpty) {
    console.log(`Committing ${notificationBatcher.size} batched notifications...`);
    const batchResult = await notificationBatcher.commit();
    console.log(`Batch commit: ${batchResult.successCount} succeeded, ${batchResult.failureCount} failed`);

    // Update notifiedUsers with actual notification IDs
    const idMap = notificationBatcher.getCreatedIdMap(batchResult);
    for (const [hashedId, pathInfo] of context.notifiedUsers) {
      if (pathInfo.notificationId?.startsWith("pending:")) {
        const actualId = idMap.get(hashedId);
        if (actualId) {
          pathInfo.notificationId = actualId;
        }
      }
    }

    // Log cache statistics
    if (interactionCache) {
      interactionCache.logStats("Interaction cache");
    }
    if (userCache) {
      userCache.logStats("User cache");
    }
  }

  // Send FCM multicast (Task 4.5)
  if (fcmBatcher && !fcmBatcher.isEmpty) {
    console.log(`Sending ${fcmBatcher.size} FCM notifications as multicast...`);
    const fcmResult = await fcmBatcher.send();
    console.log(`FCM multicast: ${fcmResult.successCount} succeeded, ${fcmResult.failureCount} failed`);

    if (fcmResult.invalidTokenIndices.length > 0) {
      console.log(`FCM: ${fcmResult.invalidTokenIndices.length} invalid tokens detected`);
    }
  }

  // Get final notification count
  const notificationCount = await db
    .collection(CONSTANTS.COLLECTIONS.NOTIFICATIONS)
    .where("reportId", "==", reportId)
    .count()
    .get();

  totalNotifications = notificationCount.data().count;

  console.log(`Chain propagation complete for report ${reportId}: ${totalNotifications} notifications created`);
  return totalNotifications;
}
