package app.justfyi.platform

import androidx.compose.runtime.Composable

/**
 * Multiplatform permission launcher interface.
 * Provides expect/actual pattern for permission handling across Android and iOS.
 *
 * On Android, this wraps rememberLauncherForActivityResult.
 * On iOS, this uses UNUserNotificationCenter and Core Bluetooth authorization APIs.
 */
interface PermissionLauncher {
    /**
     * Launch the permission request.
     */
    fun launch()
}

/**
 * Creates a permission launcher for Bluetooth permissions.
 *
 * @param onResult Callback with the result (true if granted, false otherwise)
 * @return PermissionLauncher instance
 */
@Composable
expect fun rememberBluetoothPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher

/**
 * Creates a permission launcher for notification permissions.
 *
 * @param onResult Callback with the result (true if granted, false otherwise)
 * @return PermissionLauncher instance
 */
@Composable
expect fun rememberNotificationPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher

/**
 * Creates a permission launcher for camera permissions (QR code backup).
 *
 * @param onResult Callback with the result (true if granted, false otherwise)
 * @return PermissionLauncher instance
 */
@Composable
expect fun rememberCameraPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher

/**
 * Checks if Bluetooth permissions are granted.
 *
 * @return true if all required Bluetooth permissions are granted
 */
@Composable
expect fun isBluetoothPermissionGranted(): Boolean

/**
 * Checks if notification permissions are granted.
 *
 * @return true if notification permission is granted
 */
@Composable
expect fun isNotificationPermissionGranted(): Boolean

/**
 * Checks if camera permissions are granted.
 *
 * @return true if camera permission is granted
 */
@Composable
expect fun isCameraPermissionGranted(): Boolean
