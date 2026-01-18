/**
 * STI Configuration Tests
 * Task 1.1: Tests for STI configuration loading
 *
 * Tests:
 * 1. Test JSON parsing and loading works correctly
 * 2. Test validation of required fields (stiName, maxDays)
 * 3. Test fallback behavior for missing/unknown STIs
 * 4. Test getMaxIncubationDays for multiple STIs
 */

import {
  getSTIIncubationDays,
  getMaxIncubationDays,
  getMaxIncubationDaysFromJson,
  clearConfigCache,
  _testing,
} from "../utils/stiConfig";

describe("STI Configuration", () => {
  beforeEach(() => {
    // Clear cache before each test to ensure fresh config
    clearConfigCache();
  });

  describe("Test 1: JSON parsing and configuration loading", () => {
    it("should load and return correct incubation days for known STIs", () => {
      // Test all known STI types from the config
      expect(getSTIIncubationDays("HIV")).toBe(30);
      expect(getSTIIncubationDays("SYPHILIS")).toBe(90);
      expect(getSTIIncubationDays("GONORRHEA")).toBe(14);
      expect(getSTIIncubationDays("CHLAMYDIA")).toBe(21);
      expect(getSTIIncubationDays("HPV")).toBe(180);
      expect(getSTIIncubationDays("HERPES")).toBe(21);
      expect(getSTIIncubationDays("OTHER")).toBe(30);
    });

    it("should handle case-insensitive STI type names", () => {
      expect(getSTIIncubationDays("hiv")).toBe(30);
      expect(getSTIIncubationDays("Syphilis")).toBe(90);
      expect(getSTIIncubationDays("GONORRHEA")).toBe(14);
      expect(getSTIIncubationDays("  chlamydia  ")).toBe(21);
    });
  });

  describe("Test 2: Validation of required fields", () => {
    it("should have valid default config with all required fields", () => {
      const defaultConfig = _testing.getDefaultConfig();

      // Verify all STIs have maxDays defined
      for (const [stiName, entry] of Object.entries(defaultConfig)) {
        expect(typeof entry.maxDays).toBe("number");
        expect(entry.maxDays).toBeGreaterThan(0);
        expect(stiName).toBeTruthy();
      }
    });

    it("should throw error for invalid config entries during validation", () => {
      // Test validation with invalid maxDays
      expect(() => {
        _testing.validateConfig({
          HIV: { maxDays: -1 },
        });
      }).toThrow("Invalid maxDays for STI: HIV");

      // Test validation with missing maxDays
      expect(() => {
        _testing.validateConfig({
          HIV: {} as { maxDays: number },
        });
      }).toThrow();
    });
  });

  describe("Test 3: Fallback behavior for missing/unknown STIs", () => {
    it("should return default incubation days for unknown STI types", () => {
      const unknownResult = getSTIIncubationDays("UNKNOWN_STI");
      expect(unknownResult).toBe(_testing.DEFAULT_INCUBATION_DAYS);

      const emptyResult = getSTIIncubationDays("");
      expect(emptyResult).toBe(_testing.DEFAULT_INCUBATION_DAYS);

      const randomResult = getSTIIncubationDays("RANDOM_TYPE");
      expect(randomResult).toBe(_testing.DEFAULT_INCUBATION_DAYS);
    });

    it("should return default when getMaxIncubationDays receives empty array", () => {
      const result = getMaxIncubationDays([]);
      expect(result).toBe(_testing.DEFAULT_INCUBATION_DAYS);
    });

    it("should handle null/undefined gracefully in getMaxIncubationDays", () => {
      const resultNull = getMaxIncubationDays(null as unknown as string[]);
      expect(resultNull).toBe(_testing.DEFAULT_INCUBATION_DAYS);

      const resultUndefined = getMaxIncubationDays(undefined as unknown as string[]);
      expect(resultUndefined).toBe(_testing.DEFAULT_INCUBATION_DAYS);
    });
  });

  describe("Test 4: getMaxIncubationDays for multiple STIs", () => {
    it("should return maximum incubation period for multiple STIs", () => {
      // HIV (30) + SYPHILIS (90) + GONORRHEA (14) -> max is 90
      const result1 = getMaxIncubationDays(["HIV", "SYPHILIS", "GONORRHEA"]);
      expect(result1).toBe(90);

      // CHLAMYDIA (21) + HERPES (21) -> max is 21
      const result2 = getMaxIncubationDays(["CHLAMYDIA", "HERPES"]);
      expect(result2).toBe(21);

      // HPV (180) alone -> 180
      const result3 = getMaxIncubationDays(["HPV"]);
      expect(result3).toBe(180);
    });

    it("should parse JSON array and return max incubation days", () => {
      const jsonArray = "[\"HIV\", \"SYPHILIS\", \"CHLAMYDIA\"]";
      const result = getMaxIncubationDaysFromJson(jsonArray);
      expect(result).toBe(90); // SYPHILIS has max of 90

      const jsonSingle = "[\"HPV\"]";
      const resultSingle = getMaxIncubationDaysFromJson(jsonSingle);
      expect(resultSingle).toBe(180);
    });

    it("should return default for invalid JSON in getMaxIncubationDaysFromJson", () => {
      const invalidJson = "not-valid-json";
      const result = getMaxIncubationDaysFromJson(invalidJson);
      expect(result).toBe(_testing.DEFAULT_INCUBATION_DAYS);

      const emptyJson = "[]";
      const emptyResult = getMaxIncubationDaysFromJson(emptyJson);
      expect(emptyResult).toBe(_testing.DEFAULT_INCUBATION_DAYS);
    });

    it("should filter out unknown STIs and still return max for known ones", () => {
      // Mix of known and unknown STIs
      const result = getMaxIncubationDays(["UNKNOWN", "HIV", "INVALID", "SYPHILIS"]);
      // HIV (30) + SYPHILIS (90) = max is 90
      // Unknown STIs use default (30), so overall max is still 90
      expect(result).toBe(90);
    });
  });
});
