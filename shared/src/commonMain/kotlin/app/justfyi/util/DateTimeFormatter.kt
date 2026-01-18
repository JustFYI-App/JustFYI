package app.justfyi.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Locale-aware date/time formatting utilities.
 *
 * Uses platform-specific formatters to ensure proper localization:
 * - Android: java.time.format.DateTimeFormatter with system locale
 * - iOS: NSDateFormatter with system locale
 *
 * This ensures dates are formatted correctly for all languages (French, German, etc.)
 */
expect object DateTimeFormatter {
    /**
     * Formats a LocalDateTime with full date and time.
     * Example (English): "January 15, 2024 at 3:30 PM"
     * Example (French): "15 janvier 2024 à 15:30"
     */
    fun formatDateTimeWithTime(dateTime: LocalDateTime): String

    /**
     * Formats a LocalDate with full date (no time).
     * Example (English): "January 15, 2024"
     * Example (French): "15 janvier 2024"
     */
    fun formatDateOnly(date: LocalDate): String

    /**
     * Formats a LocalDate with abbreviated/short format.
     * Example (English): "Jan 15"
     * Example (French): "15 janv."
     */
    fun formatDateShort(date: LocalDate): String

    /**
     * Formats a LocalDateTime with abbreviated date and time.
     * Example (English): "Jan 15, 2024, 3:30 PM"
     * Example (French): "15 janv. 2024, 15:30"
     */
    fun formatDateTimeShort(dateTime: LocalDateTime): String
}
