package app.justfyi.platform

import app.justfyi.util.LocaleManager
import dev.zacsweers.metro.Inject

/**
 * Android implementation of LocaleService.
 * Delegates to LocaleManager for actual implementation.
 */
@Inject
class AndroidLocaleService : LocaleService {
    /**
     * Android supports runtime locale changes via per-app language settings.
     */
    override val supportsRuntimeLocaleChange: Boolean = true

    /**
     * Gets the system language code.
     *
     * @return The system language code (e.g., "en", "de")
     */
    override fun getSystemLanguageCode(): String = LocaleManager.getDeviceLocale().language

    override fun setAppLocale(languageCode: String?) {
        LocaleManager.setAppLocale(languageCode)
    }

    override fun getLanguageDisplayName(code: String?): String =
        LocaleManager.getLanguageDisplayName(
            if (code == LocaleService.Languages.SYSTEM_DEFAULT) null else code,
        )
}
