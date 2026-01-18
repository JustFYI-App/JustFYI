/**
 * Database utility for Just FYI Cloud Functions
 * Provides access to the default Firestore database
 */

import { getFirestore, Firestore } from "firebase-admin/firestore";

/**
 * Get the Firestore instance for the Just FYI database
 * Uses the default Firestore database
 */
export function getDb(): Firestore {
  return getFirestore();
}
