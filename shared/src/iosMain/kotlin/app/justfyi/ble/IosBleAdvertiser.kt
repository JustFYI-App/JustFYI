package app.justfyi.ble

import app.justfyi.domain.repository.BluetoothState
import app.justfyi.util.Constants
import app.justfyi.util.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOff
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheralManagerStateResetting
import platform.CoreBluetooth.CBPeripheralManagerStateUnauthorized
import platform.CoreBluetooth.CBPeripheralManagerStateUnknown
import platform.CoreBluetooth.CBPeripheralManagerStateUnsupported
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * iOS implementation of BLE advertising using CBPeripheralManager.
 *
 * This class manages:
 * - Advertising the Just FYI service UUID for discovery by other devices
 * - Broadcasting presence so Android and iOS devices can discover this user
 * - Managing the peripheral manager lifecycle
 *
 * Note: The actual user data (hashed ID, username) is exposed via GATT
 * characteristics in IosBleGattHandler. This advertiser only broadcasts
 * the service UUID to enable discovery filtering.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBleAdvertiser {
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _bluetoothState = MutableStateFlow(BluetoothState.NOT_AVAILABLE)
    val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    private var peripheralManager: CBPeripheralManager? = null
    private var peripheralManagerDelegate: PeripheralManagerDelegate? = null

    // Flag to track if we should start advertising when Bluetooth becomes ready
    private var pendingAdvertisingStart = false

    /**
     * Just FYI service UUID for cross-platform discovery.
     * Must match the Android implementation exactly.
     */
    private val justFyiServiceUuid: CBUUID by lazy {
        CBUUID.UUIDWithString(Constants.Ble.SERVICE_UUID)
    }

    init {
        initializePeripheralManager()
    }

    /**
     * Initializes the CBPeripheralManager with delegate.
     */
    private fun initializePeripheralManager() {
        peripheralManagerDelegate =
            PeripheralManagerDelegate(
                onStateUpdate = { state ->
                    _bluetoothState.value = mapCBPeripheralManagerState(state)
                    Logger.d(TAG, "Peripheral manager state changed: ${_bluetoothState.value}")

                    // Start advertising if it was pending and Bluetooth is now on
                    if (pendingAdvertisingStart && _bluetoothState.value == BluetoothState.ON) {
                        pendingAdvertisingStart = false
                        startAdvertisingInternal()
                    }
                },
                onAdvertisingStarted = { error ->
                    if (error != null) {
                        Logger.e(TAG, "Advertising failed: ${error.localizedDescription}")
                        _isAdvertising.value = false
                    } else {
                        Logger.d(TAG, "Advertising started successfully")
                        _isAdvertising.value = true
                    }
                },
            )

        peripheralManager =
            CBPeripheralManager(
                delegate = peripheralManagerDelegate,
                queue = null,
            )
    }

    /**
     * Starts BLE advertising with the Just FYI service UUID.
     *
     * The actual user data is exposed via GATT characteristics in IosBleGattHandler.
     * This only advertises the service UUID to enable discovery.
     *
     * @return Result indicating success or failure
     */
    fun startAdvertising(): Result<Unit> {
        if (_isAdvertising.value) {
            Logger.d(TAG, "Already advertising")
            return Result.success(Unit)
        }

        val manager = peripheralManager
        if (manager == null) {
            Logger.e(TAG, "CBPeripheralManager not available")
            return Result.failure(IllegalStateException("CBPeripheralManager not available"))
        }

        // If Bluetooth is not ready yet, set flag to start when it becomes ready
        if (_bluetoothState.value != BluetoothState.ON) {
            Logger.d(TAG, "Bluetooth not ready, will start advertising when available")
            pendingAdvertisingStart = true
            return Result.success(Unit)
        }

        return startAdvertisingInternal()
    }

    /**
     * Internal method to start advertising when Bluetooth is ready.
     */
    private fun startAdvertisingInternal(): Result<Unit> {
        val manager =
            peripheralManager ?: return Result.failure(
                IllegalStateException("CBPeripheralManager not available"),
            )

        try {
            // Build advertisement data
            val advertisementData = buildAdvertisementData()

            manager.startAdvertising(advertisementData)
            Logger.d(TAG, "Started advertising for service: ${Constants.Ble.SERVICE_UUID}")

            return Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start advertising: ${e.message}")
            return Result.failure(e)
        }
    }

    /**
     * Stops BLE advertising.
     */
    fun stopAdvertising() {
        pendingAdvertisingStart = false

        if (!_isAdvertising.value) {
            Logger.d(TAG, "Not currently advertising")
            return
        }

        try {
            peripheralManager?.stopAdvertising()
            Logger.d(TAG, "Stopped advertising")
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping advertising: ${e.message}")
        } finally {
            _isAdvertising.value = false
        }
    }

    /**
     * Checks if the device supports BLE advertising.
     * Most iOS devices that support Core Bluetooth can advertise.
     */
    fun isAdvertisingSupported(): Boolean {
        // iOS devices with Core Bluetooth support can generally advertise
        // The state check is done via the peripheral manager state
        return _bluetoothState.value != BluetoothState.NOT_AVAILABLE
    }

    /**
     * Cleans up resources.
     */
    fun cleanup() {
        stopAdvertising()
        peripheralManager = null
        peripheralManagerDelegate = null
    }

    /**
     * Builds the advertisement data dictionary.
     *
     * Contains:
     * - Service UUIDs for discovery filtering
     * - No device name (privacy)
     */
    private fun buildAdvertisementData(): Map<Any?, *> =
        mapOf(
            CBAdvertisementDataServiceUUIDsKey to listOf(justFyiServiceUuid),
            // Don't include device name for privacy
            // CBAdvertisementDataLocalNameKey to "Just FYI"
        )

    /**
     * Maps CBPeripheralManager state to BluetoothState.
     */
    private fun mapCBPeripheralManagerState(state: Long): BluetoothState =
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

    companion object {
        private const val TAG = "IosBleAdvertiser"
    }
}

/**
 * CBPeripheralManager delegate implementation.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class PeripheralManagerDelegate(
    private val onStateUpdate: (Long) -> Unit,
    private val onAdvertisingStarted: (NSError?) -> Unit,
) : NSObject(),
    CBPeripheralManagerDelegateProtocol {
    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
        onStateUpdate(peripheral.state.toLong())
    }

    override fun peripheralManagerDidStartAdvertising(
        peripheral: CBPeripheralManager,
        error: NSError?,
    ) {
        onAdvertisingStarted(error)
    }
}
