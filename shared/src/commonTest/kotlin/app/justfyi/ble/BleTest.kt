package app.justfyi.ble

import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.repository.BleError
import app.justfyi.domain.repository.BluetoothState
import app.justfyi.util.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for BLE functionality.
 */
class BleTest {
    @Test
    fun nearbyUserCreation() {
        val userId = "abc123hash"
        val username = "TestUser"
        val rssi = -65
        val timestamp = currentTimeMillis()

        val nearbyUser =
            NearbyUser(
                anonymousIdHash = userId,
                username = username,
                signalStrength = rssi,
                lastSeen = timestamp,
            )

        assertEquals(userId, nearbyUser.anonymousIdHash)
        assertEquals(userId, nearbyUser.id)
        assertEquals(username, nearbyUser.username)
        assertEquals(rssi, nearbyUser.signalStrength)
        assertEquals(timestamp, nearbyUser.lastSeen)
    }

    @Test
    fun bluetoothStateEnumValues() {
        val states = BluetoothState.values()
        assertTrue(states.any { it == BluetoothState.ON })
        assertTrue(states.any { it == BluetoothState.OFF })
        assertTrue(states.any { it == BluetoothState.TURNING_ON })
        assertTrue(states.any { it == BluetoothState.TURNING_OFF })
        assertTrue(states.any { it == BluetoothState.NOT_AVAILABLE })
    }

    @Test
    fun bleErrorNotSupported() {
        val error = BleError.NotSupported
        assertEquals("BLE is not supported on this device", error.message)
    }

    @Test
    fun bleErrorBluetoothDisabled() {
        val error = BleError.BluetoothDisabled
        assertEquals("Bluetooth is disabled", error.message)
    }

    @Test
    fun bleErrorPermissionDenied() {
        val missingPermissions = listOf("BLUETOOTH_SCAN", "BLUETOOTH_ADVERTISE")
        val error = BleError.PermissionDenied(missingPermissions)
        assertTrue(error.message.contains("BLUETOOTH_SCAN"))
        assertTrue(error.message.contains("BLUETOOTH_ADVERTISE"))
    }

    @Test
    fun bleErrorTypesAreDistinct() {
        val notSupported = BleError.NotSupported
        val disabled = BleError.BluetoothDisabled
        val permissionDenied = BleError.PermissionDenied(listOf("test"))
        val advertisingFailed = BleError.AdvertisingFailed("test")
        val scanFailed = BleError.ScanFailed("test")
        val gattFailed = BleError.GattServerFailed("test")

        assertIs<BleError.NotSupported>(notSupported)
        assertIs<BleError.BluetoothDisabled>(disabled)
        assertIs<BleError.PermissionDenied>(permissionDenied)
        assertIs<BleError.AdvertisingFailed>(advertisingFailed)
        assertIs<BleError.ScanFailed>(scanFailed)
        assertIs<BleError.GattServerFailed>(gattFailed)
    }

    @Test
    fun nearbyUsersDeduplication() {
        val user1 = NearbyUser("hash1", "User1", -60, 1000L)
        val user1Update = NearbyUser("hash1", "User1", -55, 2000L)
        val user2 = NearbyUser("hash2", "User2", -70, 1500L)

        val allScanResults = listOf(user1, user1Update, user2)

        val uniqueUsers =
            allScanResults
                .groupBy { it.anonymousIdHash }
                .mapValues { (_, users) -> users.maxByOrNull { it.lastSeen }!! }
                .values
                .toList()

        assertEquals(2, uniqueUsers.size)
        val user1Final = uniqueUsers.find { it.anonymousIdHash == "hash1" }
        assertEquals(2000L, user1Final?.lastSeen)
    }

    @Test
    fun nearbyUsersSortedBySignalStrength() {
        val weakUser = NearbyUser("weak", "WeakSignal", -90, 1000L)
        val strongUser = NearbyUser("strong", "StrongSignal", -45, 1000L)
        val mediumUser = NearbyUser("medium", "MediumSignal", -70, 1000L)

        val unsortedList = listOf(weakUser, strongUser, mediumUser)
        val sortedList = unsortedList.sortedByDescending { it.signalStrength }

        assertEquals(strongUser, sortedList[0])
        assertEquals(mediumUser, sortedList[1])
        assertEquals(weakUser, sortedList[2])
    }

    @Test
    fun staleDeviceDetection() {
        val currentTime = currentTimeMillis()
        val staleThresholdMs = 30_000L

        val freshUser = NearbyUser("fresh", "FreshUser", -60, currentTime - 5_000L)
        val staleUser = NearbyUser("stale", "StaleUser", -60, currentTime - 60_000L)
        val users = listOf(freshUser, staleUser)

        val freshUsers =
            users.filter { user ->
                currentTime - user.lastSeen <= staleThresholdMs
            }

        assertEquals(1, freshUsers.size)
        assertEquals(freshUser, freshUsers.first())
    }

    @Test
    fun differentAnonymousIdsProduceDifferentHashes() {
        val uid1 = "firebaseUid12345"
        val uid2 = "firebaseUid67890"

        val hash1 = simpleHash(uid1)
        val hash2 = simpleHash(uid2)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun sameAnonymousIdProducesConsistentHash() {
        val uid = "consistentUid123"

        val hash1 = simpleHash(uid)
        val hash2 = simpleHash(uid)

        assertEquals(hash1, hash2)
    }

    private fun simpleHash(input: String): String {
        var hash = 0L
        for (char in input) {
            hash = hash * 31 + char.code
        }
        return hash.toString(16)
    }
}
