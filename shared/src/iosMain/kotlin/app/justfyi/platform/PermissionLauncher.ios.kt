package app.justfyi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBPeripheralManager
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS implementation of PermissionLauncher.
 *
 * Provides the actual permission launchers for iOS platform using:
 * - Core Bluetooth for Bluetooth permissions
 * - UNUserNotificationCenter for notification permissions
 * - AVCaptureDevice for camera permissions
 */
private class IosPermissionLauncher(
    private val launchPermission: () -> Unit,
) : PermissionLauncher {
    override fun launch() {
        launchPermission()
    }
}

/**
 * iOS implementation of Bluetooth permission launcher.
 * Uses Core Bluetooth authorization APIs.
 *
 * Note: On iOS, Bluetooth permission is triggered automatically when
 * CBCentralManager or CBPeripheralManager is instantiated. This launcher
 * checks the current authorization status and returns it via the callback.
 *
 * The actual permission dialog will appear when the user first uses
 * BLE features (scanning or advertising).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberBluetoothPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher {
    val permissionHandler = remember { IosPermissionHandler() }

    return remember(onResult) {
        IosPermissionLauncher {
            // Check if Bluetooth permission is granted
            // On iOS, the actual permission request happens when CBCentralManager is created
            permissionHandler.requestBluetoothPermission { granted ->
                onResult(granted)
            }
        }
    }
}

/**
 * iOS implementation of notification permission launcher.
 * Uses UNUserNotificationCenter.requestAuthorization().
 */
@Composable
actual fun rememberNotificationPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher {
    val permissionHandler = remember { IosPermissionHandler() }

    return remember(onResult) {
        IosPermissionLauncher {
            permissionHandler.requestNotificationPermission { granted ->
                onResult(granted)
            }
        }
    }
}

/**
 * iOS implementation of camera permission launcher.
 * Uses AVCaptureDevice.requestAccess(for: .video).
 */
@Composable
actual fun rememberCameraPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher {
    val permissionHandler = remember { IosPermissionHandler() }

    return remember(onResult) {
        IosPermissionLauncher {
            permissionHandler.requestCameraPermission { granted ->
                onResult(granted)
            }
        }
    }
}

/**
 * iOS implementation of Bluetooth permission check.
 * Uses CBCentralManager.authorization and CBPeripheralManager.authorizationStatus.
 *
 * Returns true if both Central (scanning) and Peripheral (advertising) modes
 * are authorized.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun isBluetoothPermissionGranted(): Boolean {
    // Check both Central and Peripheral authorization
    val centralAuth = CBCentralManager.authorization
    val peripheralAuth = CBPeripheralManager.authorizationStatus()

    return centralAuth == CBManagerAuthorizationAllowedAlways &&
        peripheralAuth == CBManagerAuthorizationAllowedAlways
}

/**
 * iOS implementation of notification permission check.
 * Uses UNUserNotificationCenter.getNotificationSettings().
 *
 * Note: This returns a cached state since the actual check is async.
 * The state updates when the permission changes.
 */
@Composable
actual fun isNotificationPermissionGranted(): Boolean {
    var isGranted by remember { mutableStateOf(false) }

    // Check notification settings asynchronously
    remember {
        UNUserNotificationCenter
            .currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                isGranted = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
            }
    }

    return isGranted
}

/**
 * iOS implementation of camera permission check.
 * Uses AVCaptureDevice.authorizationStatus(for: .video).
 */
@Composable
actual fun isCameraPermissionGranted(): Boolean {
    val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
    return status == AVAuthorizationStatusAuthorized
}
