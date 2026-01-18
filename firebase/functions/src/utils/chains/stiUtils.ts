/**
 * STI type parsing and matching utilities
 */

/**
 * Parse STI types from JSON string
 *
 * @param stiTypesJson - JSON string containing STI types array (e.g., '["HIV", "SYPHILIS"]')
 * @returns Array of STI type strings
 */
export function parseStiTypes(stiTypesJson: string): string[] {
  try {
    const parsed = JSON.parse(stiTypesJson);
    if (Array.isArray(parsed)) {
      return parsed.filter((item): item is string => typeof item === "string");
    }
    console.warn(`parseStiTypes: Expected array but got ${typeof parsed}`);
    return [];
  } catch {
    // If parsing fails, treat the string as a single STI type
    console.warn(`parseStiTypes: Failed to parse JSON, treating as single value: ${stiTypesJson?.substring(0, 50)}`);
    return stiTypesJson ? [stiTypesJson] : [];
  }
}

/**
 * Check if STI types match (for filtering negative test updates)
 *
 * @param notificationStiType - The STI type(s) from the notification (can be JSON array or single value)
 * @param reportedStiType - The STI type(s) reported in the test (can be JSON array or single value)
 * @returns true if any of the reported STI types match any of the notification's STI types
 */
export function stiTypesMatch(
  notificationStiType: string | undefined,
  reportedStiType: string | undefined,
): boolean {
  // If no STI type specified in the report, update all notifications
  if (!reportedStiType) {
    return true;
  }

  // If notification has no STI type (anonymous), include it
  if (!notificationStiType) {
    return true;
  }

  // Parse both STI types (both could be JSON arrays)
  const notificationTypes = parseStiTypes(notificationStiType);
  const reportedTypes = parseStiTypes(reportedStiType);

  // If no notification types parsed, fall back to direct comparison
  if (notificationTypes.length === 0) {
    // Check if any reported type matches the raw notification string
    return reportedTypes.length === 0
      ? notificationStiType.toUpperCase() === reportedStiType.toUpperCase()
      : reportedTypes.some((type) => type.toUpperCase() === notificationStiType.toUpperCase());
  }

  // If no reported types parsed, check if raw string matches any notification type
  if (reportedTypes.length === 0) {
    return notificationTypes.some(
      (type) => type.toUpperCase() === reportedStiType.toUpperCase()
    );
  }

  // Check if any reported STI type matches any of the notification's types
  return reportedTypes.some((reportedType) =>
    notificationTypes.some(
      (notifType) => notifType.toUpperCase() === reportedType.toUpperCase()
    )
  );
}
