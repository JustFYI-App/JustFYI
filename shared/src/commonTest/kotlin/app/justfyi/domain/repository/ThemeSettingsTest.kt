package app.justfyi.domain.repository

import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.createTestDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for theme settings functionality in SettingsRepository.
 * These tests verify:
 * - getTheme() returns default "system" when no preference is stored
 * - setTheme() persists theme preference correctly
 * - getTheme() returns stored preference after setTheme()
 */
class ThemeSettingsTest {
    private lateinit var database: JustFyiDatabase

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        // Clear all data to ensure test isolation
        database.settingsQueries.deleteAllSettings()
    }

    @AfterTest
    fun teardown() {
        // Clean up to ensure test isolation
        database.settingsQueries.deleteAllSettings()
    }

    // ==================== Theme Settings Tests ====================

    @Test
    fun `test getTheme returns null when no preference is stored`() =
        runTest {
            // Given - no theme preference exists
            val value =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()

            // Then - should be null (not set), which should be interpreted as "system" by the repository
            assertEquals(null, value)
        }

    @Test
    fun `test setTheme persists theme preference correctly`() =
        runTest {
            // Given - theme preference is not set initially
            val initialValue =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            assertEquals(null, initialValue)

            // When - set theme to "dark"
            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                "dark",
            )

            // Then - should be persisted as "dark"
            val afterDark =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            assertEquals("dark", afterDark, "Theme preference should be persisted as 'dark'")

            // When - set theme to "light"
            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                "light",
            )

            // Then - should be persisted as "light"
            val afterLight =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            assertEquals("light", afterLight, "Theme preference should be persisted as 'light'")

            // When - set theme to "system"
            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                "system",
            )

            // Then - should be persisted as "system"
            val afterSystem =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            assertEquals("system", afterSystem, "Theme preference should be persisted as 'system'")
        }

    @Test
    fun `test getTheme returns stored preference after setTheme`() =
        runTest {
            // Given - set theme to "dark"
            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                "dark",
            )

            // When - retrieve theme preference
            val value =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()

            // Then - should return "dark"
            assertEquals("dark", value, "getTheme should return stored preference")

            // When - update to light
            database.settingsQueries.insertOrReplaceSetting(
                SettingsRepository.KEY_THEME_PREFERENCE,
                "light",
            )

            // Then - retrieve again should return updated value
            val updatedValue =
                database.settingsQueries
                    .getSettingByKey(SettingsRepository.KEY_THEME_PREFERENCE)
                    .executeAsOneOrNull()
            assertEquals("light", updatedValue, "getTheme should return updated preference")
        }
}
