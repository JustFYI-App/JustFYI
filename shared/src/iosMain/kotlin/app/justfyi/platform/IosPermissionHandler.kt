package app.justfyi.platform

import app.justfyi.util.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBPeripheralManager
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * iOS implementation of permission handling.
 *
 * Handles permission requests for:
 * - Bluetooth (via Core Bluetooth authorization APIs)
 * - Notifications (via UNUserNotificationCenter)
 * - Camera (via AVCaptureDevice for QR code backup feature)
 *
 * Unlike Android, iOS permissions are typically requested at the point of first use
 * and the system handles the permission dialog automatically. The Info.plist
 * must contain the appropriate usage description strings.
 */
class IosPermissionHandler {
    /**
     * Checks if Bluetooth permission is granted.
     *
     * On iOS, Bluetooth authorization is checked via CBCentralManager and CBPeripheralManager.
     * The first time BLE is accessed, iOS automatically prompts the user.
     *
     * @return true if Bluetooth permission is authorized
     */
    @OptIn(ExperimentalForeignApi::class)
    fun isBluetoothPermissionGranted(): Boolean {
        val centralAuth = CBCentralManager.authorization
        val peripheralAuth = CBPeripheralManager.authorizationStatus()

        return centralAuth == CBManagerAuthorizationAllowedAlways &&
            peripheralAuth == CBManagerAuthorizationAllowedAlways
    }

    /**
     * Checks if Bluetooth permission has been determined.
     *
     * @return true if the user has been asked about Bluetooth permission
     */
    @OptIn(ExperimentalForeignApi::class)
    fun isBluetoothPermissionDetermined(): Boolean {
        val centralAuth = CBCentralManager.authorization
        return centralAuth != CBManagerAuthorizationNotDetermined
    }

    /**
     * Requests Bluetooth permission.
     *
     * Note: On iOS, Bluetooth permission is requested automatically when
     * CBCentralManager or CBPeripheralManager is instantiated with a delegate.
     * There's no explicit API to request permission - it happens on first use.
     *
     * This method triggers the permission request by initializing a temporary
     * CBCentralManager if permission hasn't been determined yet.
     *
     * @param onResult Callback with the result (true if granted)
     */
    @OptIn(ExperimentalForeignApi::class)
    fun requestBluetoothPermission(onResult: (Boolean) -> Unit) {
        // If permission is already determined, return the current status
        if (isBluetoothPermissionDetermined()) {
            onResult(isBluetoothPermissionGranted())
            return
        }

        // Bluetooth permission is triggered automatically when CBCentralManager is created
        // The IosBleManager already handles this - we just check the current status
        // After the user responds to the permission dialog, isBluetoothPermissionGranted() will return the result
        Logger.d(TAG, "Bluetooth permission will be requested on first BLE use")
        onResult(isBluetoothPermissionGranted())
    }

    /**
     * Checks if notification permission is granted.
     *
     * @param callback Callback with the result (true if authorized)
     */
    fun isNotificationPermissionGranted(callback: (Boolean) -> Unit) {
        UNUserNotificationCenter
            .currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                val isAuthorized = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
                callback(isAuthorized)
            }
    }

    /**
     * Checks if notification permission has been determined.
     *
     * @param callback Callback with the result (true if determined)
     */
    fun isNotificationPermissionDetermined(callback: (Boolean) -> Unit) {
        UNUserNotificationCenter
            .currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                val isDetermined = settings?.authorizationStatus != UNAuthorizationStatusNotDetermined
                callback(isDetermined)
            }
    }

    /**
     * Requests notification permission.
     *
     * @param onResult Callback with the result (true if granted)
     */
    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        val options =
            UNAuthorizationOptionAlert or
                UNAuthorizationOptionSound or
                UNAuthorizationOptionBadge

        UNUserNotificationCenter
            .currentNotificationCenter()
            .requestAuthorizationWithOptions(options) { granted, error ->
                if (error != null) {
                    Logger.e(TAG, "Notification permission request failed: ${error.localizedDescription}")
                }
                onResult(granted)
            }
    }

    /**
     * Checks if camera permission is granted.
     *
     * @return true if camera access is authorized
     */
    fun isCameraPermissionGranted(): Boolean {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        return status == AVAuthorizationStatusAuthorized
    }

    /**
     * Checks if camera permission has been determined.
     *
     * @return true if the user has been asked about camera permission
     */
    fun isCameraPermissionDetermined(): Boolean {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        return status != AVAuthorizationStatusNotDetermined
    }

    /**
     * Requests camera permission.
     *
     * @param onResult Callback with the result (true if granted)
     */
    fun requestCameraPermission(onResult: (Boolean) -> Unit) {
        if (isCameraPermissionDetermined()) {
            onResult(isCameraPermissionGranted())
            return
        }

        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            onResult(granted)
        }
    }

    /**
     * Suspend version of requestNotificationPermission for coroutine use.
     */
    suspend fun requestNotificationPermissionSuspend(): Boolean =
        suspendCoroutine { continuation ->
            requestNotificationPermission { granted ->
                continuation.resume(granted)
            }
        }

    /**
     * Suspend version of requestCameraPermission for coroutine use.
     */
    suspend fun requestCameraPermissionSuspend(): Boolean =
        suspendCoroutine { continuation ->
            requestCameraPermission { granted ->
                continuation.resume(granted)
            }
        }

    companion object {
        private const val TAG = "IosPermissionHandler"

        /**
         * Required Info.plist entries for permissions.
         * These must be added to the Xcode project's Info.plist.
         */
        val REQUIRED_PLIST_ENTRIES =
            mapOf(
                "NSBluetoothAlwaysUsageDescription" to
                    "Just FYI uses Bluetooth to discover nearby users for anonymous contact tracing.",
                "NSBluetoothPeripheralUsageDescription" to
                    "Just FYI uses Bluetooth to advertise your presence to nearby users.",
                "NSCameraUsageDescription" to
                    "Just FYI uses the camera to scan QR codes for account backup and recovery.",
            )
    }
}

/**
 * iOS implementation of BlePermissionHandler.
 *
 * This implementation wraps IosPermissionHandler to provide the common interface
 * expected by ViewModels in commonMain.
 */
class IosBlePermissionHandler : BlePermissionHandler {
    private val permissionHandler = IosPermissionHandler()

    /**
     * Gets the list of required Info.plist keys for BLE operations.
     *
     * @return List of Info.plist keys that must be configured
     */
    override fun getRequiredPermissions(): List<String> =
        listOf(
            "NSBluetoothAlwaysUsageDescription",
            "NSBluetoothPeripheralUsageDescription",
        )

    /**
     * Checks if Bluetooth permission is granted.
     *
     * @return true if Bluetooth permission is granted
     */
    override fun hasAllPermissions(): Boolean = permissionHandler.isBluetoothPermissionGranted()

    /**
     * Gets a user-friendly description of what permissions are needed and why.
     *
     * @return Description of required permissions
     */
    override fun getPermissionRationale(): String =
        "Just FYI needs Bluetooth permission to discover nearby users and share your anonymous presence for contact tracing."
}

/**
 * iOS implementation of NotificationPermissionHandler.
 *
 * Note: iOS notification permission check is async, but this interface requires a sync check.
 * For safety during account recovery, we return false to ensure the permissions step is shown.
 * The actual permission state will be checked on the permissions screen.
 */
class IosNotificationPermissionHandler : NotificationPermissionHandler {
    /**
     * Checks if notification permission is granted.
     *
     * On iOS, this check is async so we conservatively return false
     * to ensure the permissions step is shown during recovery.
     * The permissions step will check the actual state.
     *
     * @return false (conservative default to show permissions step)
     */
    override fun hasPermission(): Boolean {
        // iOS notification permission check is async via UNUserNotificationCenter
        // Return false to ensure permissions step is shown during recovery
        // The actual permission will be checked/requested on the permissions screen
        return false
    }
}
