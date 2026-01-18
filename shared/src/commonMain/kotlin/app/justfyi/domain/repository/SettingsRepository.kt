package app.justfyi.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for app settings.
 * Pure local storage, no cloud sync.
 */
interface SettingsRepository {
    /**
     * Gets a setting value by key.
     * @param key The setting key
     * @return The setting value, or null if not set
     */
    suspend fun getSetting(key: String): String?

    /**
     * Sets a setting value.
     * @param key The setting key
     * @param value The value to set
     */
    suspend fun setSetting(
        key: String,
        value: String,
    )

    /**
     * Gets the language override setting.
     * @return The language code (e.g., "en", "de"), or null to use device default
     */
    suspend fun getLanguage(): String?

    /**
     * Sets the language override.
     * @param languageCode The language code (e.g., "en", "de")
     */
    suspend fun setLanguage(languageCode: String)

    /**
     * Gets the theme preference setting.
     * @return The theme preference ("system", "light", or "dark"), defaults to "system" if not set
     */
    suspend fun getTheme(): String

    /**
     * Sets the theme preference.
     * @param theme The theme preference ("system", "light", or "dark")
     */
    suspend fun setTheme(theme: String)

    /**
     * Observes a setting value for reactive updates.
     * @param key The setting key
     * @return Flow emitting the current value and updates
     */
    fun observeSetting(key: String): Flow<String?>

    /**
     * Checks if privacy policy has been accepted.
     */
    suspend fun isPrivacyPolicyAccepted(): Boolean

    /**
     * Sets privacy policy acceptance status.
     * @param accepted Whether the privacy policy has been accepted
     */
    suspend fun setPrivacyPolicyAccepted(accepted: Boolean)

    /**
     * Checks if terms of service have been accepted.
     */
    suspend fun isTermsAccepted(): Boolean

    /**
     * Sets terms of service acceptance status.
     * @param accepted Whether the terms have been accepted
     */
    suspend fun setTermsAccepted(accepted: Boolean)

    /**
     * Checks if the onboarding flow has been completed.
     * @return true if onboarding is complete, false otherwise (including on fresh install)
     */
    suspend fun isOnboardingComplete(): Boolean

    /**
     * Sets the onboarding completion status.
     * @param complete Whether the onboarding has been completed
     */
    suspend fun setOnboardingComplete(complete: Boolean)

    /**
     * Observes the onboarding completion status for reactive updates.
     * @return Flow emitting the current onboarding completion status and updates
     */
    fun observeOnboardingComplete(): Flow<Boolean>

    /**
     * Deletes all settings (GDPR compliance).
     */
    suspend fun deleteAllSettings()

    companion object {
        const val KEY_LANGUAGE_OVERRIDE = "language_override"
        const val KEY_THEME_PREFERENCE = "theme_preference"
        const val KEY_PRIVACY_POLICY_ACCEPTED = "privacy_policy_accepted"
        const val KEY_TERMS_ACCEPTED = "terms_accepted"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
