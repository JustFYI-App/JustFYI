# Just FYI Backend - Firebase Cloud Functions

Privacy-first STI exposure notification backend built on Firebase.

## Overview

The Just FYI backend handles secure, privacy-preserving exposure notification processing. When a user reports a positive STI test, the system automatically discovers affected contacts and propagates notifications through the exposure chain while protecting user identities.

### Tech Stack

- **Firebase Cloud Functions** (Node.js/TypeScript) - Serverless compute
- **Cloud Firestore** - NoSQL document database (EU region: `eur3`)
- **Firebase Cloud Messaging (FCM)** - Push notifications
- **Firebase Authentication** - Anonymous authentication

## Module Structure

```
firebase/
├── functions/
│   ├── src/
│   │   ├── index.ts                    # Entry point, exports all functions
│   │   ├── types.ts                    # TypeScript type definitions
│   │   ├── functions/
│   │   │   ├── processExposureReport.ts  # Firestore trigger for new reports
│   │   │   ├── updateTestStatus.ts       # Negative test handling + HTTPS callables
│   │   │   └── cleanupExpiredData.ts     # Scheduled data cleanup
│   │   └── utils/
│   │       ├── chainPropagation.ts       # Core chain traversal logic
│   │       ├── pushNotification.ts       # FCM notification sending
│   │       ├── stiConfig.ts              # STI incubation period config
│   │       └── database.ts               # Database connection helper
│   ├── shared/
│   │   └── stiConfig.json              # Shared STI configuration
│   ├── package.json
│   └── tsconfig.json
├── firestore.rules                     # Security rules
├── firestore.indexes.json              # Composite indexes
└── firebase.json                       # Firebase project config
```

## Cloud Functions

### 1. processExposureReport (Firestore Trigger)

**Trigger:** Document created in `reports/{reportId}`

**Purpose:** Process new exposure reports and propagate notifications.

**Flow:**
1. Validate report data (reporterId, stiTypes, testDate, privacyLevel)
2. Update report status to `PROCESSING`
3. Discover contacts via unidirectional query (`partnerAnonymousId == reporterId`)
4. Create notifications for discovered contacts
5. Recursively propagate through chain (up to 10 hops)
6. Update report status to `COMPLETED`

**Key Security Feature:** The backend discovers contacts automatically - clients cannot provide contact lists. This prevents false reporting.

### 2. updateTestStatus (Firestore Trigger)

**Trigger:** Document written in `testStatuses/{userId}`

**Purpose:** Propagate negative test results through exposure chains.

**Flow:**
1. Detect when a user reports a negative test result
2. Find all notifications where this user appears in the chain path
3. Update chain visualization with `NEGATIVE` status
4. Send push notifications about reduced risk to downstream users

### 3. cleanupExpiredData (Scheduled)

**Trigger:** Daily at 3:00 AM UTC (`0 3 * * *`)

**Purpose:** Delete data older than 180 days for privacy compliance.

**Cleans:**
- `interactions` (by `recordedAt`)
- `notifications` (by `receivedAt`)
- `reports` (by `reportedAt`)

Cleanup statistics are logged to `cleanupLogs` collection.

### 4. HTTPS Callable Functions

| Function | Purpose |
|----------|---------|
| `reportNegativeTest` | User reports a negative test result |
| `reportPositiveTest` | User reports a positive test with chain linking |
| `getChainLinkInfo` | Check if user has existing exposure notifications |
| `triggerCleanup` | Admin-only manual cleanup trigger |

## Firestore Collections & Schemas

### users

User profile documents. Document ID is the Firebase UID.

```typescript
{
  anonymousId: string,          // Firebase UID (for display/backup)
  username: string,             // Optional username
  createdAt: number,            // Timestamp
  fcmToken?: string,            // Push notification token
  hashedInteractionId: string,  // SHA256(uid.uppercase()) - for queries
  hashedNotificationId: string  // SHA256("notification:" + uid.uppercase())
}
```

### interactions

BLE interaction records.

```typescript
{
  ownerId: string,              // SHA256(uid.uppercase())
  partnerAnonymousId: string,   // SHA256(partner_uid.uppercase())
  partnerUsernameSnapshot: string,
  recordedAt: number            // Timestamp
}
```

### notifications

Exposure notifications.

```typescript
{
  recipientId: string,          // SHA256("notification:" + uid.uppercase())
  type: "EXPOSURE" | "UPDATE",
  stiType?: string,             // JSON array, respects privacy level
  exposureDate?: number,        // Respects privacy level
  chainData: string,            // JSON serialized ChainVisualization
  chainPath: string[],          // Primary path (hashed with "chain:" prefix)
  chainPaths?: string,          // All paths (JSON serialized for Firestore)
  hopDepth: number,             // Distance from original reporter (1 = direct)
  isRead: boolean,
  receivedAt: number,
  updatedAt: number,
  reportId: string              // Reference to original report
}
```

### reports

Exposure report documents.

```typescript
{
  reporterId: string,                   // SHA256("report:" + uid.uppercase())
  reporterInteractionHashedId: string,  // SHA256(uid.uppercase()) - for queries
  stiTypes: string,                     // JSON array, e.g., '["HIV", "SYPHILIS"]'
  testDate: number,
  privacyLevel: "FULL" | "STI_ONLY" | "DATE_ONLY" | "ANONYMOUS",
  reportedAt: number,
  status: "pending" | "processing" | "completed" | "failed",
  processedAt?: number,
  error?: string,
  linkedReportId?: string               // Chain linking to original report
}
```

### testStatuses

Negative test status records.

```typescript
{
  userId: string,               // Unhashed Firebase UID
  testResult: "POSITIVE" | "NEGATIVE" | "UNKNOWN",
  stiType?: string,
  testedAt: number,
  updatedAt: number
}
```

## Security Model

### Domain-Separated Hashing

To prevent cross-collection correlation attacks if the database is breached, UIDs are hashed with domain-specific salt prefixes:

| Collection | Field | Hash Formula |
|------------|-------|--------------|
| interactions | ownerId | `SHA256(uid.uppercase())` |
| interactions | partnerAnonymousId | `SHA256(uid.uppercase())` |
| notifications | recipientId | `SHA256("notification:" + uid.uppercase())` |
| notifications | chainPath | `SHA256("chain:" + uid.uppercase())` |
| reports | reporterId | `SHA256("report:" + uid.uppercase())` |

**Key Insight:** The same UID produces different hashes in different collections, preventing correlation even if all collections are breached.

### Server-Side Hash Computation

Security rules compute hashes server-side using `request.auth.uid`:

```javascript
function hashForNotification(uid) {
  return hashing.sha256(("notification:" + uid.upper()).toUtf8()).toHexString();
}
```

Pre-computed hashes from clients cannot bypass authorization.

### Unidirectional Graph Traversal

The most critical security feature: **contacts are discovered ONLY by querying `partnerAnonymousId == reporterId`**.

**Why this matters:**
- A user is notified only if **THEY** recorded the interaction
- Reporter A cannot falsely claim interaction with User B to get B notified
- Only users who independently recorded the interaction receive notifications

**Example:**
```
User A reports positive
User B is notified ONLY if B has a record showing B interacted with A
A cannot falsely claim to have interacted with B
```

## Chain Propagation Logic

### Contact Discovery Flow

```
1. User A reports positive test
2. Backend queries: WHERE partnerAnonymousId == hash(A.uid)
3. Finds interactions where other users recorded A as their partner
4. Creates notifications for discovered contacts
5. Recursively propagates (up to MAX_CHAIN_DEPTH = 10)
```

### Rolling Window Model

Each hop in the chain uses the interaction date as the new window start:

```
A tested on Dec 20, window = Dec 20 - incubation period
B interacted with A on Dec 10 → B's window starts from Dec 10
C interacted with B on Dec 15 → C's window starts from Dec 15
```

This ensures epidemiologically accurate exposure windows per hop.

### Multi-Path Deduplication

When a user is reachable via multiple paths (e.g., A→B→D and A→C→D):

- They receive exactly ONE notification
- All paths are stored in `chainPaths` for visualization
- The shortest path determines `hopDepth`
- Additional paths are merged via Firestore transactions

### Chain Depth Limit

Maximum chain depth is 10 hops based on:
1. Beyond 10 hops, exposure risk becomes negligible
2. Most real-world STI transmission chains are shorter than 10 hops
3. Prevents excessive notifications for distant contacts
4. Reduces computational cost and notification fatigue

## Push Notifications

### FCM Configuration

Notifications use high priority with the `exposure_notifications` channel:

```typescript
{
  android: {
    priority: "high",
    notification: {
      channelId: "exposure_notifications",
      priority: "high",
      defaultSound: true,
      defaultVibrateTimings: true
    }
  }
}
```

### Notification Types

| Type | Title | When Sent |
|------|-------|-----------|
| EXPOSURE | "Potential Exposure Alert" | New exposure detected |
| UPDATE | "Exposure Status Update" | Chain member tests negative |

### Privacy in Push Notifications

STI type is intentionally **NOT** included in push notification body. Users must open the app to see sensitive health details. This prevents health information from appearing on lock screens.

### Token Management

- Invalid FCM tokens are automatically cleared
- Batch notifications use `sendEachForMulticast` for efficiency
- Token refresh is handled transparently

## Data Retention

### 180-Day Retention Period

All data is automatically deleted after 180 days:
- Covers maximum STI incubation period (HPV: 30-180 days)
- Daily cleanup runs at 3:00 AM UTC
- Batch deletion in groups of 500 documents

### Privacy Compliance

- Anonymous authentication only
- No PII stored (usernames are optional display names)
- Domain-separated hashing prevents correlation
- Server-side access control

## STI Configuration

Incubation periods are configured in `functions/shared/stiConfig.json`:

| STI | Max Incubation (days) |
|-----|----------------------|
| HIV | 30 |
| Syphilis | 90 |
| Gonorrhea | 14 |
| Chlamydia | 21 |
| HPV | 180 |
| Herpes | 21 |
| Other | 30 |

The exposure window calculation uses the maximum incubation period across all reported STI types.

## Development & Deployment

### Prerequisites

```bash
npm install -g firebase-tools
firebase login
```

### Local Development

```bash
cd firebase/functions
npm install
npm run build
npm run serve  # Start emulator
```

### Emulator Setup

```bash
firebase emulators:start --only functions,firestore
```

### Deploy Functions

```bash
cd firebase
firebase deploy --only functions
```

### Deploy Security Rules

```bash
firebase deploy --only firestore:rules
```

### Deploy Indexes

```bash
firebase deploy --only firestore:indexes
```

### View Logs

```bash
firebase functions:log
```

## Testing

### Run Unit Tests

```bash
cd firebase/functions
npm test
```

### Manual Testing with Emulator

1. Start emulators: `firebase emulators:start`
2. Access Firestore UI at `http://localhost:4000/firestore`
3. Create test documents to trigger functions
4. Check function logs in emulator UI

### Testing Chain Propagation

1. Create test users with `hashedInteractionId` and `hashedNotificationId`
2. Create interaction documents linking users
3. Create a report document to trigger chain propagation
4. Verify notifications are created for expected recipients

## Localization

Push notifications support multiple languages:

| Language | Code |
|----------|------|
| English | en |
| German | de |

Localized strings are defined in `types.ts` under `LOCALIZED_STRINGS`.
