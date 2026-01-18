# Just FYI Firebase Cloud Functions

Privacy-first STI exposure notification backend built on Firebase Cloud Functions with Firestore.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Cloud Functions](#cloud-functions)
- [Database Schema](#database-schema)
- [Security Model](#security-model)
- [Optimization System](#optimization-system)
- [Configuration](#configuration)
- [Testing](#testing)
- [Deployment](#deployment)
- [API Reference](#api-reference)

---

## Overview

This module provides the serverless backend for Just FYI, a privacy-preserving STI exposure notification system. Users who test positive can anonymously notify their contacts through a chain propagation system that respects privacy while enabling epidemiological tracking.

### Key Features

- **Privacy-First Design**: Domain-separated hashing prevents correlation attacks
- **Unidirectional Contact Discovery**: Only users who recorded interactions can be notified
- **Chain Propagation**: Notifications spread up to 10 hops with rolling exposure windows
- **Multi-Path Deduplication**: Users reachable via multiple paths receive one notification
- **GDPR Compliance**: Data export and 180-day retention with automatic cleanup
- **Performance Optimizations**: Query caching, batch operations, and FCM multicast

### Technology Stack

| Component | Technology |
|-----------|------------|
| Runtime | Node.js 20 |
| Language | TypeScript 5.3 |
| Database | Firestore |
| Functions | Firebase Cloud Functions v7 |
| Push Notifications | Firebase Cloud Messaging |
| Authentication | Firebase Anonymous Auth |
| Region | europe-west1 |

---

## Architecture

### Directory Structure

```
firebase/functions/
├── src/
│   ├── index.ts                    # Main entry point (exports all functions)
│   ├── types.ts                    # TypeScript type definitions
│   ├── functions/                  # Cloud Function implementations
│   │   ├── processExposureReport.ts
│   │   ├── updateTestStatus.ts
│   │   ├── cleanupExpiredData.ts
│   │   ├── exportUserData.ts
│   │   ├── deleteExposureReport.ts
│   │   └── recoverAccount.ts
│   ├── utils/                      # Utility modules
│   │   ├── database.ts             # Firestore access
│   │   ├── logger.ts               # Structured logging
│   │   ├── rateLimit.ts            # Rate limiting with TTL
│   │   ├── chainPropagation.ts     # Chain propagation orchestrator
│   │   ├── pushNotification.ts     # FCM integration
│   │   ├── stiConfig.ts            # STI configuration loader
│   │   ├── crypto/                 # Hashing utilities
│   │   │   └── hashing.ts
│   │   ├── queries/                # Database query utilities
│   │   │   └── userQueries.ts
│   │   ├── chains/                 # Chain processing utilities
│   │   │   ├── chainUpdates.ts
│   │   │   ├── pathUtils.ts
│   │   │   ├── windowCalculation.ts
│   │   │   └── stiUtils.ts
│   │   ├── cache/                  # Query caching (optimization)
│   │   │   ├── queryCache.ts
│   │   │   └── userLookupCache.ts
│   │   └── batch/                  # Batch operations (optimization)
│   │       ├── types.ts
│   │       ├── notificationBatcher.ts
│   │       └── fcmBatcher.ts
│   └── __tests__/                  # Test suites
├── shared/
│   └── stiConfig.json              # STI incubation periods
├── package.json
├── tsconfig.json
├── jest.config.js
└── .eslintrc.js
```

### Data Flow

**Positive Report Flow:**
```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  User Reports   │────▶│ processExposure  │────▶│  Notifications  │
│  Positive Test  │     │     Report       │     │    Created      │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                               │
                               ▼
                   ┌──────────────────────┐
                   │  Chain Propagation   │
                   │  (up to 10 hops)     │
                   └──────────────────────┘
                               │
                               ▼
                   ┌──────────────────────┐
                   │   FCM Push Notify    │
                   │   (batched)          │
                   └──────────────────────┘
```

**Negative Report Flow:**
```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  User Reports   │────▶│ processExposure  │────▶│ Chain Nodes     │
│  Negative Test  │     │     Report       │     │ Updated         │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                               │
                               ▼
                   ┌──────────────────────┐
                   │  Propagate Negative  │
                   │  to Downstream       │
                   └──────────────────────┘
                               │
                               ▼
                   ┌──────────────────────┐
                   │   FCM Push Notify    │
                   │   (UPDATE type)      │
                   └──────────────────────┘
```

---

## Getting Started

### Prerequisites

- Node.js 20+
- npm 9+
- Firebase CLI (`npm install -g firebase-tools`)
- Firebase project with Firestore and Cloud Functions enabled

### Installation

```bash
cd firebase/functions
npm install
```

### Local Development

```bash
# Start Firebase emulators
npm run serve

# Watch mode compilation
npm run build:watch

# Run tests
npm test

# Run tests with emulators
npm run test:emulator
```

### Environment Setup

The functions use Firebase Admin SDK which automatically authenticates in:
- **Local**: Uses emulator or service account key
- **Deployed**: Uses default service account

For local development with emulators:
```bash
export FIRESTORE_EMULATOR_HOST=localhost:8080
export FIREBASE_AUTH_EMULATOR_HOST=localhost:9099
```

---

## Cloud Functions

### Event-Triggered Functions

#### processExposureReport

**Trigger**: Firestore `onCreate` on `reports/{reportId}`

Processes both POSITIVE and NEGATIVE test reports from the unified reports collection.

**For POSITIVE Reports**:
1. Validates report data (STI types, test date, privacy level)
2. Discovers contacts via unidirectional query
3. Creates notifications for direct contacts
4. Propagates to indirect contacts (up to 10 hops)
5. Updates reporter's own notifications with positive status
6. Handles chain linking for epidemiological tracking

**For NEGATIVE Reports**:
1. Updates the reporter's own notification (if notificationId provided)
2. Propagates negative status to downstream chain notifications
3. Sends UPDATE push notifications to affected downstream users
4. No new notification documents created (existing ones updated)

**Optimizations**:
- Single consolidated query for reporter's notifications
- Batch notification writes (max 500 per batch)
- FCM multicast (max 500 per call)
- Query result caching within function invocation

---

#### cleanupExpiredData

**Trigger**: Scheduled daily at 3:00 AM UTC

Removes data older than 180 days (retention period).

**Collections cleaned**:
- `interactions` (by `recordedAt`)
- `notifications` (by `receivedAt`)
- `reports` (by `reportedAt`)

**Batch size**: 500 documents per iteration

---

### Callable Functions (HTTPS)

| Function | Auth Required | Rate Limit | Purpose |
|----------|--------------|------------|---------|
| `reportPositiveTest` | Yes | 5/hour | Submit positive test report (triggers chain propagation) |
| `reportNegativeTest` | Yes | 10/hour | Submit negative test report (triggers status propagation) |
| `getChainLinkInfo` | Yes | - | Check for chain link opportunity |
| `exportUserData` | Yes | 3/hour | GDPR data export |
| `deleteExposureReport` | Yes | - | Delete own report |
| `recoverAccount` | No | 5/hour | Account recovery via saved ID |
| `triggerCleanup` | Admin | - | Manual cleanup trigger |

---

## Database Schema

### Collections

#### users
```typescript
{
  anonymousId: string;           // Firebase UID (document ID)
  username: string;              // Display name (max 50 chars)
  createdAt: number;             // Timestamp
  fcmToken?: string;             // Push notification token
  hashedInteractionId: string;   // SHA256(uid) for interaction queries
  hashedNotificationId: string;  // SHA256("notification:" + uid)
}
```

#### interactions
```typescript
{
  ownerId: string;               // SHA256(uid) - who recorded this
  partnerAnonymousId: string;    // SHA256(partnerUid) - interaction partner
  partnerUsernameSnapshot: string;
  recordedAt: number;            // Timestamp
}
```

#### notifications
```typescript
{
  recipientId: string;           // SHA256("notification:" + uid)
  type: "EXPOSURE" | "UPDATE" | "REPORT_DELETED";
  stiType?: string;              // STI type or JSON array
  exposureDate?: number;         // When exposure occurred
  chainData: string;             // JSON ChainVisualization
  isRead: boolean;
  receivedAt: number;
  updatedAt: number;
  reportId: string;              // Source report ID
  chainPath: string[];           // Primary path (shortest)
  hopDepth?: number;             // Hops from reporter (1 = direct)
  chainPaths?: string;           // All paths (JSON string[][])
  deletedAt?: number;            // Soft delete timestamp
}
```

#### reports

Unified collection for both positive and negative test reports, providing complete test history.

```typescript
{
  reporterId: string;                    // SHA256("report:" + uid)
  reporterInteractionHashedId: string;   // SHA256(uid)
  reporterNotificationHashedId?: string; // SHA256("notification:" + uid)
  stiTypes: string;                      // JSON array '["HIV", "SYPHILIS"]'
  testDate: number;
  privacyLevel: "FULL" | "STI_ONLY" | "DATE_ONLY" | "ANONYMOUS";
  reportedAt: number;
  status: "pending" | "processing" | "completed" | "failed";
  processedAt?: number;
  error?: string;
  testResult: "POSITIVE" | "NEGATIVE";   // Report type
  linkedReportId?: string;               // For chain linking (positive reports)
  notificationId?: string;               // Specific notification (negative reports)
}
```

**Note**: The `testStatuses` collection has been deprecated. Both positive and negative
test results are now stored in the unified `reports` collection with the `testResult`
field distinguishing between them.

#### rateLimits
```typescript
{
  count: number;           // Requests in current window
  windowStart: number;     // Window start timestamp
  expiresAt: number;       // TTL auto-delete timestamp
}
// Document ID: {userId}_{limitType}
```

### Firestore Indexes

Required composite indexes (defined in `firestore.indexes.json`):

```json
[
  {
    "collectionGroup": "interactions",
    "fields": [
      { "fieldPath": "partnerAnonymousId", "order": "ASCENDING" },
      { "fieldPath": "recordedAt", "order": "DESCENDING" }
    ]
  },
  {
    "collectionGroup": "notifications",
    "fields": [
      { "fieldPath": "recipientId", "order": "ASCENDING" },
      { "fieldPath": "receivedAt", "order": "DESCENDING" }
    ]
  },
  {
    "collectionGroup": "notifications",
    "fields": [
      { "fieldPath": "chainPath", "arrayConfig": "CONTAINS" },
      { "fieldPath": "reportId", "order": "ASCENDING" }
    ]
  }
]
```

---

## Security Model

### Domain-Separated Hashing

To prevent correlation attacks if the database is breached, UIDs are hashed with different domain prefixes:

| Purpose | Hash Formula | Used In |
|---------|--------------|---------|
| Interaction queries | `SHA256(uid)` | `interactions.ownerId`, `users.hashedInteractionId` |
| Notification delivery | `SHA256("notification:" + uid)` | `notifications.recipientId` |
| Chain path tracking | `SHA256("chain:" + interactionHash)` | `notifications.chainPath` |
| Report ownership | `SHA256("report:" + uid)` | `reports.reporterId` |

### Unidirectional Graph Traversal

Contacts are discovered ONLY via:
```
interactions.partnerAnonymousId == reporterHashedId
```

This ensures:
- Only users who **recorded** an interaction can be notified
- Prevents malicious false reporting
- Privacy-first by design

### Rate Limiting

Per-user hourly limits enforced via Firestore transactions:

| Operation | Limit |
|-----------|-------|
| Positive reports | 5/hour |
| Negative tests | 10/hour |
| Data exports | 3/hour |
| Account recovery | 5/hour |

Rate limit documents include `expiresAt` for automatic TTL cleanup.

---

## Optimization System

### Query Result Caching

Function-scoped caching prevents duplicate Firestore queries within a single invocation:

```typescript
// QueryCache<T> - Generic cache for query results
const cache = createQueryCache<InteractionDoc[]>();

// Check cache before query
const cached = cache.get(cacheKey);
if (cached) return cached;

// Store result after query
cache.set(cacheKey, results);
```

**Features**:
- Max 1000 entries with LRU eviction
- Cache statistics (hits, misses, hit rate)
- Keyed by `${queryType}:${partnerId}:${windowStart}:${windowEnd}`

### User Lookup Cache

Specialized cache for user document lookups:

```typescript
const userCache = createUserLookupCache();

// Batch populate from query results
userCache.populateFromBatch(users);

// Lookup with cache
const user = userCache.get(hashedInteractionId);
```

### Notification Batching

Collects notification writes and commits in batches:

```typescript
const batcher = createNotificationBatcher();

// Queue notifications during chain traversal
batcher.add(notificationData);

// Commit all at once (max 500 per batch)
const result = await batcher.commit();
```

### FCM Multicast Batching

Collects push notifications and sends as multicast:

```typescript
const fcmBatcher = createFCMBatcher();

// Queue FCM notifications
fcmBatcher.add({ token, payload });

// Send all at once (max 500 per multicast)
const result = await fcmBatcher.send();
```

### Performance Impact

| Optimization | Before | After | Improvement |
|--------------|--------|-------|-------------|
| User lookups | N sequential queries | 1 batch query | ~10x reduction |
| Interaction queries | Duplicate per path | Cached | 30-50% reduction |
| Notification writes | N individual writes | Batched (500) | Throughput increase |
| FCM sends | N individual sends | Multicast (500) | Throughput increase |

---

## Configuration

### STI Incubation Periods

Defined in `shared/stiConfig.json`:

```json
{
  "HIV": { "maxDays": 30 },
  "SYPHILIS": { "maxDays": 90 },
  "GONORRHEA": { "maxDays": 14 },
  "CHLAMYDIA": { "maxDays": 21 },
  "HPV": { "maxDays": 180 },
  "HERPES": { "maxDays": 21 },
  "OTHER": { "maxDays": 30 }
}
```

### Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `RETENTION_DAYS` | 180 | Data retention period |
| `MAX_CHAIN_DEPTH` | 10 | Maximum notification hops |
| `BATCH_SIZE` | 500 | Firestore batch limit |
| `MAX_STI_TYPES_JSON` | 500 | Max STI types JSON length |
| `MAX_USERNAME` | 50 | Max username length |

### ESLint Rules

Key rules enforced:
- Double quotes for strings
- Semicolons required
- Max line length: 120 characters
- Trailing commas in multiline

---

## Testing

### Test Structure

```
src/__tests__/
├── setup.ts                        # Jest setup and mocks
├── chainPropagation.test.ts        # Chain propagation logic
├── chainStatusUpdates.test.ts      # Status update propagation
├── cloudFunctions.test.ts          # Function integration
├── deleteExposureReport.test.ts    # Report deletion
├── domainSeparatedHashing.test.ts  # Hash consistency
├── edgeCases.test.ts               # Boundary conditions
├── exportUserData.test.ts          # Data export
├── multiPathDeduplication.test.ts  # Multi-path handling
└── integration/
    ├── backendOptimization.test.ts # Optimization verification
    └── endToEndIntegration.test.ts # Full flow testing

src/utils/__tests__/
├── queryCache.test.ts              # Cache utilities
├── batchOperations.test.ts         # Batch utilities
└── chainPropagationOptimized.test.ts

src/functions/__tests__/
└── optimizations.test.ts           # Function optimizations
```

### Running Tests

```bash
# Run all tests with coverage
npm test

# Run specific test file
npm test -- --testPathPattern=chainPropagation

# Watch mode
npm run test:watch

# With emulators
npm run test:emulator
```

### Test Coverage

Current coverage: 110+ tests across:
- Unit tests for utilities
- Integration tests for functions
- Edge case handling
- Optimization verification

---

## Deployment

### Deploy to Production

```bash
npm run deploy
```

### Deploy to Staging

```bash
npm run deploy:staging
```

### Deploy Indexes Only

```bash
firebase deploy --only firestore:indexes
```

### Configure TTL Policy

After deploying, configure Firestore TTL for rate limits:

```bash
gcloud firestore fields ttls update expiresAt \
  --collection-group=rateLimits \
  --enable-ttl
```

### Post-Deployment Checklist

- [ ] Verify indexes are in READY state
- [ ] Test callable functions via Firebase Console
- [ ] Monitor Cloud Functions logs
- [ ] Verify FCM delivery
- [ ] Check Firestore read/write metrics

---

## API Reference

### reportPositiveTest

Submit a positive test report.

**Request**:
```typescript
{
  stiTypes: string;      // JSON array: '["HIV", "SYPHILIS"]'
  testDate: number;      // Milliseconds timestamp
  privacyLevel: "FULL" | "STI_ONLY" | "DATE_ONLY" | "ANONYMOUS";
}
```

**Response**:
```typescript
{
  success: boolean;
  reportId: string;
  linkedReportId: string | null;  // If linking to existing notification
}
```

---

### reportNegativeTest

Record a negative test result. Creates a report in the unified `reports` collection
with `testResult: "NEGATIVE"`, which triggers `processExposureReport` to handle
status propagation.

**Request**:
```typescript
{
  stiType?: string;        // Optional STI type
  notificationId?: string; // Optional specific notification to update
}
```

**Response**:
```typescript
{
  success: boolean;
  reportId: string;        // ID of the created report document
}
```

---

### getChainLinkInfo

Check for chain linking opportunity before reporting positive.

**Request**:
```typescript
{
  stiType?: string;  // Optional STI type filter
}
```

**Response**:
```typescript
{
  hasExistingNotification: boolean;
  linkedReportId: string | null;
}
```

---

### exportUserData

Export all user data (GDPR compliance).

**Request**: None

**Response**:
```typescript
{
  user: UserDocument | null;
  interactions: InteractionDocument[];
  notifications: NotificationDocument[];
  reports: ReportDocument[];
}
```

---

### deleteExposureReport

Delete own test report (positive or negative).

**For POSITIVE reports**:
- Marks all notifications associated with this report as "deleted"
- Sends REPORT_DELETED push notifications to impacted users

**For NEGATIVE reports**:
- Reverts chain status from NEGATIVE back to UNKNOWN
- Sends UPDATE push notifications to affected users
- Deletes the report document

**Request**:
```typescript
{
  reportId: string;
}
```

**Response**:
```typescript
{
  success: boolean;
  deletedNotificationsCount: number;  // For positive: notifications marked deleted
                                       // For negative: chain nodes reverted
  error?: string;
}
```

---

### recoverAccount

Recover account using saved anonymous ID.

**Request**:
```typescript
{
  savedId: string;  // 20-40 alphanumeric characters
}
```

**Response**:
```typescript
{
  success: boolean;
  customToken?: string;  // Use with signInWithCustomToken()
  error?: string;
}
```

---

## Troubleshooting

### Common Issues

**"Index not found" errors**:
```bash
firebase deploy --only firestore:indexes
# Wait for indexes to reach READY state
```

**Rate limit errors**:
- Check `rateLimits` collection for stuck documents
- Verify TTL policy is enabled

**FCM delivery failures**:
- Check for invalid tokens in logs
- Verify FCM configuration in Firebase Console

### Logging

All functions use structured logging:
```
[INFO] Processing report abc123
[WARN] User xyz has invalid FCM token
[ERROR] Failed to create notification: ...
```

View logs in Firebase Console or via:
```bash
firebase functions:log
```

---

## Contributing

1. Follow existing code patterns and TypeScript conventions
2. Add tests for new functionality
3. Run `npm run lint` before committing
4. Update this README for significant changes

---

## License

Apache License 2.0 - See [LICENSE](../../LICENSE) for details.
