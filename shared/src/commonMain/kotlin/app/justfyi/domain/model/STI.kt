package app.justfyi.domain.model

/**
 * Enumeration of STI types that can be reported.
 * Each STI has an associated maximum incubation period in days
 * used for calculating the exposure window.
 *
 * Incubation periods are based on medical literature:
 * - HIV: 14-30 days (use 30)
 * - Syphilis: 10-90 days (use 90)
 * - Gonorrhea: 1-14 days (use 14)
 * - Chlamydia: 7-21 days (use 21)
 * - HPV: 30-180 days (use 180)
 * - Herpes: 2-12 days (use 12)
 * - Other: 14 days (default)
 *
 * @property displayNameKey Resource key for localized display name
 * @property maxIncubationDays Maximum incubation period in days (used for window calculation)
 */
enum class STI(
    val displayNameKey: String,
    val maxIncubationDays: Int,
) {
    HIV(
        displayNameKey = "sti_hiv",
        maxIncubationDays = 30,
    ),
    SYPHILIS(
        displayNameKey = "sti_syphilis",
        maxIncubationDays = 90,
    ),
    GONORRHEA(
        displayNameKey = "sti_gonorrhea",
        maxIncubationDays = 14,
    ),
    CHLAMYDIA(
        displayNameKey = "sti_chlamydia",
        maxIncubationDays = 21,
    ),
    HPV(
        displayNameKey = "sti_hpv",
        maxIncubationDays = 180,
    ),
    HERPES(
        displayNameKey = "sti_herpes",
        maxIncubationDays = 12,
    ),
    OTHER(
        displayNameKey = "sti_other",
        maxIncubationDays = 14,
    ),
    ;

    companion object {
        /**
         * Default fallback incubation period when STI type is unknown.
         */
        const val DEFAULT_INCUBATION_DAYS = 14

        /**
         * Parses a string to STI enum, returning null if not found.
         * Case-insensitive matching.
         *
         * @param name The name to parse
         * @return The STI enum value or null
         */
        fun fromString(name: String): STI? = entries.find { it.name.equals(name, ignoreCase = true) }

        /**
         * Parses a JSON array string of STI names to a list of STI enums.
         * Example: "[\"HIV\", \"SYPHILIS\"]" -> [STI.HIV, STI.SYPHILIS]
         *
         * @param jsonArray The JSON array string
         * @return List of STI enums (empty if parsing fails or no valid STIs)
         */
        fun fromJsonArray(jsonArray: String): List<STI> =
            try {
                // Simple parsing without full JSON library dependency
                val cleaned =
                    jsonArray
                        .removePrefix("[")
                        .removeSuffix("]")
                        .replace("\"", "")
                        .replace("'", "")
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                cleaned.mapNotNull { fromString(it) }
            } catch (e: Exception) {
                emptyList()
            }

        /**
         * Converts a list of STIs to a JSON array string.
         * Example: [STI.HIV, STI.SYPHILIS] -> "[\"HIV\",\"SYPHILIS\"]"
         *
         * @param stis The list of STIs
         * @return JSON array string representation
         */
        fun toJsonArray(stis: List<STI>): String =
            stis.joinToString(
                separator = ",",
                prefix = "[",
                postfix = "]",
            ) { "\"${it.name}\"" }
    }
}
