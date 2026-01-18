package app.justfyi.util

import android.content.Context
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages app localization and language switching.
 * Follows device language by default with in-app override capability.
 */
object LocaleManager {
    object Languages {
        const val ENGLISH = "en"
        const val GERMAN = "de"
        const val SPANISH = "es"
        const val PORTUGUESE = "pt"
        const val FRENCH = "fr"
        const val SYSTEM_DEFAULT = "system"
    }

    data class LanguageOption(
        val code: String,
        val displayName: String,
        val nativeDisplayName: String,
    )

    val supportedLanguages =
        listOf(
            LanguageOption(Languages.SYSTEM_DEFAULT, "System Default", "System Default"),
            LanguageOption(Languages.ENGLISH, "English", "English"),
            LanguageOption(Languages.GERMAN, "German", "Deutsch"),
            LanguageOption(Languages.SPANISH, "Spanish", "Español"),
            LanguageOption(Languages.PORTUGUESE, "Portuguese", "Português"),
            LanguageOption(Languages.FRENCH, "French", "Français"),
        )

    private val _currentLanguage = MutableStateFlow(Languages.SYSTEM_DEFAULT)
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    /**
     * Gets the current device locale.
     */
    fun getDeviceLocale(): Locale = LocaleList.getDefault().get(0) ?: Locale.getDefault()

    /**
     * Gets the effective locale based on settings.
     */
    fun getEffectiveLocale(languageCode: String?): Locale =
        when (languageCode) {
            null, Languages.SYSTEM_DEFAULT -> {
                val deviceLocale = getDeviceLocale()
                if (isLanguageSupported(deviceLocale.language)) {
                    deviceLocale
                } else {
                    Locale.forLanguageTag(Languages.ENGLISH)
                }
            }
            else -> Locale.forLanguageTag(languageCode)
        }

    /**
     * Checks if a language code is supported.
     */
    fun isLanguageSupported(languageCode: String): Boolean =
        languageCode in
            listOf(
                Languages.ENGLISH,
                Languages.GERMAN,
                Languages.SPANISH,
                Languages.PORTUGUESE,
                Languages.FRENCH,
            )

    /**
     * Sets the app locale.
     */
    fun setAppLocale(languageCode: String?) {
        val code = languageCode?.takeIf { it != Languages.SYSTEM_DEFAULT && it.isNotEmpty() }
        _currentLanguage.value = code ?: Languages.SYSTEM_DEFAULT

        val locale = getEffectiveLocale(code)
        Locale.setDefault(locale)
    }

    /**
     * Gets a Context configured with the specified locale.
     */
    fun getLocalizedContext(
        context: Context,
        languageCode: String?,
    ): Context {
        val locale = getEffectiveLocale(languageCode)
        val config =
            context.resources.configuration.apply {
                setLocale(locale)
                setLocales(LocaleList(locale))
            }
        return context.createConfigurationContext(config)
    }

    /**
     * Gets the display name for a language code.
     */
    fun getLanguageDisplayName(languageCode: String?): String =
        when (languageCode) {
            null, Languages.SYSTEM_DEFAULT -> "System Default"
            Languages.ENGLISH -> "English"
            Languages.GERMAN -> "Deutsch"
            Languages.SPANISH -> "Español"
            Languages.PORTUGUESE -> "Português"
            Languages.FRENCH -> "Français"
            else -> languageCode
        }

    /**
     * Gets the native display name for a language code.
     */
    fun getLanguageNativeDisplayName(languageCode: String?): String =
        when (languageCode) {
            null, Languages.SYSTEM_DEFAULT -> {
                val deviceLang = getDeviceLocale().language
                getLanguageNativeDisplayName(deviceLang)
            }
            Languages.ENGLISH -> "English"
            Languages.GERMAN -> "Deutsch"
            Languages.SPANISH -> "Español"
            Languages.PORTUGUESE -> "Português"
            Languages.FRENCH -> "Français"
            else -> languageCode
        }
}
