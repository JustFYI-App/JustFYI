package app.justfyi.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.Inject

/**
 * Handles BLE-related permission checks and requirements.
 *
 * With minSdk 31 (Android 12+), only BLE-specific permissions are needed:
 * BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT
 */
@Inject
class BlePermissionHandler(
    private val context: Context,
) {
    /**
     * Gets the list of required permissions for BLE operations.
     */
    fun getRequiredPermissions(): List<String> =
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

    /**
     * Checks if all required BLE permissions are granted.
     */
    fun hasAllPermissions(): Boolean =
        getRequiredPermissions().all { permission ->
            isPermissionGranted(permission)
        }

    /**
     * Gets the list of permissions that are not yet granted.
     */
    fun getMissingPermissions(): List<String> =
        getRequiredPermissions().filter { permission ->
            !isPermissionGranted(permission)
        }

    /**
     * Checks if a specific permission is granted.
     */
    fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Checks if the app should show rationale for requesting permissions.
     */
    fun shouldShowRationale(permission: String): Boolean = false

    /**
     * Checks if Bluetooth permission is granted for scanning.
     */
    fun canScan(): Boolean = isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)

    /**
     * Checks if Bluetooth permission is granted for advertising.
     */
    fun canAdvertise(): Boolean = isPermissionGranted(Manifest.permission.BLUETOOTH_ADVERTISE)

    /**
     * Checks if Bluetooth permission is granted for connecting.
     */
    fun canConnect(): Boolean = isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)

    /**
     * Gets a user-friendly description of what permissions are needed and why.
     */
    fun getPermissionRationale(): String =
        "Just FYI needs Bluetooth permissions to discover nearby users. " +
            "This allows you to find and record interactions with other Just FYI users."

    companion object {
        const val BLE_PERMISSION_REQUEST_CODE = 1001

        val ALL_BLE_PERMISSIONS =
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
    }
}
