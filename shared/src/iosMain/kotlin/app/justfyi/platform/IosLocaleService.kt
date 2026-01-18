package app.justfyi.platform

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/**
 * iOS implementation of LocaleService.
 *
 * Provides iOS-specific locale operations using NSLocale.
 * iOS does not support runtime locale switching per-app, so this implementation
 * uses the system language if supported, otherwise defaults to English.
 */
class IosLocaleService : LocaleService {
    /**
     * iOS does not support runtime locale changes.
     * The app uses the system language setting.
     */
    override val supportsRuntimeLocaleChange: Boolean = false

    /**
     * Gets the system language code from NSLocale.
     *
     * @return The system language code (e.g., "en", "de")
     */
    override fun getSystemLanguageCode(): String = NSLocale.currentLocale.languageCode

    /**
     * Sets the app locale.
     *
     * Note: iOS doesn't support runtime locale switching per-app in the same way Android does.
     * Users must change the system language in Settings. This method is provided for API
     * compatibility but has limited effect on iOS.
     *
     * @param languageCode The language code to set, or null/SYSTEM_DEFAULT for device default
     */
    override fun setAppLocale(languageCode: String?) {
        // iOS locale changes require system-level settings changes
        // The app follows the system locale by default
        // This is a no-op on iOS as runtime locale changes aren't supported
    }

    /**
     * Gets the display name for a language code.
     *
     * @param code The language code
     * @return The display name for the language
     */
    override fun getLanguageDisplayName(code: String?): String =
        when (code) {
            LocaleService.Languages.ENGLISH -> "English"
            LocaleService.Languages.GERMAN -> "Deutsch"
            LocaleService.Languages.SPANISH -> "Español"
            LocaleService.Languages.PORTUGUESE -> "Português"
            LocaleService.Languages.FRENCH -> "Français"
            LocaleService.Languages.SYSTEM_DEFAULT, null -> {
                val systemLanguage = getSystemLanguageCode()
                when (systemLanguage) {
                    LocaleService.Languages.ENGLISH -> "English (System)"
                    LocaleService.Languages.GERMAN -> "Deutsch (System)"
                    LocaleService.Languages.SPANISH -> "Español (System)"
                    LocaleService.Languages.PORTUGUESE -> "Português (System)"
                    LocaleService.Languages.FRENCH -> "Français (System)"
                    else -> "English (Default)"
                }
            }
            else -> code
        }
}
