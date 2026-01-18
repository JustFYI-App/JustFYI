package app.justfyi.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/**
 * iOS implementation of DateTimeFormatter using NSDateFormatter.
 * Uses the system locale for proper localization.
 */
actual object DateTimeFormatter {
    /**
     * Formats a LocalDateTime with full date and time.
     * Uses long date style and short time style for locale-appropriate formatting.
     */
    actual fun formatDateTimeWithTime(dateTime: LocalDateTime): String {
        val formatter =
            NSDateFormatter().apply {
                dateStyle = NSDateFormatterLongStyle
                timeStyle = NSDateFormatterShortStyle
                locale = NSLocale.currentLocale
            }
        return formatter.stringFromDate(dateTime.toNSDate())
    }

    /**
     * Formats a LocalDate with full date (no time).
     * Uses long date style for locale-appropriate formatting.
     */
    actual fun formatDateOnly(date: LocalDate): String {
        val formatter =
            NSDateFormatter().apply {
                dateStyle = NSDateFormatterLongStyle
                timeStyle = NSDateFormatterNoStyle
                locale = NSLocale.currentLocale
            }
        return formatter.stringFromDate(date.toNSDate())
    }

    /**
     * Formats a LocalDate with abbreviated/short format.
     * Uses medium date style for locale-appropriate abbreviated formatting.
     */
    actual fun formatDateShort(date: LocalDate): String {
        val formatter =
            NSDateFormatter().apply {
                dateStyle = NSDateFormatterMediumStyle
                timeStyle = NSDateFormatterNoStyle
                locale = NSLocale.currentLocale
            }
        return formatter.stringFromDate(date.toNSDate())
    }

    /**
     * Formats a LocalDateTime with abbreviated date and time.
     * Uses medium date style and short time style for locale-appropriate formatting.
     */
    actual fun formatDateTimeShort(dateTime: LocalDateTime): String {
        val formatter =
            NSDateFormatter().apply {
                dateStyle = NSDateFormatterMediumStyle
                timeStyle = NSDateFormatterShortStyle
                locale = NSLocale.currentLocale
            }
        return formatter.stringFromDate(dateTime.toNSDate())
    }

    /**
     * Converts a kotlinx-datetime LocalDateTime to NSDate.
     */
    private fun LocalDateTime.toNSDate(): NSDate {
        val instant = this.toInstant(TimeZone.currentSystemDefault())
        return instant.toNSDate()
    }

    /**
     * Converts a kotlinx-datetime LocalDate to NSDate (at midnight).
     */
    private fun LocalDate.toNSDate(): NSDate {
        val dateTime = LocalDateTime(this.year, this.month, this.dayOfMonth, 0, 0, 0)
        return dateTime.toNSDate()
    }
}
