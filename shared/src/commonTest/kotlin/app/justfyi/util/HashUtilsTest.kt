package app.justfyi.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for HashUtils domain-separated hashing.
 *
 * These tests verify that:
 * 1. Hash functions produce consistent output
 * 2. Different domains produce different hashes for the same UID
 * 3. Output is always lowercase hex string
 */
class HashUtilsTest {
    companion object {
        private const val TEST_UID = "abc123xyz"
    }

    @Test
    fun hashForInteractionProducesConsistentHash() {
        val uid = TEST_UID

        val hash1 = HashUtils.hashForInteraction(uid)
        val hash2 = HashUtils.hashForInteraction(uid)

        assertEquals(hash1, hash2, "Hash should be deterministic")
        assertEquals(64, hash1.length, "SHA-256 hash should be 64 hex characters")
        assertEquals(hash1, hash1.lowercase(), "Hash should be lowercase")
    }

    @Test
    fun hashForNotificationProducesDifferentHashThanInteraction() {
        val uid = TEST_UID

        val interactionHash = HashUtils.hashForInteraction(uid)
        val notificationHash = HashUtils.hashForNotification(uid)

        assertNotEquals(
            interactionHash,
            notificationHash,
            "Notification hash should differ from interaction hash (domain separation)",
        )
        assertEquals(64, notificationHash.length, "SHA-256 hash should be 64 hex characters")
        assertEquals(notificationHash, notificationHash.lowercase(), "Hash should be lowercase")
    }

    @Test
    fun hashForChainProducesDifferentHashThanOtherDomains() {
        val uid = TEST_UID

        val chainHash = HashUtils.hashForChain(uid)
        val interactionHash = HashUtils.hashForInteraction(uid)
        val notificationHash = HashUtils.hashForNotification(uid)

        assertNotEquals(chainHash, interactionHash, "Chain hash should differ from interaction hash")
        assertNotEquals(chainHash, notificationHash, "Chain hash should differ from notification hash")
        assertEquals(64, chainHash.length, "SHA-256 hash should be 64 hex characters")
        assertEquals(chainHash, chainHash.lowercase(), "Hash should be lowercase")
    }

    @Test
    fun hashForReportProducesDifferentHashThanOtherDomains() {
        val uid = TEST_UID

        val reportHash = HashUtils.hashForReport(uid)
        val interactionHash = HashUtils.hashForInteraction(uid)
        val notificationHash = HashUtils.hashForNotification(uid)
        val chainHash = HashUtils.hashForChain(uid)

        assertNotEquals(reportHash, interactionHash, "Report hash should differ from interaction hash")
        assertNotEquals(reportHash, notificationHash, "Report hash should differ from notification hash")
        assertNotEquals(reportHash, chainHash, "Report hash should differ from chain hash")
        assertEquals(64, reportHash.length, "SHA-256 hash should be 64 hex characters")
        assertEquals(reportHash, reportHash.lowercase(), "Hash should be lowercase")
    }

    @Test
    fun differentCasesProduceDifferentHashes() {
        // Note: This implementation does NOT normalize case before hashing
        val lowercaseUid = "abc123xyz"
        val uppercaseUid = "ABC123XYZ"

        val hashLower = HashUtils.hashForInteraction(lowercaseUid)
        val hashUpper = HashUtils.hashForInteraction(uppercaseUid)

        assertNotEquals(hashLower, hashUpper, "Different case inputs should produce different hashes")
    }

    @Test
    fun hashOutputIsValidLowercaseHexString() {
        val uid = "testUser123"

        val interactionHash = HashUtils.hashForInteraction(uid)
        val notificationHash = HashUtils.hashForNotification(uid)
        val chainHash = HashUtils.hashForChain(uid)
        val reportHash = HashUtils.hashForReport(uid)

        val hexPattern = Regex("^[0-9a-f]{64}$")

        assertTrue(
            hexPattern.matches(interactionHash),
            "Interaction hash should be 64 lowercase hex chars: $interactionHash",
        )
        assertTrue(
            hexPattern.matches(notificationHash),
            "Notification hash should be 64 lowercase hex chars: $notificationHash",
        )
        assertTrue(
            hexPattern.matches(chainHash),
            "Chain hash should be 64 lowercase hex chars: $chainHash",
        )
        assertTrue(
            hexPattern.matches(reportHash),
            "Report hash should be 64 lowercase hex chars: $reportHash",
        )
    }

    @Test
    fun hashIsDeterministicAcrossMultipleCalls() {
        val uid = "consistentTest123"

        val hashes = (1..10).map { HashUtils.hashForInteraction(uid) }

        assertTrue(hashes.all { it == hashes.first() }, "All hashes should be identical")
    }

    @Test
    fun domainSaltsPrefixInputCorrectly() {
        // Verify that salt prefixes are being applied
        val uid = "test"

        val interactionHash = HashUtils.hashForInteraction(uid)
        val notificationHash = HashUtils.hashForNotification(uid)
        val chainHash = HashUtils.hashForChain(uid)
        val reportHash = HashUtils.hashForReport(uid)

        // All four hashes should be different due to domain separation
        val allHashes = setOf(interactionHash, notificationHash, chainHash, reportHash)
        assertEquals(4, allHashes.size, "All domain hashes should be unique")
    }
}
