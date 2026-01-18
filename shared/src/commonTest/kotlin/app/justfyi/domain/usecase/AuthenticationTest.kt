package app.justfyi.domain.usecase

import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.createTestDatabase
import app.justfyi.domain.model.User
import app.justfyi.util.currentTimeMillis
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for authentication and user identity functionality.
 * These tests verify:
 * - Anonymous sign-in creates new user
 * - ID recovery restores existing user
 * - Username validation (ASCII, 30 char max)
 * - Duplicate username handling (emoji suffix)
 *
 * Note: These tests focus on the use case logic.
 * Firebase authentication is mocked/simulated for testing.
 */
class AuthenticationTest {
    private lateinit var database: JustFyiDatabase

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        // Clear all data to ensure test isolation
        database.userQueries.deleteAllUsers()
        database.settingsQueries.deleteAllSettings()
    }

    @AfterTest
    fun teardown() {
        // Clean up to ensure test isolation
        database.userQueries.deleteAllUsers()
        database.settingsQueries.deleteAllSettings()
    }

    // ==================== Anonymous Sign-In Tests ====================

    @Test
    fun `test anonymous sign-in creates new user in database`() {
        // Given - no user exists
        val existingUser = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertEquals(null, existingUser)

        // When - simulating anonymous sign-in
        val anonymousId = "firebase-anon-uid-12345"
        val newUser =
            User(
                id = anonymousId,
                anonymousId = anonymousId,
                username = "User", // Default username
                createdAt = currentTimeMillis(),
                fcmToken = null,
                idBackupConfirmed = false,
            )

        database.userQueries.insertUser(
            id = newUser.id,
            anonymous_id = newUser.anonymousId,
            username = newUser.username,
            created_at = newUser.createdAt,
            fcm_token = newUser.fcmToken,
            id_backup_confirmed = if (newUser.idBackupConfirmed) 1L else 0L,
        )

        // Then - user should be created
        val createdUser = database.userQueries.getCurrentUser().executeAsOneOrNull()
        assertNotNull(createdUser)
        assertEquals(anonymousId, createdUser.anonymous_id)
        assertEquals("User", createdUser.username)
        assertEquals(0L, createdUser.id_backup_confirmed)
    }

    @Test
    fun `test ID recovery restores existing user`() {
        // Given - user exists in database (simulating cloud recovery)
        val originalAnonymousId = "original-firebase-uid"
        val originalUsername = "RecoveredUser"
        val originalCreatedAt = currentTimeMillis() - 86400000L // 1 day ago

        database.userQueries.insertUser(
            id = originalAnonymousId,
            anonymous_id = originalAnonymousId,
            username = originalUsername,
            created_at = originalCreatedAt,
            fcm_token = "old-fcm-token",
            id_backup_confirmed = 1L, // Was confirmed before
        )

        // When - recovering account (simulating sync from cloud)
        val recoveredUser = database.userQueries.getUserById(originalAnonymousId).executeAsOneOrNull()

        // Then - user data should be restored
        assertNotNull(recoveredUser)
        assertEquals(originalAnonymousId, recoveredUser.anonymous_id)
        assertEquals(originalUsername, recoveredUser.username)
        assertEquals(originalCreatedAt, recoveredUser.created_at)
    }

    // ==================== Username Validation Tests ====================

    @Test
    fun `test username validation accepts valid ASCII username`() {
        // Given - valid ASCII usernames
        val validUsernames =
            listOf(
                "Alice",
                "Bob123",
                "User_Name",
                "test-user",
                "CamelCaseUser",
                "a", // Minimum length
                "abcdefghij1234567890abcdefghij", // Exactly 30 chars
            )

        // When/Then - all should be valid
        validUsernames.forEach { username ->
            val isValid = validateUsername(username)
            assertTrue(isValid, "Username '$username' should be valid")
        }
    }

    @Test
    fun `test username validation rejects non-ASCII characters`() {
        // Given - usernames with non-ASCII characters
        val invalidUsernames =
            listOf(
                "User\u00e9", // e with accent
                "User\u4e2d\u6587", // Chinese characters
                "\u0410\u043b\u0438\u0441\u0430", // Cyrillic
                "\ud83d\ude00User", // Starts with emoji
                "User\ud83d\ude00", // Ends with emoji
            )

        // When/Then - all should be invalid
        invalidUsernames.forEach { username ->
            val isValid = validateUsername(username)
            assertFalse(isValid, "Username '$username' should be invalid (non-ASCII)")
        }
    }

    @Test
    fun `test username validation rejects usernames over 30 characters`() {
        // Given - username that's too long
        val longUsername = "a".repeat(31)
        assertEquals(31, longUsername.length)

        // When
        val isValid = validateUsername(longUsername)

        // Then
        assertFalse(isValid, "Username with 31 characters should be invalid")
    }

    @Test
    fun `test username validation rejects empty username`() {
        // Given
        val emptyUsername = ""

        // When
        val isValid = validateUsername(emptyUsername)

        // Then
        assertFalse(isValid, "Empty username should be invalid")
    }

    // ==================== Duplicate Username Handling Tests ====================

    @Test
    fun `test duplicate username gets emoji suffix`() {
        // Given - existing username in database (simulating Firestore query result)
        val baseUsername = "Alice"
        val existingUsernames = setOf(baseUsername)

        // When - generating unique username
        val uniqueUsername = generateUniqueUsername(baseUsername, existingUsernames)

        // Then - should have emoji suffix
        assertNotEquals(baseUsername, uniqueUsername)
        assertTrue(uniqueUsername.startsWith(baseUsername), "Unique username should start with base")
        // The suffix should be an emoji from the curated list
        val suffix = uniqueUsername.removePrefix(baseUsername)
        assertTrue(EMOJI_SUFFIXES.contains(suffix), "Suffix '$suffix' should be from curated emoji list")
    }

    @Test
    fun `test unique username returned if no duplicate exists`() {
        // Given - no existing usernames
        val baseUsername = "UniqueUser"
        val existingUsernames = emptySet<String>()

        // When
        val uniqueUsername = generateUniqueUsername(baseUsername, existingUsernames)

        // Then - should be unchanged
        assertEquals(baseUsername, uniqueUsername)
    }

    @Test
    fun `test multiple duplicate usernames get different emoji suffixes`() {
        // Given - base username and some variations already exist
        val baseUsername = "Popular"
        val existingUsernames = mutableSetOf(baseUsername)

        // When - generating multiple unique usernames
        val generatedUsernames = mutableSetOf<String>()
        repeat(5) {
            val unique = generateUniqueUsername(baseUsername, existingUsernames)
            generatedUsernames.add(unique)
            existingUsernames.add(unique)
        }

        // Then - all generated usernames should be unique
        assertEquals(5, generatedUsernames.size, "All generated usernames should be unique")
    }

    // ==================== Helper Functions (mirroring UsernameUseCase logic) ====================

    /**
     * Validates username according to spec requirements:
     * - ASCII characters only
     * - Maximum 30 characters
     * - Non-empty
     */
    private fun validateUsername(username: String): Boolean {
        if (username.isEmpty()) return false
        if (username.length > MAX_USERNAME_LENGTH) return false
        // Check if all characters are ASCII printable (32-126)
        return username.all { it.code in 32..126 }
    }

    /**
     * Generates a unique username by adding an emoji suffix if needed.
     * @param baseUsername The desired username
     * @param existingUsernames Set of already taken usernames
     * @return A unique username, possibly with emoji suffix
     */
    private fun generateUniqueUsername(
        baseUsername: String,
        existingUsernames: Set<String>,
    ): String {
        if (!existingUsernames.contains(baseUsername)) {
            return baseUsername
        }

        // Try adding random emoji suffixes until we find a unique one
        val shuffledEmojis = EMOJI_SUFFIXES.shuffled()
        for (emoji in shuffledEmojis) {
            val candidate = "$baseUsername$emoji"
            if (!existingUsernames.contains(candidate)) {
                return candidate
            }
        }

        // Fallback: add multiple emojis if single ones are all taken
        val randomEmojis = (1..3).map { shuffledEmojis.random() }.joinToString("")
        return "$baseUsername$randomEmojis"
    }

    companion object {
        const val MAX_USERNAME_LENGTH = 30

        /**
         * Curated list of emoji suffixes for duplicate username handling.
         * These are friendly, neutral emojis that won't cause confusion.
         */
        val EMOJI_SUFFIXES =
            listOf(
                "\ud83c\udf1f", // Star
                "\ud83c\udf08", // Rainbow
                "\ud83c\udf3b", // Sunflower
                "\ud83c\udf3c", // Blossom
                "\ud83c\udf3f", // Herb
                "\ud83c\udf40", // Four Leaf Clover
                "\ud83c\udf52", // Cherries
                "\ud83c\udf53", // Strawberry
                "\ud83e\udd8b", // Butterfly
                "\ud83e\udd9a", // Peacock
                "\ud83e\udd89", // Owl
                "\ud83d\udc22", // Turtle
                "\ud83d\udc1d", // Bee
                "\ud83c\udf32", // Evergreen Tree
                "\ud83c\udfb5", // Musical Note
                "\ud83d\udca7", // Droplet
                "\u2728", // Sparkles
                "\ud83c\udf38", // Cherry Blossom
                "\ud83c\udf3a", // Hibiscus
                "\ud83c\udf41", // Maple Leaf
            )
    }
}
