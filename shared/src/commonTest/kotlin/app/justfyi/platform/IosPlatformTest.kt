package app.justfyi.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for platform interface contracts.
 */
class IosPlatformTest {
    @Test
    fun blePermissionHandlerInterfaceContract() {
        val testHandler =
            object : BlePermissionHandler {
                override fun getRequiredPermissions(): List<String> =
                    listOf(
                        "NSBluetoothAlwaysUsageDescription",
                        "NSBluetoothPeripheralUsageDescription",
                    )

                override fun hasAllPermissions(): Boolean = true

                override fun getPermissionRationale(): String = "Bluetooth is required for nearby user discovery"
            }

        val permissions = testHandler.getRequiredPermissions()
        assertEquals(2, permissions.size)
        assertTrue(permissions.contains("NSBluetoothAlwaysUsageDescription"))
        assertTrue(permissions.contains("NSBluetoothPeripheralUsageDescription"))
        assertTrue(testHandler.hasAllPermissions())
        assertNotNull(testHandler.getPermissionRationale())
    }
}
