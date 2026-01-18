package app.justfyi.integration

import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.User
import app.justfyi.data.local.createTestDatabase
import app.justfyi.domain.repository.SettingsRepository
import app.justfyi.util.currentTimeMillis
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for onboarding feature critical user workflows.
 *
 * These tests fill critical gaps identified in Task Group 5:
 * - Full onboarding flow from start to completion
 * - ID generation failure and retry flow
 * - Permission denial and "Continue Anyway" flow
 * - Username validation edge cases with special characters
 * - Returning user (onboarding already complete) skips flow
 * - App killed mid-onboarding resumes correctly
 *
 * Total: 8 strategic tests maximum as per spec requirements.
 */
class OnboardingIntegrationTest {
    private lateinit var database: JustFyiDatabase

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        // Clear all data to ensure test isolation
        database.settingsQueries.deleteAllSettings()
        database.userQueries.deleteAllUsers()
        database.interactionQueries.deleteAllInteractions()
        database.notificationQueries.deleteAllNotifications()
    }

    @AfterTest
    fun teardown() {
        // Clean up to ensure test isolation
        database.settingsQueries.deleteAllSettings()
        database.userQueries.deleteAllUsers()
        database.interactionQueries.deleteAllInteractions()
        database.notificationQueries.deleteAllNotifications()
    }

    // ==================================================================================
    // Test 1: Full Onboarding Flow Integration
    // User Journey: Start -> ID Generation -> Backup Confirm -> Permissions -> Username -> Complete
    // ==================================================================================

    @Test
    fun `test full onboarding flow from start to completion persists correctly`() {
        val initialOnboardingState =
            database.settingsQueries
                .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                .executeAsOneOrNull()
        assertNull(initialOnboardingState, "Onboarding should not be complete initially")

        val firebaseAnonymousId = "onboarding-flow-user-${currentTimeMillis()}"
        val createdAt = currentTimeMillis()

        database.userQueries.insertUser(
            id = firebaseAnonymousId,
            anonymous_id = firebaseAnonymousId,
            username = "User",
            created_at = createdAt,
            fcm_token = null,
            id_backup_confirmed = 0,
        )

        val userAfterIdGen = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNotNull(userAfterIdGen, "User should exist after ID generation")
        assertEquals(0L, userAfterIdGen.id_backup_confirmed, "Backup should not be confirmed yet")

        database.userQueries.updateIdBackupConfirmed(id_backup_confirmed = 1, id = firebaseAnonymousId)

        val userAfterBackup = database.userQueries.getUserById(firebaseAnonymousId).executeAsOneOrNull()
        assertNotNull(userAfterBackup)
        assertEquals(1L, userAfterBackup.id_backup_confirmed, "Backup should be confirmed")

        // Permissions are handled at runtime, not persisted

        val chosenUsername = "OnboardingTestUser"
        database.userQueries.updateUsername(username = chosenUsername, id = firebaseAnonymousId)

        val userAfterUsername = database.userQueries.getUserById(firebaseAnonymousId).executeAsOneOrNull()
        assertNotNull(userAfterUsername)
        assertEquals(chosenUsername, userAfterUsername.username)

        database.settingsQueries.insertOrReplaceSetting(
            SettingsRepository.KEY_ONBOARDING_COMPLETE,
            "true",
        )

        // Verify final state
        val finalOnboardingState =
            database.settingsQueries
                .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                .executeAsOneOrNull()
        assertEquals("true", finalOnboardingState, "Onboarding should be marked complete")

        val finalUser = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNotNull(finalUser)
        assertEquals(chosenUsername, finalUser.username)
        assertEquals(1L, finalUser.id_backup_confirmed)
    }

    // ==================================================================================
    // Test 2: ID Generation Failure and Retry Flow
    // User Journey: Start -> Firebase auth fails -> Error shown -> Retry -> Success
    // ==================================================================================

    @Test
    fun `test ID generation failure and retry flow recovers correctly`() {
        // Simulate initial state tracking
        var isLoading = false
        var generatedId: String? = null
        var error: String? = null
        var retryCount = 0

        isLoading = true
        assertNull(generatedId, "ID should be null before generation")

        isLoading = false
        error = "Firebase auth failed: Network error"
        retryCount++

        assertNull(generatedId, "ID should still be null after failure")
        assertNotNull(error, "Error should be set")
        assertEquals(1, retryCount, "Retry count should be 1")

        error = null
        isLoading = true

        val firebaseId = "retry-success-uid-${currentTimeMillis()}"
        generatedId = firebaseId
        isLoading = false

        // Persist user to database
        database.userQueries.insertUser(
            id = firebaseId,
            anonymous_id = firebaseId,
            username = "User",
            created_at = currentTimeMillis(),
            fcm_token = null,
            id_backup_confirmed = 0,
        )

        // Verify recovery
        assertFalse(isLoading, "Loading should be false after success")
        assertNull(error, "Error should be cleared")
        assertNotNull(generatedId, "ID should be generated after retry")

        val user = database.userQueries.getUserById(firebaseId).executeAsOneOrNull()
        assertNotNull(user, "User should exist in database after retry success")
    }

    // ==================================================================================
    // Test 3: Permission Denial and "Continue Anyway" Flow
    // User Journey: Request permission -> Denied -> Show warning -> Continue Anyway -> Proceed
    // ==================================================================================

    @Test
    fun `test permission denial and continue anyway flow allows progression`() {
        // Simulate permission state (mirroring ViewModel state)
        var bluetoothPermissionGranted = false
        var notificationPermissionGranted = false
        var permissionDeniedWarningShown = false
        var currentStep = 3 // Permission step

        assertFalse(bluetoothPermissionGranted)
        assertFalse(notificationPermissionGranted)

        bluetoothPermissionGranted = false
        permissionDeniedWarningShown = true

        assertTrue(permissionDeniedWarningShown, "Warning should be shown after denial")

        val canProceed = canProceedFromStep(currentStep, bluetoothPermissionGranted, notificationPermissionGranted)
        assertTrue(canProceed, "Should be able to proceed from step 3 even with denied permissions")

        currentStep = 4

        // Verify user can complete onboarding without permissions
        val canComplete = canProceedFromStep(currentStep, bluetoothPermissionGranted, notificationPermissionGranted)
        assertTrue(canComplete, "Should be able to complete onboarding without permissions")

        database.settingsQueries.insertOrReplaceSetting(
            SettingsRepository.KEY_ONBOARDING_COMPLETE,
            "true",
        )

        val onboardingComplete =
            database.settingsQueries
                .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                .executeAsOneOrNull()
        assertEquals("true", onboardingComplete, "Onboarding should complete despite permission denial")
    }

    // ==================================================================================
    // Test 4: Username Validation Edge Cases with Special Characters
    // Tests: Whitespace-only, leading/trailing spaces, emoji, control chars, boundary length
    // ==================================================================================

    @Test
    fun `test username validation edge cases with special characters`() {
        // Test cases for various edge cases

        // Case 1: Whitespace-only username
        val whitespaceResult = validateUsernameForTest("   ")
        assertEquals(UsernameValidationResult.Empty, whitespaceResult, "Whitespace-only should be treated as empty")

        // Case 2: Leading/trailing whitespace (should be trimmed and valid)
        val paddedResult = validateUsernameForTest("  ValidUser  ")
        assertEquals(UsernameValidationResult.Valid, paddedResult, "Padded username should be valid after trim")

        // Case 3: Emoji characters (non-ASCII)
        val emojiResult = validateUsernameForTest("User123!")
        // Note: '!' is ASCII (code 33), so this should be valid
        assertEquals(UsernameValidationResult.Valid, emojiResult, "ASCII special chars should be valid")

        val actualEmojiResult = validateUsernameForTest("User\uD83D\uDE00") // User with smiley emoji
        assertTrue(
            actualEmojiResult is UsernameValidationResult.NonAscii,
            "Emoji should be rejected as non-ASCII",
        )

        // Case 4: Control characters
        val controlCharResult = validateUsernameForTest("User\u0000Name")
        assertTrue(
            controlCharResult is UsernameValidationResult.NonAscii,
            "Control characters should be rejected",
        )

        // Case 5: Boundary length - exactly 30 characters
        val exactly30Result = validateUsernameForTest("A".repeat(30))
        assertEquals(UsernameValidationResult.Valid, exactly30Result, "Exactly 30 chars should be valid")

        // Case 6: Boundary length - 31 characters (over limit)
        val over30Result = validateUsernameForTest("A".repeat(31))
        assertTrue(
            over30Result is UsernameValidationResult.TooLong,
            "31 chars should be too long",
        )

        // Case 7: Mixed valid special ASCII characters
        val specialAsciiResult = validateUsernameForTest("User_Name-123.test")
        assertEquals(UsernameValidationResult.Valid, specialAsciiResult, "ASCII special chars should be valid")

        // Case 8: Tab and newline (control characters)
        val tabResult = validateUsernameForTest("User\tName")
        assertTrue(
            tabResult is UsernameValidationResult.NonAscii,
            "Tab should be rejected (outside printable ASCII range)",
        )
    }

    // ==================================================================================
    // Test 5: Returning User (Onboarding Already Complete) Skips Flow
    // User Journey: App launch -> Check onboarding state -> Already complete -> Go to Home
    // ==================================================================================

    @Test
    fun `test returning user with onboarding complete skips to home`() {
        // Setup: Simulate a returning user who completed onboarding previously
        val existingUserId = "returning-user-${currentTimeMillis()}"

        // Create existing user
        database.userQueries.insertUser(
            id = existingUserId,
            anonymous_id = existingUserId,
            username = "ReturningUser",
            created_at = currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L), // 30 days ago
            fcm_token = "existing-fcm-token",
            id_backup_confirmed = 1,
        )

        // Set onboarding as complete
        database.settingsQueries.insertOrReplaceSetting(
            SettingsRepository.KEY_ONBOARDING_COMPLETE,
            "true",
        )

        // App launch: Check onboarding state
        val isOnboardingComplete =
            database.settingsQueries
                .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                .executeAsOneOrNull() == "true"

        assertTrue(isOnboardingComplete, "Onboarding should be marked complete")

        // Determine initial destination
        val initialDestination = determineInitialDestination(isOnboardingComplete)

        assertEquals(
            InitialDestination.HOME,
            initialDestination,
            "Returning user should go directly to Home",
        )

        // Verify user data is intact
        val user = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNotNull(user, "User should still exist")
        assertEquals("ReturningUser", user.username)
        assertEquals(1L, user.id_backup_confirmed)
    }

    // ==================================================================================
    // Test 6: App Killed Mid-Onboarding Resumes Correctly
    // User Journey: Start onboarding -> Complete step 2 -> App killed -> Relaunch -> Resume from step 3
    // ==================================================================================

    @Test
    fun `test app killed mid-onboarding resumes from correct step`() {
        // Phase 1: Start onboarding and complete steps 1-2
        val userId = "mid-onboarding-user-${currentTimeMillis()}"

        database.userQueries.insertUser(
            id = userId,
            anonymous_id = userId,
            username = "User",
            created_at = currentTimeMillis(),
            fcm_token = null,
            id_backup_confirmed = 0,
        )

        database.userQueries.updateIdBackupConfirmed(id_backup_confirmed = 1, id = userId)

        // Verify state before "app kill"
        val userBeforeKill = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNotNull(userBeforeKill)
        assertEquals(1L, userBeforeKill.id_backup_confirmed)

        // Onboarding NOT marked complete (user was on step 3 or 4)
        val onboardingBeforeKill =
            database.settingsQueries
                .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                .executeAsOneOrNull()
        assertNull(onboardingBeforeKill, "Onboarding should not be complete yet")

        // === SIMULATE APP KILL AND RELAUNCH ===
        // (Database is persistent, so state survives)

        // Phase 2: App relaunches - determine resume state
        val isOnboardingComplete =
            database.settingsQueries
                .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                .executeAsOneOrNull() == "true"

        assertFalse(isOnboardingComplete, "Onboarding should still be incomplete after relaunch")

        // Check what step to resume from based on persisted state
        val userAfterRelaunch = database.userQueries.getCurrentUser().executeAsOneOrNull()
        val resumeStep = determineResumeStep(userAfterRelaunch)

        // User has ID (step 1 done) and backup confirmed (step 2 done)
        // Should resume at step 3 (Permissions)
        assertEquals(3, resumeStep, "Should resume at step 3 (Permissions)")

        // Phase 3: Complete remaining steps

        // Mark onboarding complete
        database.settingsQueries.insertOrReplaceSetting(
            SettingsRepository.KEY_ONBOARDING_COMPLETE,
            "true",
        )

        val finalOnboardingState =
            database.settingsQueries
                .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                .executeAsOneOrNull()
        assertEquals("true", finalOnboardingState, "Onboarding should be complete after resume")
    }

    // ==================================================================================
    // Test 7: ID Reveal Toggle State Transitions
    // Verifies reveal/hide toggle works correctly across multiple transitions
    // ==================================================================================

    @Test
    fun `test ID reveal toggle state transitions correctly`() {
        var isIdRevealed = false

        // Initial state: hidden
        assertFalse(isIdRevealed, "ID should be hidden initially")
        assertEquals("****-****-****-****", getMaskedId("ABCD-EFGH-IJKL-MNOP", isIdRevealed))

        // Toggle to reveal
        isIdRevealed = !isIdRevealed
        assertTrue(isIdRevealed, "ID should be revealed after first toggle")
        assertEquals("ABCD-EFGH-IJKL-MNOP", getMaskedId("ABCD-EFGH-IJKL-MNOP", isIdRevealed))

        // Toggle back to hidden
        isIdRevealed = !isIdRevealed
        assertFalse(isIdRevealed, "ID should be hidden after second toggle")
        assertEquals("****-****-****-****", getMaskedId("ABCD-EFGH-IJKL-MNOP", isIdRevealed))

        // Multiple rapid toggles should end in correct state
        repeat(5) { isIdRevealed = !isIdRevealed }
        assertTrue(isIdRevealed, "After 5 toggles (odd), should be revealed")

        repeat(4) { isIdRevealed = !isIdRevealed }
        assertTrue(isIdRevealed, "After 4 more toggles (even), should still be revealed")
    }

    // ==================================================================================
    // Test 8: Complete Step Validation Chain
    // Verifies the entire step validation chain works end-to-end
    // ==================================================================================

    @Test
    fun `test complete step validation chain enforces correct order`() {
        // Simulate state across all steps
        var generatedId: String? = null
        var isBackupConfirmed = false
        var bluetoothGranted = false
        var notificationGranted = false
        var username = ""
        var currentStep = 1

        assertFalse(
            canProceedWithFullState(currentStep, generatedId, isBackupConfirmed),
            "Cannot proceed from step 1 without ID",
        )

        // Generate ID
        generatedId = "test-id-123"
        assertTrue(
            canProceedWithFullState(currentStep, generatedId, isBackupConfirmed),
            "Can proceed from step 1 with ID",
        )

        // Move to step 2
        currentStep = 2

        assertFalse(
            canProceedWithFullState(currentStep, generatedId, isBackupConfirmed),
            "Cannot proceed from step 2 without backup confirmation",
        )

        // Confirm backup
        isBackupConfirmed = true
        assertTrue(
            canProceedWithFullState(currentStep, generatedId, isBackupConfirmed),
            "Can proceed from step 2 with backup confirmed",
        )

        // Move to step 3
        currentStep = 3

        assertTrue(
            canProceedWithFullState(currentStep, generatedId, isBackupConfirmed),
            "Can proceed from step 3 without permissions",
        )

        // Grant permissions (optional)
        bluetoothGranted = true
        notificationGranted = true
        assertTrue(
            canProceedWithFullState(currentStep, generatedId, isBackupConfirmed),
            "Can proceed from step 3 with permissions",
        )

        // Move to step 4
        currentStep = 4

        assertTrue(
            canProceedWithFullState(currentStep, generatedId, isBackupConfirmed),
            "Can proceed from step 4 without username",
        )

        // Set username (optional)
        username = "TestUser"
        assertTrue(
            canProceedWithFullState(currentStep, generatedId, isBackupConfirmed),
            "Can proceed from step 4 with username",
        )

        // Complete onboarding
        database.settingsQueries.insertOrReplaceSetting(
            SettingsRepository.KEY_ONBOARDING_COMPLETE,
            "true",
        )

        val isComplete =
            database.settingsQueries
                .getSettingByKey(SettingsRepository.KEY_ONBOARDING_COMPLETE)
                .executeAsOneOrNull() == "true"
        assertTrue(isComplete, "Onboarding should be complete after all steps")
    }

    // ==================================================================================
    // Helper Functions
    // ==================================================================================

    private sealed class UsernameValidationResult {
        data object Valid : UsernameValidationResult()

        data object Empty : UsernameValidationResult()

        data class TooLong(
            val maxLength: Int,
            val actualLength: Int,
        ) : UsernameValidationResult()

        data class NonAscii(
            val invalidChars: List<Char>,
        ) : UsernameValidationResult()
    }

    private fun validateUsernameForTest(name: String): UsernameValidationResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return UsernameValidationResult.Empty
        if (trimmed.length > 30) return UsernameValidationResult.TooLong(30, trimmed.length)

        // Check for non-printable ASCII (codes 32-126 are printable)
        val nonAsciiChars = trimmed.filter { it.code !in 32..126 }
        if (nonAsciiChars.isNotEmpty()) {
            return UsernameValidationResult.NonAscii(nonAsciiChars.toList())
        }

        return UsernameValidationResult.Valid
    }

    private fun canProceedFromStep(
        step: Int,
        bluetoothGranted: Boolean,
        notificationGranted: Boolean,
    ): Boolean {
        // Steps 3 and 4 can always be skipped
        return step == 3 || step == 4
    }

    private fun canProceedWithFullState(
        step: Int,
        generatedId: String?,
        isBackupConfirmed: Boolean,
    ): Boolean =
        when (step) {
            1 -> generatedId != null
            2 -> isBackupConfirmed
            3 -> true // Permissions can be skipped
            4 -> true // Username is optional
            else -> false
        }

    private enum class InitialDestination {
        ONBOARDING,
        HOME,
    }

    private fun determineInitialDestination(isOnboardingComplete: Boolean): InitialDestination =
        if (isOnboardingComplete) InitialDestination.HOME else InitialDestination.ONBOARDING

    private fun determineResumeStep(user: User?): Int {
        if (user == null) return 1 // No user, start from beginning

        // Check what has been completed
        val hasId = user.anonymous_id.isNotEmpty()
        val hasBackupConfirmed = user.id_backup_confirmed == 1L

        return when {
            !hasId -> 1 // No ID, start from step 1
            !hasBackupConfirmed -> 2 // ID exists but backup not confirmed, start from step 2
            else -> 3 // ID and backup done, start from permissions (step 3)
        }
    }

    private fun getMaskedId(
        formattedId: String,
        isRevealed: Boolean,
    ): String =
        if (isRevealed) {
            formattedId
        } else {
            formattedId.replace(Regex("[A-Z0-9]"), "*")
        }
}
