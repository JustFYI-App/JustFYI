package app.justfyi.screenshots

/**
 * Marketing messages for Play Store screenshots.
 * Each language contains messages for all 5 screen types.
 *
 * Structure: Map<languageCode, Map<screenType, message>>
 * Languages: en (English), de (German), es (Spanish), fr (French), pt (Portuguese)
 * Screen types: scanning, users_detected, profile, notification, history
 */
object MarketingMessages {

    /**
     * Screen type constants matching the screenshot test parameterization.
     */
    object ScreenType {
        const val SCANNING = "scanning"
        const val USERS_DETECTED = "users_detected"
        const val PROFILE = "profile"
        const val NOTIFICATION = "notification"
        const val HISTORY = "history"
    }

    /**
     * Language code constants for supported locales.
     */
    object Language {
        const val ENGLISH = "en"
        const val GERMAN = "de"
        const val SPANISH = "es"
        const val FRENCH = "fr"
        const val PORTUGUESE = "pt"
    }

    /**
     * Translation map: languageCode -> screenType -> marketing message
     */
    val translations: Map<String, Map<String, String>> = mapOf(
        // English (source language)
        Language.ENGLISH to mapOf(
            ScreenType.SCANNING to "Stay anonymous. Stay safe.",
            ScreenType.USERS_DETECTED to "Connect with people around you",
            ScreenType.PROFILE to "Your identity stays yours",
            ScreenType.NOTIFICATION to "Get notified. Take action.",
            ScreenType.HISTORY to "Track your encounters privately"
        ),

        // German translations
        Language.GERMAN to mapOf(
            ScreenType.SCANNING to "Bleib anonym. Bleib sicher.",
            ScreenType.USERS_DETECTED to "Verbinde dich mit Menschen um dich",
            ScreenType.PROFILE to "Deine Identitat bleibt deine",
            ScreenType.NOTIFICATION to "Werde informiert. Handle sofort.",
            ScreenType.HISTORY to "Verfolge deine Kontakte privat"
        ),

        // Spanish translations
        Language.SPANISH to mapOf(
            ScreenType.SCANNING to "Mantente anonimo. Mantente seguro.",
            ScreenType.USERS_DETECTED to "Conecta con personas a tu alrededor",
            ScreenType.PROFILE to "Tu identidad sigue siendo tuya",
            ScreenType.NOTIFICATION to "Recibe alertas. Actua ya.",
            ScreenType.HISTORY to "Rastrea tus encuentros en privado"
        ),

        // French translations
        Language.FRENCH to mapOf(
            ScreenType.SCANNING to "Restez anonyme. Restez protege.",
            ScreenType.USERS_DETECTED to "Connectez-vous avec ceux qui vous entourent",
            ScreenType.PROFILE to "Votre identite reste la votre",
            ScreenType.NOTIFICATION to "Soyez alerte. Agissez vite.",
            ScreenType.HISTORY to "Suivez vos rencontres en toute discretion"
        ),

        // Portuguese translations
        Language.PORTUGUESE to mapOf(
            ScreenType.SCANNING to "Fique anonimo. Fique seguro.",
            ScreenType.USERS_DETECTED to "Conecte-se com pessoas ao seu redor",
            ScreenType.PROFILE to "Sua identidade continua sua",
            ScreenType.NOTIFICATION to "Seja notificado. Tome uma atitude.",
            ScreenType.HISTORY to "Acompanhe seus encontros com privacidade"
        )
    )

    /**
     * Get the marketing message for a specific language and screen type.
     *
     * @param languageCode The language code (en, de, es, fr, pt)
     * @param screenType The screen type (scanning, users_detected, profile, notification, history)
     * @return The localized marketing message, or English fallback if not found
     */
    fun getMessage(languageCode: String, screenType: String): String {
        return translations[languageCode]?.get(screenType)
            ?: translations[Language.ENGLISH]?.get(screenType)
            ?: "JustFYI" // Ultimate fallback
    }

    /**
     * Get all supported language codes.
     */
    val supportedLanguages: List<String> = listOf(
        Language.ENGLISH,
        Language.GERMAN,
        Language.SPANISH,
        Language.FRENCH,
        Language.PORTUGUESE
    )

    /**
     * Get all screen types.
     */
    val allScreenTypes: List<String> = listOf(
        ScreenType.SCANNING,
        ScreenType.USERS_DETECTED,
        ScreenType.PROFILE,
        ScreenType.NOTIFICATION,
        ScreenType.HISTORY
    )
}
