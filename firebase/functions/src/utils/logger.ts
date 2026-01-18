/**
 * Logging utilities for Cloud Functions
 *
 * Provides structured logging for Cloud Functions.
 */

/**
 * Log informational messages.
 *
 * @param message - The info message to log
 * @param args - Additional arguments to log
 */
export function logInfo(message: string, ...args: unknown[]): void {
  console.log(`[INFO] ${message}`, ...args);
}

/**
 * Log warning messages.
 *
 * @param message - The warning message to log
 * @param args - Additional arguments to log
 */
export function logWarn(message: string, ...args: unknown[]): void {
  console.warn(`[WARN] ${message}`, ...args);
}

/**
 * Log error messages.
 *
 * @param message - The error message to log
 * @param error - The error object
 */
export function logError(message: string, error?: unknown): void {
  console.error(`[ERROR] ${message}`, error);
}
