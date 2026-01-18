package app.justfyi.domain.model

/**
 * Privacy options for exposure report notifications.
 * Controls what information is disclosed to notified contacts.
 *
 * These options allow the reporting user to control how much
 * detail is shared in the exposure notifications.
 *
 * @property discloseSTIType Whether to include the STI type in notifications.
 *                           If false, recipients see a generic "STI exposure" message.
 * @property discloseExposureDate Whether to include the potential exposure date.
 *                                If false, recipients see a date range instead.
 */
data class PrivacyOptions(
    val discloseSTIType: Boolean = true,
    val discloseExposureDate: Boolean = true,
) {
    /**
     * Converts to a privacy level string for storage.
     * "FULL" - both STI type and date disclosed
     * "STI_ONLY" - only STI type disclosed
     * "DATE_ONLY" - only date disclosed
     * "ANONYMOUS" - neither disclosed
     */
    fun toPrivacyLevel(): String =
        when {
            discloseSTIType && discloseExposureDate -> LEVEL_FULL
            discloseSTIType && !discloseExposureDate -> LEVEL_STI_ONLY
            !discloseSTIType && discloseExposureDate -> LEVEL_DATE_ONLY
            else -> LEVEL_ANONYMOUS
        }

    companion object {
        const val LEVEL_FULL = "FULL"
        const val LEVEL_STI_ONLY = "STI_ONLY"
        const val LEVEL_DATE_ONLY = "DATE_ONLY"
        const val LEVEL_ANONYMOUS = "ANONYMOUS"

        /**
         * Default privacy options - full disclosure.
         */
        val DEFAULT =
            PrivacyOptions(
                discloseSTIType = true,
                discloseExposureDate = true,
            )

        /**
         * Anonymous privacy options - no disclosure.
         */
        val ANONYMOUS =
            PrivacyOptions(
                discloseSTIType = false,
                discloseExposureDate = false,
            )

        /**
         * Parses a privacy level string to PrivacyOptions.
         *
         * @param level The privacy level string
         * @return PrivacyOptions corresponding to the level
         */
        fun fromPrivacyLevel(level: String): PrivacyOptions =
            when (level) {
                LEVEL_FULL -> PrivacyOptions(discloseSTIType = true, discloseExposureDate = true)
                LEVEL_STI_ONLY -> PrivacyOptions(discloseSTIType = true, discloseExposureDate = false)
                LEVEL_DATE_ONLY -> PrivacyOptions(discloseSTIType = false, discloseExposureDate = true)
                LEVEL_ANONYMOUS -> PrivacyOptions(discloseSTIType = false, discloseExposureDate = false)
                else -> DEFAULT
            }
    }
}
