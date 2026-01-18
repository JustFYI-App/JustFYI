/**
 * Test setup for Firebase Cloud Functions
 * Initializes Firebase test environment with mocks
 */

// Set environment variables for Firebase emulator
process.env.FIRESTORE_EMULATOR_HOST = "localhost:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "localhost:9099";

// Create mock document reference
const createMockDocRef = () => ({
  get: jest.fn().mockResolvedValue({
    exists: false,
    data: () => null,
  }),
  set: jest.fn().mockResolvedValue(undefined),
  update: jest.fn().mockResolvedValue(undefined),
  delete: jest.fn().mockResolvedValue(undefined),
});

// Create mock collection reference
const createMockCollectionRef = () => ({
  doc: jest.fn(() => createMockDocRef()),
  add: jest.fn().mockResolvedValue({ id: "mock-doc-id" }),
  where: jest.fn().mockReturnThis(),
  limit: jest.fn().mockReturnThis(),
  orderBy: jest.fn().mockReturnThis(),
  count: jest.fn().mockReturnThis(),
  get: jest.fn().mockResolvedValue({
    exists: false,
    data: () => null,
    docs: [],
    empty: true,
  }),
});

// Create mock Firestore
const mockFirestore = {
  collection: jest.fn(() => createMockCollectionRef()),
  doc: jest.fn(() => createMockDocRef()),
  batch: jest.fn().mockReturnValue({
    set: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
    commit: jest.fn().mockResolvedValue(undefined),
  }),
  runTransaction: jest.fn().mockImplementation(async (updateFunction) => {
    // Mock transaction object
    const transaction = {
      get: jest.fn().mockResolvedValue({
        exists: false,
        data: () => null,
      }),
      set: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    };
    return updateFunction(transaction);
  }),
};

// Create mock Messaging
const mockMessaging = {
  send: jest.fn().mockResolvedValue("mock-message-id"),
  sendEachForMulticast: jest.fn().mockResolvedValue({
    successCount: 1,
    failureCount: 0,
    responses: [{ success: true }],
  }),
};

// Mock Firebase Admin module
jest.mock("firebase-admin", () => ({
  apps: [],
  initializeApp: jest.fn(),
  firestore: jest.fn(() => mockFirestore),
  messaging: jest.fn(() => mockMessaging),
  credential: {
    applicationDefault: jest.fn(),
  },
}));

// Mock firebase-admin/firestore module for getFirestore
jest.mock("firebase-admin/firestore", () => ({
  getFirestore: jest.fn(() => mockFirestore),
  FieldValue: {
    serverTimestamp: jest.fn(),
    delete: jest.fn(),
    increment: jest.fn(),
  },
}));

// Reset mocks between tests
beforeEach(() => {
  jest.clearAllMocks();
});

afterAll(() => {
  jest.clearAllMocks();
});

/**
 * Helper to create a mock Firestore snapshot
 */
export function createMockSnapshot<T>(
  data: T | null,
  id: string = "mock-id",
): {
  exists: boolean;
  data: () => T | null;
  id: string;
  ref: { update: jest.Mock; set: jest.Mock };
} {
  return {
    exists: data !== null,
    data: () => data,
    id,
    ref: {
      update: jest.fn().mockResolvedValue(undefined),
      set: jest.fn().mockResolvedValue(undefined),
    },
  };
}

/**
 * Helper to create mock query results
 */
export function createMockQuerySnapshot<T>(
  docs: Array<{ id: string; data: T }>,
): {
  docs: Array<{ id: string; data: () => T; ref: { update: jest.Mock; delete: jest.Mock } }>;
  empty: boolean;
  size: number;
} {
  return {
    docs: docs.map((doc) => ({
      id: doc.id,
      data: () => doc.data,
      ref: {
        update: jest.fn().mockResolvedValue(undefined),
        delete: jest.fn().mockResolvedValue(undefined),
      },
    })),
    empty: docs.length === 0,
    size: docs.length,
  };
}

/**
 * Helper to wait for async operations
 */
export function flushPromises(): Promise<void> {
  return new Promise((resolve) => setImmediate(resolve));
}
