/**
 * Path comparison and normalization utilities for chain propagation
 *
 * These utilities handle path deduplication for group events where
 * the same users may be reached via different orderings.
 */

/**
 * Normalize a path by sorting the intermediate nodes.
 * This allows us to detect paths that have the same users in different orders.
 *
 * For group events (A, B, C, D all interacted), paths like:
 *   A -> B -> C -> D and A -> C -> B -> D
 * are equivalent from D's perspective - they represent the same group exposure.
 *
 * We keep the first and last elements (reporter and recipient) fixed,
 * and sort only the intermediate nodes to create a canonical form.
 *
 * @param path - Array of user IDs representing the path
 * @returns Normalized path with sorted intermediate nodes
 */
export function normalizePathForComparison(path: string[]): string[] {
  if (path.length <= 2) {
    return path;
  }

  // Keep first (reporter) and last (recipient), sort middle
  const first = path[0];
  const last = path[path.length - 1];
  const middle = path.slice(1, -1).sort();

  return [first, ...middle, last];
}

/**
 * Check if two paths are equivalent (same users, possibly different order).
 * Uses normalized comparison to detect group event duplicates.
 *
 * @param path1 - First path
 * @param path2 - Second path
 * @returns true if paths are equivalent
 */
export function arePathsEquivalent(path1: string[], path2: string[]): boolean {
  const normalized1 = normalizePathForComparison(path1);
  const normalized2 = normalizePathForComparison(path2);

  return normalized1.length === normalized2.length &&
    normalized1.every((id, idx) => id === normalized2[idx]);
}

/**
 * Parse chain paths from JSON string (Firestore workaround for nested arrays)
 *
 * @param chainPaths - JSON string or array of paths
 * @param fallback - Fallback paths if parsing fails
 * @returns Parsed array of paths
 */
export function parseChainPaths(
  chainPaths: string | string[][] | undefined,
  fallback?: string[][],
): string[][] {
  try {
    if (typeof chainPaths === "string") {
      return JSON.parse(chainPaths);
    }
    return chainPaths || fallback || [];
  } catch {
    return fallback || [];
  }
}
