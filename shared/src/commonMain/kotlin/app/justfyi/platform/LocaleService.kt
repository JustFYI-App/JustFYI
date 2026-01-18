package app.justfyi.platform

/**
 * Platform-agnostic interface for locale/language operations.
 * Implemented in platform-specific source sets.
 */
interface LocaleService {
    /**
     * Supported language codes.
     */
    object Languages {
        const val ENGLISH = "en"
        const val GERMAN = "de"
        const val SPANISH = "es"
        const val PORTUGUESE = "pt"
        const val FRENCH = "fr"
        const val SYSTEM_DEFAULT = "system"

        val SUPPORTED_CODES = listOf(ENGLISH, GERMAN, SPANISH, PORTUGUESE, FRENCH)
    }

    /**
     * Whether the platform supports runtime locale changes.
     * Android: true (supports per-app language settings)
     * iOS: false (must use system language settings)
     */
    val supportsRuntimeLocaleChange: Boolean

    /**
     * Gets the system language code.
     * Returns the device's current language setting.
     *
     * @return The system language code (e.g., "en", "de")
     */
    fun getSystemLanguageCode(): String

    /**
     * Gets the effective language code to use for the app.
     * On platforms that don't support runtime locale change, this returns
     * the system language if supported, otherwise defaults to English.
     *
     * @param savedLanguage The user's saved language preference (if any)
     * @return The effective language code to use
     */
    fun getEffectiveLanguageCode(savedLanguage: String?): String {
        // On platforms that support runtime change, use the saved preference
        if (supportsRuntimeLocaleChange) {
            return savedLanguage ?: Languages.SYSTEM_DEFAULT
        }

        // On iOS, use system language if supported, otherwise default to English
        val systemLang = getSystemLanguageCode()
        return if (systemLang in Languages.SUPPORTED_CODES) {
            systemLang
        } else {
            Languages.ENGLISH
        }
    }

    /**
     * Sets the app locale.
     *
     * @param languageCode The language code to set, or null/SYSTEM_DEFAULT for device default
     */
    fun setAppLocale(languageCode: String?)

    /**
     * Gets the display name for a language code.
     *
     * @param code The language code
     * @return The display name for the language
     */
    fun getLanguageDisplayName(code: String?): String
}
