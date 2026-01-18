package app.justfyi.domain.repository

import app.justfyi.domain.model.NearbyUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for BLE discovery operations.
 * Manages BLE advertising and scanning for nearby Just FYI users.
 *
 * BLE discovery automatically starts when the app is in foreground and stops
 * when the app goes to background (per spec: no background BLE for MVP).
 */
interface BleRepository {
    /**
     * Current discovery state.
     * True if both advertising and scanning are active.
     */
    val isDiscovering: StateFlow<Boolean>

    /**
     * Current Bluetooth adapter state.
     */
    val bluetoothState: StateFlow<BluetoothState>

    /**
     * Starts BLE discovery (advertising + scanning).
     * Call when app comes to foreground or home screen is opened.
     *
     * Prerequisites:
     * - Bluetooth is enabled
     * - Required permissions are granted
     *
     * @return Result indicating success or failure with error details
     */
    suspend fun startDiscovery(): Result<Unit>

    /**
     * Stops all BLE operations (advertising + scanning).
     * Call when app goes to background or home screen is closed.
     */
    suspend fun stopDiscovery()

    /**
     * Observes the list of nearby Just FYI users.
     * The list is automatically updated as devices are discovered or become stale.
     * Stale devices (not seen for 30 seconds) are automatically removed.
     *
     * @return Flow of nearby users, sorted by signal strength (strongest first)
     */
    fun getNearbyUsers(): Flow<List<NearbyUser>>

    /**
     * Clears all discovered nearby users.
     * Useful when starting a fresh discovery session.
     */
    suspend fun clearNearbyUsers()

    /**
     * Checks if BLE is supported on this device.
     * @return true if the device supports BLE advertising and scanning
     */
    fun isBleSupported(): Boolean

    /**
     * Checks if all required BLE permissions are granted.
     * @return true if all required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean

    /**
     * Gets the list of required permissions for BLE operations.
     * The list varies based on Android version.
     * @return List of permission strings that need to be granted
     */
    fun getRequiredPermissions(): List<String>
}

/**
 * Represents the current state of the Bluetooth adapter.
 */
enum class BluetoothState {
    /** Bluetooth is on and ready */
    ON,

    /** Bluetooth is off */
    OFF,

    /** Bluetooth is turning on */
    TURNING_ON,

    /** Bluetooth is turning off */
    TURNING_OFF,

    /** Bluetooth is not available on this device */
    NOT_AVAILABLE,
}

/**
 * Sealed class representing BLE-related errors.
 */
sealed class BleError : Exception() {
    /** BLE is not supported on this device */
    data object NotSupported : BleError() {
        private fun readResolve(): Any = NotSupported

        override val message: String = "BLE is not supported on this device"
    }

    /** Bluetooth is disabled */
    data object BluetoothDisabled : BleError() {
        private fun readResolve(): Any = BluetoothDisabled

        override val message: String = "Bluetooth is disabled"
    }

    /** Required permissions are not granted */
    data class PermissionDenied(
        val permissions: List<String>,
    ) : BleError() {
        override val message: String = "Required permissions not granted: ${permissions.joinToString()}"
    }

    /** Advertising failed to start */
    data class AdvertisingFailed(
        override val message: String,
    ) : BleError()

    /** Scanning failed to start */
    data class ScanFailed(
        override val message: String,
    ) : BleError()

    /** GATT server setup failed */
    data class GattServerFailed(
        override val message: String,
    ) : BleError()
}
