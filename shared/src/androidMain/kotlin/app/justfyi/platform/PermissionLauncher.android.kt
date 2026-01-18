package app.justfyi.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.justfyi.util.Logger

private const val TAG = "PermissionLauncher"

/**
 * Android implementation of PermissionLauncher wrapping ActivityResultLauncher.
 */
private class AndroidPermissionLauncher(
    private val launchPermission: () -> Unit,
) : PermissionLauncher {
    override fun launch() {
        Logger.d(TAG, "AndroidPermissionLauncher.launch() called")
        launchPermission()
    }
}

/**
 * Android implementation of Bluetooth permission launcher.
 * With minSdk 31 (Android 12+), only BLE-specific permissions are needed.
 */
@Composable
actual fun rememberBluetoothPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher {
    val permissions =
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

    val currentOnResult by rememberUpdatedState(onResult)

    Logger.d(TAG, "rememberBluetoothPermissionLauncher: permissions=${permissions.joinToString()}")

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissionsResult ->
            Logger.d(TAG, "Bluetooth permission result: $permissionsResult")
            val allGranted = permissionsResult.values.all { it }
            currentOnResult(allGranted)
        }

    return remember(launcher) {
        AndroidPermissionLauncher {
            Logger.d(TAG, "Launching Bluetooth permission request for: ${permissions.joinToString()}")
            launcher.launch(permissions)
        }
    }
}

/**
 * Android implementation of notification permission launcher.
 * Only needed for Android 13+ (TIRAMISU).
 */
@Composable
actual fun rememberNotificationPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher {
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            currentOnResult(isGranted)
        }

    return remember(launcher, currentOnResult) {
        AndroidPermissionLauncher {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // No runtime permission needed before Android 13
                currentOnResult(true)
            }
        }
    }
}

/**
 * Android implementation of camera permission launcher.
 */
@Composable
actual fun rememberCameraPermissionLauncher(onResult: (Boolean) -> Unit): PermissionLauncher {
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            currentOnResult(isGranted)
        }

    return remember(launcher) {
        AndroidPermissionLauncher {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
}

/**
 * Android implementation of Bluetooth permission check.
 * With minSdk 31 (Android 12+), only BLE-specific permissions are checked.
 */
@Composable
actual fun isBluetoothPermissionGranted(): Boolean {
    val context = LocalContext.current

    return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
}

/**
 * Android implementation of notification permission check.
 */
@Composable
actual fun isNotificationPermissionGranted(): Boolean {
    val context = LocalContext.current

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        // No runtime permission needed before Android 13
        true
    }
}

/**
 * Android implementation of camera permission check.
 */
@Composable
actual fun isCameraPermissionGranted(): Boolean {
    val context = LocalContext.current
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
