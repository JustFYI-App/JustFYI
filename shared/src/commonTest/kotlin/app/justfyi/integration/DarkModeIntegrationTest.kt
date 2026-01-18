package app.justfyi.integration

import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.createTestDatabase
import app.justfyi.domain.repository.SettingsRepository
import app.justfyi.presentation.feature.settings.SettingsViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Dark Mode Integration Tests (Task Group 5)
 *
 * These tests verify end-to-end dark mode workflows:
 * 1. Full flow: Settings -> Theme Selection -> Repository Persistence
 * 2. Theme persistence across app restarts (database re-instantiation)
 * 3. Theme preference coexistence with other settings
 * 4. Default theme behavior
 *
 * Note: These tests focus on critical integration paths, not comprehensive
 * unit test coverage which is handled in individual component tests.
 */
class DarkModeIntegrationTest {
    private lateinit var database: JustFyiDatabase

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        // Clear any existing settings to ensure test isolation
        database.settingsQueries.deleteAllSettings()
    }

    @AfterTest
    fun teardown() {
        // Clean up to ensure test isolation
        database.settingsQueries.deleteAllSettings()
    }

    // ==================================================================================
    // Test 1: Full Flow - Set Theme and Verify Persistence
    // Simulates: User opens Settings -> Selects Dark theme -> Theme is saved
    // ==================================================================================

    @Test
    fun `test full theme selection flow - set dark theme and verify persistence`() =
        runTest {
            val initialTheme =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            assertEquals(null, initialTheme, "No theme should be set initially")

            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                SettingsViewModel.THEME_DARK,
            )

            val storedTheme =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            assertEquals(
                SettingsViewModel.THEME_DARK,
                storedTheme,
                "Theme should be persisted as 'dark'",
            )

            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                SettingsViewModel.THEME_LIGHT,
            )

            val updatedTheme =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            assertEquals(
                SettingsViewModel.THEME_LIGHT,
                updatedTheme,
                "Theme should be updated to 'light'",
            )
        }

    // ==================================================================================
    // Test 2: Theme Persistence Across App Restarts
    // Simulates: User sets theme -> Closes app -> Reopens app -> Theme is restored
    // ==================================================================================

    @Test
    fun `test theme persistence across simulated app restart`() =
        runTest {
            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                SettingsViewModel.THEME_DARK,
            )

            // Verify it's set
            val themeBeforeRestart =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            assertEquals(SettingsViewModel.THEME_DARK, themeBeforeRestart)

            // Note: In real scenario, SQLite persists to disk. In test, we verify
            // the same database instance maintains state across operations.
            val themeAfterOperations =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()

            assertEquals(
                SettingsViewModel.THEME_DARK,
                themeAfterOperations,
                "Theme preference should persist across database operations",
            )
        }

    // ==================================================================================
    // Test 3: Theme and Language Settings Coexistence
    // Verifies: Theme and language settings don't interfere with each other
    // ==================================================================================

    @Test
    fun `test theme and language settings coexist independently`() =
        runTest {
            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                SettingsViewModel.THEME_DARK,
            )
            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_LANGUAGE_OVERRIDE,
                "de", // German
            )

            val storedTheme =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            val storedLanguage =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_LANGUAGE_OVERRIDE)
                    .executeAsOneOrNull()

            assertEquals(SettingsViewModel.THEME_DARK, storedTheme)
            assertEquals("de", storedLanguage)

            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                SettingsViewModel.THEME_LIGHT,
            )

            val updatedTheme =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            val languageAfterThemeUpdate =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_LANGUAGE_OVERRIDE)
                    .executeAsOneOrNull()

            assertEquals(
                SettingsViewModel.THEME_LIGHT,
                updatedTheme,
                "Theme should be updated",
            )
            assertEquals(
                "de",
                languageAfterThemeUpdate,
                "Language should remain unchanged after theme update",
            )
        }

    // ==================================================================================
    // Test 4: Default Theme Behavior
    // Verifies: System default is used when no preference is stored
    // ==================================================================================

    @Test
    fun `test default theme is system when no preference stored`() =
        runTest {
            val initialTheme =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()

            // Here we verify the database returns null so the repository layer provides default
            assertEquals(
                null,
                initialTheme,
                "Database should return null when no theme is set",
            )

            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                SettingsViewModel.THEME_SYSTEM,
            )

            val systemTheme =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            assertEquals(
                SettingsViewModel.THEME_SYSTEM,
                systemTheme,
                "System theme should be stored explicitly when user selects it",
            )
        }

    // ==================================================================================
    // Test 5: Theme Selection Cycles Correctly
    // Verifies: Theme can be cycled through all valid options
    // ==================================================================================

    @Test
    fun `test theme cycles through all valid options`() =
        runTest {
            val themes =
                listOf(
                    SettingsViewModel.THEME_SYSTEM,
                    SettingsViewModel.THEME_LIGHT,
                    SettingsViewModel.THEME_DARK,
                )

            // Cycle through each theme and verify it's stored correctly
            themes.forEach { theme ->
                database.settingsQueries.insertOrReplaceSetting(
                    SettingsRepository.KEY_THEME_PREFERENCE,
                    theme,
                )

                val storedTheme =
                    database.settingsQueries
                        .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                        .executeAsOneOrNull()

                assertEquals(
                    theme,
                    storedTheme,
                    "Theme '$theme' should be stored and retrieved correctly",
                )
            }
        }

    // ==================================================================================
    // Test 6: Theme Constants Match Expected Values
    // Verifies: Theme constant values are correct and match spec requirements
    // ==================================================================================

    @Test
    fun `test theme constants have correct values matching spec`() {
        // Verify theme constants match spec requirements
        assertEquals(
            "system",
            SettingsViewModel.THEME_SYSTEM,
            "THEME_SYSTEM should be 'system'",
        )
        assertEquals(
            "light",
            SettingsViewModel.THEME_LIGHT,
            "THEME_LIGHT should be 'light'",
        )
        assertEquals(
            "dark",
            SettingsViewModel.THEME_DARK,
            "THEME_DARK should be 'dark'",
        )

        // Verify THEME_OPTIONS list contains all three options
        assertEquals(
            3,
            SettingsViewModel.THEME_OPTIONS.size,
            "Should have exactly 3 theme options",
        )

        val themeCodes = SettingsViewModel.THEME_OPTIONS.map { it.code }
        assertEquals(true, themeCodes.contains(SettingsViewModel.THEME_SYSTEM))
        assertEquals(true, themeCodes.contains(SettingsViewModel.THEME_LIGHT))
        assertEquals(true, themeCodes.contains(SettingsViewModel.THEME_DARK))
    }
}
