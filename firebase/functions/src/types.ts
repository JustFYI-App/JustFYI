/**
 * Type definitions for Just FYI Cloud Functions
 * Matches the Firestore collection structure defined in the Android app
 */

/**
 * User document in Firestore
 */
export interface UserDocument {
  anonymousId: string;
  username: string;
  createdAt: number;
  fcmToken?: string;
}

/**
 * Interaction document in Firestore
 */
export interface InteractionDocument {
  partnerAnonymousId: string;
  partnerUsernameSnapshot: string;
  recordedAt: number;
  ownerId: string;
}

/**
 * Notification types
 *
 * SOURCE OF TRUTH: shared/src/commonMain/kotlin/app/justfyi/data/model/FirestoreCollections.kt
 * Keep in sync with: FirestoreCollections.NotificationTypes
 *
 * @enum {string}
 * @property {string} EXPOSURE - Initial exposure notification from a positive report
 * @property {string} UPDATE - Status update to an existing notification (e.g., chain member tested negative)
 * @property {string} REPORT_DELETED - Notification informing users that a report they were
 *   notified about has been retracted/deleted by the reporter
 */
export enum NotificationType {
  EXPOSURE = "EXPOSURE",
  UPDATE = "UPDATE",
  /**
   * Notification sent to impacted users when the original reporter deletes/retracts their report.
   * This type is used to inform users that a previous exposure notification they received
   * is no longer valid because the reporter withdrew their report.
   */
  REPORT_DELETED = "REPORT_DELETED",
}

/**
 * Test status for chain nodes
 */
export enum TestStatus {
  POSITIVE = "POSITIVE",
  NEGATIVE = "NEGATIVE",
  UNKNOWN = "UNKNOWN",
}

/**
 * A node in the exposure chain
 */
export interface ChainNode {
  username: string;
  testStatus: TestStatus;
  date?: number;
  isCurrentUser: boolean;
  userId?: string; // Internal use only, not sent to client
  /** Specific STI types the user tested positive for (subset of notification's STIs) */
  testedPositiveFor?: string[];
}

/**
 * Chain visualization data
 *
 * MULTI-PATH SUPPORT:
 * When a user is reachable via multiple paths, the `paths` field contains
 * all possible routes to that user. The primary `nodes` field contains
 * the shortest path for backward compatibility.
 *
 * Example: User D reachable via A->B->D and A->C->D
 * - nodes: [A, B, D] (shortest or first discovered path)
 * - paths: [[A, B, D], [A, C, D]] (all paths)
 */
export interface ChainVisualization {
  nodes: ChainNode[];
  /** All paths leading to this user (array of node arrays) */
  paths?: ChainNode[][];
}

/**
 * Notification document in Firestore
 *
 * MULTI-PATH DEDUPLICATION:
 * When a user is reachable via multiple exposure paths, we store:
 * - chainPath: The primary (shortest) path for backward compatibility
 * - chainPaths: All paths leading to this user (JSON serialized array of arrays)
 * - hopDepth: Based on the shortest path
 *
 * This allows chain visualization to display all routes while
 * ensuring only one notification per user.
 *
 * NOTE: chainPaths is stored as a JSON string because Firestore doesn't support nested arrays.
 *
 * DELETED NOTIFICATIONS:
 * When a report is deleted, notifications are marked with a `deletedAt` timestamp
 * rather than being physically deleted. This preserves audit trail and allows
 * the client to show appropriate UI for retracted reports.
 */
export interface NotificationDocument {
  recipientId: string;
  type: NotificationType;
  stiType?: string;
  exposureDate?: number;
  chainData: string; // JSON serialized ChainVisualization
  isRead: boolean;
  receivedAt: number;
  updatedAt: number;
  reportId: string; // Reference to the original report
  chainPath: string[]; // Primary path (shortest) - User IDs for tracking
  hopDepth?: number; // Number of hops from the original reporter (1 = direct contact)
  /** All paths - JSON serialized string[][] (Firestore doesn't allow nested arrays) */
  chainPaths?: string;
  /** Timestamp when this notification was marked as deleted (report retracted) */
  deletedAt?: number;
}

/**
 * Privacy levels for exposure reports
 */
export enum PrivacyLevel {
  FULL = "FULL", // Disclose STI type and date
  STI_ONLY = "STI_ONLY", // Only STI type
  DATE_ONLY = "DATE_ONLY", // Only date
  ANONYMOUS = "ANONYMOUS", // Neither
}

/**
 * Report status values
 */
export enum ReportStatus {
  PENDING = "pending",
  PROCESSING = "processing",
  COMPLETED = "completed",
  FAILED = "failed",
}

/**
 * Exposure report document in Firestore
 *
 * NOTE: contactedIds is now OPTIONAL.
 * The backend automatically discovers contacts using unidirectional queries
 * (partnerAnonymousId == reporterId). This is a security improvement that
 * prevents false reporting - a user can only be notified if THEY recorded
 * the interaction with the reporter.
 *
 * CHAIN LINKING:
 * When a chain member (someone who received an exposure notification) later
 * tests positive and creates their own report, the linkedReportId field
 * links the new report to the original chain's report. This enables:
 * - Tracking epidemiological chains
 * - Understanding how infections spread through the network
 * - Better risk assessment for downstream users
 */
export interface ReportDocument {
  reporterId: string;
  /** Interaction-compatible hash for Cloud Function queries: SHA256(uid.uppercase()) */
  reporterInteractionHashedId: string;
  /** Notification-compatible hash for querying reporter's own notifications: SHA256("notification:" + uid) */
  reporterNotificationHashedId?: string;
  stiTypes: string; // JSON array of STI types
  testDate: number;
  privacyLevel: PrivacyLevel;
  contactedIds?: string; // DEPRECATED: Backend now discovers contacts automatically
  reportedAt: number;
  status: ReportStatus;
  processedAt?: number;
  error?: string;
  /**
   * The test result type - POSITIVE or NEGATIVE.
   * Both positive and negative test reports are stored in the reports collection
   * for complete history tracking.
   */
  testResult: TestStatus;
  /**
   * Optional link to an original report when a chain member reports positive.
   * If this user received a notification from another report and now tests positive,
   * this field links to that original report for chain tracking purposes.
   */
  linkedReportId?: string;
  /**
   * For NEGATIVE reports: Optional reference to a specific notification being responded to.
   * When a user reports negative in response to a specific exposure notification,
   * this links to that notification for tracking purposes.
   */
  notificationId?: string;
}

// TestStatusDocument removed - negative tests now stored in reports collection

/**
 * Cleanup statistics
 */
export interface CleanupStats {
  interactionsDeleted: number;
  notificationsDeleted: number;
  reportsDeleted: number;
  timestamp: number;
}

/**
 * Path info for multi-path tracking
 * Used internally during chain propagation to track all paths to a user
 */
export interface PathInfo {
  /** All paths leading to this user (array of user ID arrays) */
  paths: string[][];
  /** The minimum hop depth across all paths */
  minHopDepth: number;
  /** Notification document ID if already created */
  notificationId?: string;
}

/**
 * Constants for the application
 */
export const CONSTANTS = {
  /** Maximum data retention period in days (covers HPV 30-180 day incubation) */
  RETENTION_DAYS: 180,

  /** Collection names */
  COLLECTIONS: {
    USERS: "users",
    INTERACTIONS: "interactions",
    NOTIFICATIONS: "notifications",
    REPORTS: "reports",
    CLEANUP_LOGS: "cleanupLogs",
  },

  /**
   * Maximum chain depth for exposure notification propagation.
   *
   * Set to 10 hops based on epidemiological research suggesting that:
   * 1. Beyond 10 hops, exposure risk becomes negligible
   * 2. Most real-world STI transmission chains are shorter than 10 hops
   * 3. Limiting depth prevents excessive notifications for distant contacts
   * 4. Reduces computational cost and potential for notification fatigue
   *
   * Example chain: A -> B -> C -> D -> E -> F -> G -> H -> I -> J -> K
   * User K at hop 10 is notified, but any users beyond K would NOT be notified.
   */
  MAX_CHAIN_DEPTH: 10,

  /** Batch size for Firestore operations */
  BATCH_SIZE: 500,

  /** Input validation limits */
  MAX_INPUT_LENGTH: {
    STI_TYPES_JSON: 500,  // Max length for stiTypes JSON string
    REPORTER_ID: 128,      // Max length for reporterId hash
    USERNAME: 50,          // Max length for username
  },

  /** Rate limiting settings */
  RATE_LIMITS: {
    REPORTS_PER_HOUR: 5,           // Max positive reports per user per hour
    NEGATIVE_TESTS_PER_HOUR: 10,   // Max negative test reports per user per hour
    DATA_EXPORTS_PER_HOUR: 3,      // Max data export requests per user per hour
  },
} as const;

// NOTE: Push notification strings are now handled client-side using FCM localization keys.
// See pushNotification.ts for the key names and:
// - Android: androidApp/src/main/res/values/strings.xml
// - iOS: iosApp/iosApp/*.lproj/Localizable.strings
