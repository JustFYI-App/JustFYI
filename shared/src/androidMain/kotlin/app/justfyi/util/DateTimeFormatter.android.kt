package app.justfyi.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.FormatStyle
import java.util.Locale
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter

/**
 * Android implementation of DateTimeFormatter using java.time.format.DateTimeFormatter.
 * Uses the system locale for proper localization.
 */
actual object DateTimeFormatter {
    /**
     * Formats a LocalDateTime with full date and time.
     * Uses LONG date style and SHORT time style for locale-appropriate formatting.
     */
    actual fun formatDateTimeWithTime(dateTime: LocalDateTime): String {
        val formatter =
            JavaDateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
        return dateTime.toJavaLocalDateTime().format(formatter)
    }

    /**
     * Formats a LocalDate with full date (no time).
     * Uses LONG date style for locale-appropriate formatting.
     */
    actual fun formatDateOnly(date: LocalDate): String {
        val formatter =
            JavaDateTimeFormatter
                .ofLocalizedDate(FormatStyle.LONG)
                .withLocale(Locale.getDefault())
        return date.toJavaLocalDate().format(formatter)
    }

    /**
     * Formats a LocalDate with abbreviated/short format.
     * Uses MEDIUM date style for locale-appropriate abbreviated formatting.
     */
    actual fun formatDateShort(date: LocalDate): String {
        val formatter =
            JavaDateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
        return date.toJavaLocalDate().format(formatter)
    }

    /**
     * Formats a LocalDateTime with abbreviated date and time.
     * Uses MEDIUM date style and SHORT time style for locale-appropriate formatting.
     */
    actual fun formatDateTimeShort(dateTime: LocalDateTime): String {
        val formatter =
            JavaDateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
        return dateTime.toJavaLocalDateTime().format(formatter)
    }
}
