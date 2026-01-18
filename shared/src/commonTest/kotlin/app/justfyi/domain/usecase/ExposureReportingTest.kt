package app.justfyi.domain.usecase

import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.createTestDatabase
import app.justfyi.domain.model.PrivacyOptions
import app.justfyi.domain.model.STI
import app.justfyi.util.currentTimeMillis
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests for exposure reporting functionality.
 * These tests verify:
 * - STI selection (multi-select)
 * - Date validation (not future, within 180 days)
 * - Incubation period calculation
 * - Simplified 4-step wizard flow (contact selection now automatic on backend)
 * - Report submission
 *
 * Note: These tests focus on the business logic.
 * Firebase Cloud Function integration is tested separately.
 */

class ExposureReportingTest {
    private lateinit var database: JustFyiDatabase
    private lateinit var incubationCalculator: IncubationCalculator
    private val timeZone = TimeZone.UTC
    private val today: LocalDate
        get() {
            val kotlinInstant = Clock.System.now()
            val datetimeInstant = Instant.fromEpochMilliseconds(kotlinInstant.toEpochMilliseconds())
            return datetimeInstant.toLocalDateTime(timeZone).date
        }

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        // Clear all data to ensure test isolation
        database.exposureReportQueries.deleteAllReports()
        database.interactionQueries.deleteAllInteractions()
        database.userQueries.deleteAllUsers()
        incubationCalculator = IncubationCalculatorImpl(Clock.System, timeZone)
    }

    @AfterTest
    fun teardown() {
        // Clean up to ensure test isolation
        database.exposureReportQueries.deleteAllReports()
        database.interactionQueries.deleteAllInteractions()
        database.userQueries.deleteAllUsers()
    }

    // ==================== STI Selection Tests (Multi-Select) ====================

    @Test
    fun `test single STI selection returns correct incubation period`() {
        // Given - select HIV only
        val selectedSTIs = listOf(STI.HIV)

        // When - get max incubation days
        val maxDays = incubationCalculator.getMaxIncubationDays(selectedSTIs)

        // Then - should return HIV's incubation period (30 days)
        assertEquals(30, maxDays)
    }

    @Test
    fun `test multi-select STIs uses maximum incubation period`() {
        // Given - select multiple STIs with different incubation periods
        val selectedSTIs =
            listOf(
                STI.GONORRHEA, // 14 days
                STI.SYPHILIS, // 90 days (maximum)
                STI.CHLAMYDIA, // 21 days
            )

        // When - get max incubation days
        val maxDays = incubationCalculator.getMaxIncubationDays(selectedSTIs)

        // Then - should return the maximum (Syphilis = 90 days)
        assertEquals(90, maxDays)
    }

    @Test
    fun `test all STI types have correct incubation periods`() {
        // Verify each STI has the correct max incubation period as per spec
        assertEquals(30, STI.HIV.maxIncubationDays)
        assertEquals(90, STI.SYPHILIS.maxIncubationDays)
        assertEquals(14, STI.GONORRHEA.maxIncubationDays)
        assertEquals(21, STI.CHLAMYDIA.maxIncubationDays)
        assertEquals(180, STI.HPV.maxIncubationDays)
        assertEquals(12, STI.HERPES.maxIncubationDays)
        assertEquals(14, STI.OTHER.maxIncubationDays)
    }

    @Test
    fun `test empty STI selection returns default incubation period`() {
        // Given - no STIs selected
        val selectedSTIs = emptyList<STI>()

        // When - get max incubation days
        val maxDays = incubationCalculator.getMaxIncubationDays(selectedSTIs)

        // Then - should return default (14 days)
        assertEquals(STI.DEFAULT_INCUBATION_DAYS, maxDays)
    }

    // ==================== Date Validation Tests ====================

    @Test
    fun `test valid date passes validation`() {
        // Given - a date 30 days ago (within valid range)
        val testDate = today.minus(30, DateTimeUnit.DAY)

        // When - validate the date
        val result = incubationCalculator.validateTestDate(testDate)

        // Then - should be valid
        assertTrue(result.isValid)
        assertNull(result.errorType)
    }

    @Test
    fun `test today's date passes validation`() {
        // Given - today's date
        val testDate = today

        // When - validate the date
        val result = incubationCalculator.validateTestDate(testDate)

        // Then - should be valid
        assertTrue(result.isValid)
    }

    @Test
    fun `test future date fails validation`() {
        // Given - a date in the future
        val futureDate = today.plus(1, DateTimeUnit.DAY)

        // When - validate the date
        val result = incubationCalculator.validateTestDate(futureDate)

        // Then - should be invalid with FUTURE_DATE error
        assertFalse(result.isValid)
        assertEquals(DateValidationError.FUTURE_DATE, result.errorType)
    }

    @Test
    fun `test date beyond 180 days fails validation`() {
        // Given - a date more than 180 days ago
        val oldDate = today.minus(181, DateTimeUnit.DAY)

        // When - validate the date
        val result = incubationCalculator.validateTestDate(oldDate)

        // Then - should be invalid with BEYOND_RETENTION_PERIOD error
        assertFalse(result.isValid)
        assertEquals(DateValidationError.BEYOND_RETENTION_PERIOD, result.errorType)
    }

    @Test
    fun `test date exactly 180 days ago passes validation`() {
        // Given - a date exactly 180 days ago (boundary case)
        val boundaryDate = today.minus(180, DateTimeUnit.DAY)

        // When - validate the date
        val result = incubationCalculator.validateTestDate(boundaryDate)

        // Then - should be valid (180 days is the limit, not 179)
        assertTrue(result.isValid)
    }

    // ==================== Incubation Period Calculation Tests ====================

    @Test
    fun `test exposure window calculation for HIV`() {
        // Given - HIV selected, test date 10 days ago
        val testDate = today.minus(10, DateTimeUnit.DAY)
        val selectedSTIs = listOf(STI.HIV)

        // When - calculate exposure window
        val window = incubationCalculator.calculateExposureWindow(selectedSTIs, testDate)

        // Then - window should start 30 days before test date
        val expectedStart = testDate.minus(30, DateTimeUnit.DAY)
        assertEquals(expectedStart, window.startDate)
        assertEquals(testDate, window.endDate)
        assertEquals(31, window.daysInWindow) // 30 days inclusive
    }

    @Test
    fun `test exposure window uses maximum when multiple STIs selected`() {
        // Given - multiple STIs selected
        val testDate = today.minus(5, DateTimeUnit.DAY)
        val selectedSTIs = listOf(STI.HIV, STI.SYPHILIS, STI.GONORRHEA)

        // When - calculate exposure window
        val window = incubationCalculator.calculateExposureWindow(selectedSTIs, testDate)

        // Then - window should use Syphilis's max incubation (90 days)
        val expectedStart = testDate.minus(90, DateTimeUnit.DAY)
        assertEquals(expectedStart, window.startDate)
    }

    @Test
    fun `test exposure window respects 180 day retention limit`() {
        // Given - HPV (180 days incubation) selected, test date today
        val testDate = today
        val selectedSTIs = listOf(STI.HPV)

        // When - calculate exposure window
        val window = incubationCalculator.calculateExposureWindow(selectedSTIs, testDate)

        // Then - window start should be exactly 180 days ago (now matches retention)
        val expectedStart = today.minus(180, DateTimeUnit.DAY)
        assertEquals(expectedStart, window.startDate)
        assertEquals(testDate, window.endDate)
    }

    // ==================== Simplified 4-Step Flow Tests ====================

    @Test
    fun `test contacts in window are correctly queried`() {
        // Given - interactions at various times
        val now = currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L

        // Interaction 10 days ago (should be in window)
        database.interactionQueries.insertInteraction(
            id = "interaction-in-window",
            partner_anonymous_id = "hash-partner-1",
            partner_username_snapshot = "Partner1",
            recorded_at = now - (10 * dayMillis),
            synced_to_cloud = 1,
        )

        // Interaction 50 days ago (should also be in window for HIV)
        database.interactionQueries.insertInteraction(
            id = "interaction-also-in-window",
            partner_anonymous_id = "hash-partner-2",
            partner_username_snapshot = "Partner2",
            recorded_at = now - (50 * dayMillis),
            synced_to_cloud = 1,
        )

        // Interaction 100 days ago (outside HIV window of 30 days)
        database.interactionQueries.insertInteraction(
            id = "interaction-outside-window",
            partner_anonymous_id = "hash-partner-3",
            partner_username_snapshot = "Partner3",
            recorded_at = now - (100 * dayMillis),
            synced_to_cloud = 1,
        )

        // When - query interactions within a 60-day window
        val windowStart = now - (60 * dayMillis)
        val windowEnd = now
        val interactions =
            database.interactionQueries
                .getInteractionsInDateRange(windowStart, windowEnd)
                .executeAsList()

        // Then - should find 2 interactions (10 days and 50 days ago)
        assertEquals(2, interactions.size)
        assertTrue(interactions.any { it.id == "interaction-in-window" })
        assertTrue(interactions.any { it.id == "interaction-also-in-window" })
        assertFalse(interactions.any { it.id == "interaction-outside-window" })
    }

    @Test
    fun `test simplified flow completes without contact selection`() {
        // In the simplified 4-step flow, contact selection is automatic on the backend.
        // This test verifies the state can be complete without any contact-related data.

        // Given - state with all required fields for review step
        val state =
            ExposureReportState(
                selectedSTIs = listOf(STI.HIV),
                testDate = today.minus(5, DateTimeUnit.DAY),
                exposureWindow =
                    ExposureWindow(
                        startDate = today.minus(35, DateTimeUnit.DAY),
                        endDate = today.minus(5, DateTimeUnit.DAY),
                        daysInWindow = 31,
                    ),
                privacyOptions = PrivacyOptions.DEFAULT,
                currentStep = 4, // Review step in simplified flow
                isComplete = true, // Ready to submit
            )

        // When - check if can proceed at Review step
        val canProceed = state.canProceedToNextStep()

        // Then - should be able to proceed (contact selection is automatic on backend)
        assertTrue(canProceed, "Review step should allow submission when isComplete is true")
    }

    // ==================== Privacy Options Tests ====================

    @Test
    fun `test privacy options default to full disclosure`() {
        // Given - default privacy options
        val options = PrivacyOptions.DEFAULT

        // Then - both should be true
        assertTrue(options.discloseSTIType)
        assertTrue(options.discloseExposureDate)
        assertEquals(PrivacyOptions.LEVEL_FULL, options.toPrivacyLevel())
    }

    @Test
    fun `test privacy options anonymous mode`() {
        // Given - anonymous privacy options
        val options = PrivacyOptions.ANONYMOUS

        // Then - both should be false
        assertFalse(options.discloseSTIType)
        assertFalse(options.discloseExposureDate)
        assertEquals(PrivacyOptions.LEVEL_ANONYMOUS, options.toPrivacyLevel())
    }

    @Test
    fun `test privacy options partial disclosure`() {
        // STI only
        val stiOnly = PrivacyOptions(discloseSTIType = true, discloseExposureDate = false)
        assertEquals(PrivacyOptions.LEVEL_STI_ONLY, stiOnly.toPrivacyLevel())

        // Date only
        val dateOnly = PrivacyOptions(discloseSTIType = false, discloseExposureDate = true)
        assertEquals(PrivacyOptions.LEVEL_DATE_ONLY, dateOnly.toPrivacyLevel())
    }

    // ==================== Report Submission Tests ====================

    @Test
    fun `test report can be saved to local database without contactedIds`() {
        // Given - report data (no contactedIds needed in simplified flow)
        val reportId = "test-report-${currentTimeMillis()}"
        val stiTypes = STI.toJsonArray(listOf(STI.HIV, STI.SYPHILIS))
        val testDate = currentTimeMillis()
        val privacyLevel = PrivacyOptions.DEFAULT.toPrivacyLevel()

        // When - insert report to database (contacted_ids is empty - backend handles it)
        database.exposureReportQueries.insertReport(
            id = reportId,
            sti_types = stiTypes,
            test_date = testDate,
            privacy_level = privacyLevel,
            contacted_ids = "[]", // Empty - backend handles contact discovery
            reported_at = currentTimeMillis(),
            synced_to_cloud = 0,
            test_result = "POSITIVE",
        )

        // Then - report should be saved
        val savedReport =
            database.exposureReportQueries
                .getReportById(reportId)
                .executeAsOneOrNull()

        assertNotNull(savedReport)
        assertEquals(reportId, savedReport.id)
        assertEquals(stiTypes, savedReport.sti_types)
        assertEquals(privacyLevel, savedReport.privacy_level)
        assertEquals("[]", savedReport.contacted_ids) // Empty as expected
        assertEquals(0L, savedReport.synced_to_cloud) // Not synced yet
    }

    @Test
    fun `test pending reports can be queried for sync retry`() {
        // Given - multiple reports with different sync status
        database.exposureReportQueries.insertReport(
            id = "synced-report",
            sti_types = "[\"HIV\"]",
            test_date = currentTimeMillis(),
            privacy_level = "FULL",
            contacted_ids = "[]",
            reported_at = currentTimeMillis(),
            synced_to_cloud = 1, // Already synced
            test_result = "POSITIVE",
        )

        database.exposureReportQueries.insertReport(
            id = "pending-report-1",
            sti_types = "[\"SYPHILIS\"]",
            test_date = currentTimeMillis(),
            privacy_level = "ANONYMOUS",
            contacted_ids = "[]", // Empty - backend handles contacts
            reported_at = currentTimeMillis(),
            synced_to_cloud = 0, // Not synced
            test_result = "POSITIVE",
        )

        database.exposureReportQueries.insertReport(
            id = "pending-report-2",
            sti_types = "[\"CHLAMYDIA\"]",
            test_date = currentTimeMillis(),
            privacy_level = "FULL",
            contacted_ids = "[]", // Empty - backend handles contacts
            reported_at = currentTimeMillis(),
            synced_to_cloud = 0, // Not synced
            test_result = "POSITIVE",
        )

        // When - query pending reports
        val pendingReports =
            database.exposureReportQueries
                .getPendingSyncReports()
                .executeAsList()

        // Then - should find 2 pending reports
        assertEquals(2, pendingReports.size)
        assertTrue(pendingReports.any { it.id == "pending-report-1" })
        assertTrue(pendingReports.any { it.id == "pending-report-2" })
        assertFalse(pendingReports.any { it.id == "synced-report" })
    }

    @Test
    fun `test report marked as synced after cloud submission`() {
        // Given - a pending report
        val reportId = "to-be-synced"
        database.exposureReportQueries.insertReport(
            id = reportId,
            sti_types = "[\"HIV\"]",
            test_date = currentTimeMillis(),
            privacy_level = "FULL",
            contacted_ids = "[]",
            reported_at = currentTimeMillis(),
            synced_to_cloud = 0,
            test_result = "POSITIVE",
        )

        // When - mark as synced
        database.exposureReportQueries.markAsSynced(reportId)

        // Then - sync status should be updated
        val report =
            database.exposureReportQueries
                .getReportById(reportId)
                .executeAsOneOrNull()

        assertNotNull(report)
        assertEquals(1L, report.synced_to_cloud)

        // And should not appear in pending list
        val pending =
            database.exposureReportQueries
                .getPendingSyncReports()
                .executeAsList()
        assertFalse(pending.any { it.id == reportId })
    }

    // ==================== STI JSON Serialization Tests ====================

    @Test
    fun `test STI list to JSON array conversion`() {
        // Given - list of STIs
        val stis = listOf(STI.HIV, STI.SYPHILIS)

        // When - convert to JSON
        val json = STI.toJsonArray(stis)

        // Then - should produce valid JSON array
        assertEquals("[\"HIV\",\"SYPHILIS\"]", json)
    }

    @Test
    fun `test STI JSON array parsing`() {
        // Given - JSON array string
        val json = "[\"HIV\",\"SYPHILIS\",\"CHLAMYDIA\"]"

        // When - parse back to list
        val stis = STI.fromJsonArray(json)

        // Then - should return correct STI list
        assertEquals(3, stis.size)
        assertTrue(STI.HIV in stis)
        assertTrue(STI.SYPHILIS in stis)
        assertTrue(STI.CHLAMYDIA in stis)
    }

    @Test
    fun `test STI parsing ignores invalid values`() {
        // Given - JSON with invalid STI
        val json = "[\"HIV\",\"INVALID_STI\",\"SYPHILIS\"]"

        // When - parse
        val stis = STI.fromJsonArray(json)

        // Then - should only include valid STIs
        assertEquals(2, stis.size)
        assertTrue(STI.HIV in stis)
        assertTrue(STI.SYPHILIS in stis)
    }
}
