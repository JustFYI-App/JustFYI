/**
 * STI Configuration Reader
 * Reads STI incubation periods from the shared configuration file.
 * This ensures consistency between backend and client.
 */

import * as fs from "fs";
import * as path from "path";

/**
 * STI configuration entry containing incubation period
 */
interface STIConfigEntry {
  maxDays: number;
}

/**
 * Full STI configuration mapping
 */
interface STIConfig {
  [stiName: string]: STIConfigEntry;
}

/**
 * Default incubation period in days when STI type is unknown
 */
const DEFAULT_INCUBATION_DAYS = 30;

/**
 * Cached configuration to avoid repeated file reads
 */
let cachedConfig: STIConfig | null = null;

/**
 * Loads the STI configuration from the shared JSON file.
 * The configuration is cached after first load.
 *
 * @returns The STI configuration object
 */
export function loadSTIConfig(): STIConfig {
  if (cachedConfig !== null) {
    return cachedConfig;
  }

  try {
    // Path to shared config relative to the functions directory
    // When deployed, the shared folder is copied to lib/shared
    const configPath = path.resolve(__dirname, "../../shared/stiConfig.json");

    // Check if the shared config exists at runtime location
    if (!fs.existsSync(configPath)) {
      // Fallback to hardcoded defaults if config file is not found
      console.warn(`STI config file not found at ${configPath}, using defaults`);
      cachedConfig = getDefaultConfig();
      return cachedConfig;
    }

    const configContent = fs.readFileSync(configPath, "utf-8");
    cachedConfig = JSON.parse(configContent) as STIConfig;

    // Validate that required fields exist
    validateConfig(cachedConfig);

    return cachedConfig;
  } catch (error) {
    console.error("Error loading STI config:", error);
    cachedConfig = getDefaultConfig();
    return cachedConfig;
  }
}

/**
 * Validates the loaded configuration has required structure
 *
 * @param config The configuration to validate
 * @throws Error if configuration is invalid
 */
function validateConfig(config: STIConfig): void {
  if (typeof config !== "object" || config === null) {
    throw new Error("STI config must be an object");
  }

  for (const [stiName, entry] of Object.entries(config)) {
    if (typeof entry !== "object" || entry === null) {
      throw new Error(`Invalid config entry for STI: ${stiName}`);
    }
    if (typeof entry.maxDays !== "number" || entry.maxDays <= 0) {
      throw new Error(`Invalid maxDays for STI: ${stiName}`);
    }
  }
}

/**
 * Returns the default STI configuration
 * Used as fallback when config file cannot be loaded
 */
function getDefaultConfig(): STIConfig {
  return {
    HIV: { maxDays: 30 },
    SYPHILIS: { maxDays: 90 },
    GONORRHEA: { maxDays: 14 },
    CHLAMYDIA: { maxDays: 21 },
    HPV: { maxDays: 180 },
    HERPES: { maxDays: 21 },
    OTHER: { maxDays: 30 },
  };
}

/**
 * Gets the incubation period in days for a specific STI type.
 *
 * @param stiType The STI type name (e.g., "HIV", "SYPHILIS")
 * @returns The maximum incubation period in days
 */
export function getSTIIncubationDays(stiType: string): number {
  const config = loadSTIConfig();
  const normalizedType = stiType.toUpperCase().trim();

  const entry = config[normalizedType];
  if (entry && typeof entry.maxDays === "number") {
    return entry.maxDays;
  }

  return DEFAULT_INCUBATION_DAYS;
}

/**
 * Gets the maximum incubation period for multiple STI types.
 * This is used when a report involves multiple STIs.
 *
 * @param stiTypes Array of STI type names
 * @returns The maximum incubation period across all specified STIs
 */
export function getMaxIncubationDays(stiTypes: string[]): number {
  if (!stiTypes || stiTypes.length === 0) {
    return DEFAULT_INCUBATION_DAYS;
  }

  let maxDays = 0;
  for (const stiType of stiTypes) {
    const days = getSTIIncubationDays(stiType);
    if (days > maxDays) {
      maxDays = days;
    }
  }

  return maxDays > 0 ? maxDays : DEFAULT_INCUBATION_DAYS;
}

/**
 * Parses a JSON array string of STI types and returns the max incubation days.
 * This is a convenience function for handling the stiTypes field from reports.
 *
 * @param stiTypesJson JSON array string like '["HIV", "SYPHILIS"]'
 * @returns The maximum incubation period across all specified STIs
 */
export function getMaxIncubationDaysFromJson(stiTypesJson: string): number {
  try {
    const stiTypes = JSON.parse(stiTypesJson) as string[];
    return getMaxIncubationDays(stiTypes);
  } catch {
    console.warn(`Failed to parse STI types JSON: ${stiTypesJson}`);
    return DEFAULT_INCUBATION_DAYS;
  }
}

/**
 * Clears the cached configuration.
 * Useful for testing to ensure fresh config is loaded.
 */
export function clearConfigCache(): void {
  cachedConfig = null;
}

/**
 * Exports for testing purposes
 */
export const _testing = {
  DEFAULT_INCUBATION_DAYS,
  getDefaultConfig,
  validateConfig,
};
