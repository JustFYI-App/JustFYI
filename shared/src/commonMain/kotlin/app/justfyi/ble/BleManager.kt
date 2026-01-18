package app.justfyi.ble

import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.repository.BluetoothState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic BLE manager interface.
 *
 * This interface defines the common BLE operations that both Android and iOS
 * must implement. It provides:
 * - BLE advertising to broadcast user identity
 * - BLE scanning to discover nearby Just FYI users
 * - GATT server/client handling for reading user data
 * - Bluetooth state observation
 *
 * Android implementation uses BluetoothLeAdvertiser/Scanner and BluetoothGattServer.
 * iOS implementation uses CBPeripheralManager/CBCentralManager.
 */
interface BleManager {
    /**
     * Current discovery state.
     * True if both advertising and scanning are active.
     */
    val isDiscovering: StateFlow<Boolean>

    /**
     * Current Bluetooth adapter/manager state.
     */
    val bluetoothState: StateFlow<BluetoothState>

    /**
     * Starts BLE discovery (advertising + scanning).
     *
     * Prerequisites:
     * - Bluetooth is enabled
     * - Required permissions are granted
     * - User is authenticated
     *
     * @param anonymousId The current user's Firebase anonymous ID
     * @param username The current user's display name
     * @return Result indicating success or failure with error details
     */
    suspend fun startDiscovery(
        anonymousId: String,
        username: String,
    ): Result<Unit>

    /**
     * Stops all BLE operations (advertising + scanning).
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
     * Checks if Bluetooth is currently enabled.
     * @return true if Bluetooth is on
     */
    fun isBluetoothEnabled(): Boolean

    /**
     * Updates the user data exposed via BLE GATT characteristics.
     * Call this when the user's username changes while discovery is active.
     *
     * @param anonymousId The user's Firebase anonymous ID
     * @param username The user's updated display name
     */
    fun updateUserData(
        anonymousId: String,
        username: String,
    )

    /**
     * Cleans up resources. Should be called when the manager is no longer needed.
     */
    fun cleanup()
}
