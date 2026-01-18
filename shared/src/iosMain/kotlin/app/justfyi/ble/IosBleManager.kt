package app.justfyi.ble

import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.repository.BleError
import app.justfyi.domain.repository.BluetoothState
import app.justfyi.util.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOff
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheralManagerStateResetting
import platform.CoreBluetooth.CBPeripheralManagerStateUnauthorized
import platform.CoreBluetooth.CBPeripheralManagerStateUnknown
import platform.CoreBluetooth.CBPeripheralManagerStateUnsupported
import platform.CoreBluetooth.CBService
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * iOS implementation of BleManager using Core Bluetooth.
 *
 * This class coordinates all iOS BLE operations:
 * - IosBleScanner for discovering nearby Just FYI users via CBCentralManager
 * - IosBleAdvertiser for advertising presence via CBPeripheralManager
 * - IosBleGattHandler for exposing user data via GATT characteristics
 *
 * The implementation uses the same service UUID and characteristic structure
 * as the Android implementation to enable cross-platform discovery.
 *
 * Usage:
 * 1. Create IosBleManager instance
 * 2. Call startDiscovery() with user credentials when app enters foreground
 * 3. Observe getNearbyUsers() flow for discovered users
 * 4. Call stopDiscovery() when app enters background
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBleManager : BleManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Component instances
    private val bleScanner = IosBleScanner()
    private val bleAdvertiser = IosBleAdvertiser()
    private val bleGattHandler = IosBleGattHandler()

    // Shared peripheral manager for advertising and GATT
    private var peripheralManager: CBPeripheralManager? = null
    private var peripheralManagerDelegate: SharedPeripheralManagerDelegate? = null

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Bluetooth state from scanner (most reliable source)
    override val bluetoothState: StateFlow<BluetoothState>
        get() = bleScanner.bluetoothState

    init {
        initializePeripheralManager()
    }

    /**
     * Initializes the shared peripheral manager.
     */
    private fun initializePeripheralManager() {
        peripheralManagerDelegate =
            SharedPeripheralManagerDelegate(
                onStateUpdate = { state ->
                    Logger.d(TAG, "Peripheral manager state: ${mapPeripheralManagerState(state)}")
                },
                onReadRequest = { request ->
                    bleGattHandler.handleReadRequest(request)
                },
                onServiceAdded = { service, error ->
                    bleGattHandler.handleServiceAdded(service, error)
                },
            )

        peripheralManager =
            CBPeripheralManager(
                delegate = peripheralManagerDelegate,
                queue = null,
            )

        // Initialize GATT handler with the peripheral manager
        peripheralManager?.let { manager ->
            bleGattHandler.initialize(manager)
        }
    }

    /**
     * Maps peripheral manager state to BluetoothState.
     */
    private fun mapPeripheralManagerState(state: Long): BluetoothState =
        when (state) {
            CBPeripheralManagerStatePoweredOn.toLong() -> BluetoothState.ON
            CBPeripheralManagerStatePoweredOff.toLong() -> BluetoothState.OFF
            CBPeripheralManagerStateResetting.toLong() -> BluetoothState.TURNING_OFF
            CBPeripheralManagerStateUnknown.toLong(),
            CBPeripheralManagerStateUnsupported.toLong(),
            CBPeripheralManagerStateUnauthorized.toLong(),
            -> BluetoothState.NOT_AVAILABLE
            else -> BluetoothState.NOT_AVAILABLE
        }

    override suspend fun startDiscovery(
        anonymousId: String,
        username: String,
    ): Result<Unit> {
        Logger.d(TAG, "Starting BLE discovery")

        // Check if BLE is supported
        if (!isBleSupported()) {
            Logger.e(TAG, "BLE is not supported on this device")
            return Result.failure(
                BleError.NotSupported,
            )
        }

        // Check if Bluetooth is enabled
        if (!isBluetoothEnabled()) {
            Logger.e(TAG, "Bluetooth is disabled")
            return Result.failure(
                BleError.BluetoothDisabled,
            )
        }

        // Start GATT handler (exposes user data)
        val gattResult = bleGattHandler.start(anonymousId, username)
        if (gattResult.isFailure) {
            Logger.e(TAG, "Failed to start GATT handler: ${gattResult.exceptionOrNull()?.message}")
            return Result.failure(
                BleError.GattServerFailed(
                    gattResult.exceptionOrNull()?.message ?: "Unknown error",
                ),
            )
        }

        // Start advertising
        val advertiseResult = bleAdvertiser.startAdvertising()
        if (advertiseResult.isFailure) {
            Logger.e(TAG, "Failed to start advertising: ${advertiseResult.exceptionOrNull()?.message}")
            bleGattHandler.stop()
            return Result.failure(
                BleError.AdvertisingFailed(
                    advertiseResult.exceptionOrNull()?.message ?: "Unknown error",
                ),
            )
        }

        // Start scanning
        val scanResult = bleScanner.startScanning()
        if (scanResult.isFailure) {
            Logger.e(TAG, "Failed to start scanning: ${scanResult.exceptionOrNull()?.message}")
            bleAdvertiser.stopAdvertising()
            bleGattHandler.stop()
            return Result.failure(
                BleError.ScanFailed(
                    scanResult.exceptionOrNull()?.message ?: "Unknown error",
                ),
            )
        }

        _isDiscovering.value = true
        Logger.d(TAG, "BLE discovery started successfully")
        return Result.success(Unit)
    }

    override suspend fun stopDiscovery() {
        Logger.d(TAG, "Stopping BLE discovery")

        bleScanner.stopScanning()
        bleAdvertiser.stopAdvertising()
        bleGattHandler.stop()

        _isDiscovering.value = false
        Logger.d(TAG, "BLE discovery stopped")
    }

    override fun getNearbyUsers(): Flow<List<NearbyUser>> = bleScanner.nearbyUsers

    override suspend fun clearNearbyUsers() {
        bleScanner.clearDiscoveredUsers()
    }

    override fun isBleSupported(): Boolean {
        // iOS devices with Core Bluetooth support BLE
        // Check if the peripheral manager initialized properly
        return peripheralManager != null && bleAdvertiser.isAdvertisingSupported()
    }

    override fun isBluetoothEnabled(): Boolean = bluetoothState.value == BluetoothState.ON

    override fun updateUserData(
        anonymousId: String,
        username: String,
    ) {
        Logger.d(TAG, "Updating user data - username: $username")
        bleGattHandler.updateUserData(anonymousId, username)
    }

    override fun cleanup() {
        Logger.d(TAG, "Cleaning up BLE manager")

        bleScanner.cleanup()
        bleAdvertiser.cleanup()
        bleGattHandler.cleanup()

        peripheralManager = null
        peripheralManagerDelegate = null

        _isDiscovering.value = false
    }

    companion object {
        private const val TAG = "IosBleManager"
    }
}

/**
 * Shared peripheral manager delegate for coordinating GATT and advertising.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class SharedPeripheralManagerDelegate(
    private val onStateUpdate: (Long) -> Unit,
    private val onReadRequest: (CBATTRequest) -> Boolean,
    private val onServiceAdded: (CBService, NSError?) -> Unit,
) : NSObject(),
    CBPeripheralManagerDelegateProtocol {
    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
        onStateUpdate(peripheral.state.toLong())
    }

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didReceiveReadRequest: CBATTRequest,
    ) {
        onReadRequest(didReceiveReadRequest)
    }

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didAddService: CBService,
        error: NSError?,
    ) {
        onServiceAdded(didAddService, error)
    }
}
