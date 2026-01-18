package app.justfyi.domain.usecase

import app.justfyi.domain.model.STI
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Calculator for STI incubation periods and exposure windows.
 *
 * When a user reports a positive test, the exposure window is calculated
 * backward from the test date based on the maximum incubation period
 * of the selected STI(s).
 *
 * Key calculation rules:
 * - Use the maximum incubation period when multiple STIs are selected
 * - Exposure window starts at (testDate - maxIncubationDays)
 * - Exposure window ends at the test date
 * - Window cannot exceed 180-day retention limit
 */
interface IncubationCalculator {
    /**
     * Calculates the exposure window based on selected STIs and test date.
     *
     * @param selectedSTIs List of STIs to calculate window for
     * @param testDate The date of the positive test
     * @return ExposureWindow containing start and end dates
     */
    fun calculateExposureWindow(
        selectedSTIs: List<STI>,
        testDate: LocalDate,
    ): ExposureWindow

    /**
     * Gets the maximum incubation period for a list of STIs.
     * When multiple STIs are selected, returns the maximum of all their periods.
     *
     * @param selectedSTIs List of STIs
     * @return Maximum incubation period in days
     */
    fun getMaxIncubationDays(selectedSTIs: List<STI>): Int

    /**
     * Validates that a test date is within acceptable range.
     * - Not in the future
     * - Within 180 days of today (data retention limit)
     *
     * @param testDate The date to validate
     * @return ValidationResult with success status and optional error message
     */
    fun validateTestDate(testDate: LocalDate): DateValidationResult
}

/**
 * Represents an exposure window calculated from STI incubation periods.
 *
 * @property startDate Start of the exposure window (inclusive)
 * @property endDate End of the exposure window (inclusive, typically the test date)
 * @property daysInWindow Number of days in the window
 */
data class ExposureWindow(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val daysInWindow: Int,
)

/**
 * Result of test date validation.
 *
 * @property isValid Whether the date is valid
 * @property errorType Type of validation error (if invalid)
 */
data class DateValidationResult(
    val isValid: Boolean,
    val errorType: DateValidationError? = null,
) {
    companion object {
        val VALID = DateValidationResult(isValid = true)

        fun invalid(error: DateValidationError) =
            DateValidationResult(
                isValid = false,
                errorType = error,
            )
    }
}

/**
 * Types of date validation errors.
 */
enum class DateValidationError {
    /** Date is in the future */
    FUTURE_DATE,

    /** Date is older than 180 days (beyond retention period) */
    BEYOND_RETENTION_PERIOD,
}

/**
 * Default implementation of IncubationCalculator.
 */
class IncubationCalculatorImpl(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : IncubationCalculator {
    override fun calculateExposureWindow(
        selectedSTIs: List<STI>,
        testDate: LocalDate,
    ): ExposureWindow {
        val maxDays = getMaxIncubationDays(selectedSTIs)

        // Calculate start date by subtracting max incubation period
        val startDate = testDate.minus(maxDays, DateTimeUnit.DAY)

        // Check against 180-day retention limit
        val retentionLimit = today().minus(RETENTION_DAYS, DateTimeUnit.DAY)
        val effectiveStartDate = if (startDate < retentionLimit) retentionLimit else startDate

        // Calculate actual days in window
        val daysInWindow = daysBetween(effectiveStartDate, testDate)

        return ExposureWindow(
            startDate = effectiveStartDate,
            endDate = testDate,
            daysInWindow = daysInWindow,
        )
    }

    override fun getMaxIncubationDays(selectedSTIs: List<STI>): Int {
        if (selectedSTIs.isEmpty()) {
            return STI.DEFAULT_INCUBATION_DAYS
        }
        return selectedSTIs.maxOf { it.maxIncubationDays }
    }

    override fun validateTestDate(testDate: LocalDate): DateValidationResult {
        val today = today()

        // Check if date is in the future
        if (testDate > today) {
            return DateValidationResult.invalid(DateValidationError.FUTURE_DATE)
        }

        // Check if date is beyond retention period (180 days)
        val retentionLimit = today.minus(RETENTION_DAYS, DateTimeUnit.DAY)
        if (testDate < retentionLimit) {
            return DateValidationResult.invalid(DateValidationError.BEYOND_RETENTION_PERIOD)
        }

        return DateValidationResult.VALID
    }

    private fun today(): LocalDate {
        val kotlinInstant = clock.now()
        val datetimeInstant = Instant.fromEpochMilliseconds(kotlinInstant.toEpochMilliseconds())
        return datetimeInstant.toLocalDateTime(timeZone).date
    }

    private fun daysBetween(
        start: LocalDate,
        end: LocalDate,
    ): Int {
        val startMillis = start.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val endMillis = end.atStartOfDayIn(timeZone).toEpochMilliseconds()
        return ((endMillis - startMillis) / MILLIS_PER_DAY).toInt() + 1
    }

    companion object {
        const val RETENTION_DAYS = 180
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
}
