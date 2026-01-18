package app.justfyi.ble

import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.repository.BluetoothState
import app.justfyi.util.Constants
import app.justfyi.util.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerStatePoweredOff
import platform.CoreBluetooth.CBCentralManagerStatePoweredOn
import platform.CoreBluetooth.CBCentralManagerStateResetting
import platform.CoreBluetooth.CBCentralManagerStateUnauthorized
import platform.CoreBluetooth.CBCentralManagerStateUnknown
import platform.CoreBluetooth.CBCentralManagerStateUnsupported
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * iOS implementation of BLE scanning using CBCentralManager.
 *
 * This class manages:
 * - Scanning for nearby Just FYI devices advertising our service UUID
 * - Connecting to peripherals to read their GATT characteristics
 * - Extracting user data (hashed ID, username) from characteristics
 * - Maintaining a list of nearby users with signal strength
 *
 * The scanner filters for the Just FYI service UUID to only discover
 * other Just FYI users, enabling cross-platform discovery with Android.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBleScanner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _bluetoothState = MutableStateFlow(BluetoothState.NOT_AVAILABLE)
    val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    private val _nearbyUsers = MutableStateFlow<List<NearbyUser>>(emptyList())
    val nearbyUsers: StateFlow<List<NearbyUser>> = _nearbyUsers.asStateFlow()

    // Map of anonymousIdHash -> NearbyUser for deduplication
    private val discoveredUsers = mutableMapOf<String, NearbyUser>()

    // Track peripherals we're connecting to with their RSSI values
    private val pendingConnections = mutableMapOf<String, Pair<CBPeripheral, Int>>()

    // Track peripherals we've processed recently
    private val processedPeripherals = mutableMapOf<String, Long>()

    // Partial user data during multi-characteristic read
    private val partialUserData = mutableMapOf<String, String>() // peripheralId -> userIdHash

    // The central manager and its delegate
    private var centralManager: CBCentralManager? = null
    private var centralManagerDelegate: CentralManagerDelegate? = null

    // Stale device cleanup job
    private var staleDeviceJob: kotlinx.coroutines.Job? = null

    // RSSI smoother for stable signal strength readings
    private val rssiSmoother = RssiSmoother()

    /**
     * Just FYI service UUID for cross-platform discovery.
     */
    private val justFyiServiceUuid: CBUUID by lazy {
        CBUUID.UUIDWithString(Constants.Ble.SERVICE_UUID)
    }

    /**
     * User ID characteristic UUID.
     */
    private val userIdCharacteristicUuid: CBUUID by lazy {
        CBUUID.UUIDWithString(Constants.Ble.USER_ID_CHARACTERISTIC_UUID)
    }

    /**
     * Username characteristic UUID.
     */
    private val usernameCharacteristicUuid: CBUUID by lazy {
        CBUUID.UUIDWithString(Constants.Ble.USERNAME_CHARACTERISTIC_UUID)
    }

    init {
        initializeCentralManager()
    }

    /**
     * Initializes the CBCentralManager with delegate.
     */
    private fun initializeCentralManager() {
        centralManagerDelegate =
            CentralManagerDelegate(
                onStateUpdate = { state ->
                    _bluetoothState.value = mapCBManagerState(state)
                    Logger.d(TAG, "Bluetooth state changed: ${_bluetoothState.value}")
                },
                onPeripheralDiscovered = { peripheral, rssi ->
                    handleDiscoveredPeripheral(peripheral, rssi)
                },
                onPeripheralConnected = { peripheral ->
                    handlePeripheralConnected(peripheral)
                },
                onPeripheralDisconnected = { peripheral ->
                    handlePeripheralDisconnected(peripheral)
                },
                onServicesDiscovered = { peripheral, error ->
                    handleServicesDiscovered(peripheral, error)
                },
                onCharacteristicRead = { peripheral, characteristic, error ->
                    handleCharacteristicRead(peripheral, characteristic, error)
                },
                userIdCharacteristicUuid = userIdCharacteristicUuid.UUIDString,
            )

        centralManager =
            CBCentralManager(
                delegate = centralManagerDelegate,
                queue = null,
            )
    }

    /**
     * Starts scanning for nearby Just FYI devices.
     *
     * @return Result indicating success or failure
     */
    fun startScanning(): Result<Unit> {
        if (_isScanning.value) {
            Logger.d(TAG, "Already scanning")
            return Result.success(Unit)
        }

        val manager = centralManager
        if (manager == null) {
            Logger.e(TAG, "CBCentralManager not available")
            return Result.failure(IllegalStateException("CBCentralManager not available"))
        }

        if (_bluetoothState.value != BluetoothState.ON) {
            Logger.e(TAG, "Bluetooth is not enabled")
            return Result.failure(IllegalStateException("Bluetooth is not enabled"))
        }

        // Clear previous data
        discoveredUsers.clear()
        processedPeripherals.clear()
        rssiSmoother.clear()
        _nearbyUsers.value = emptyList()

        try {
            // Start scanning with service UUID filter
            manager.scanForPeripheralsWithServices(
                serviceUUIDs = listOf(justFyiServiceUuid),
                options = null,
            )

            _isScanning.value = true
            Logger.d(TAG, "Started scanning for Just FYI devices")

            // Start stale device cleanup
            startStaleDeviceCleanup()

            return Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start scanning: ${e.message}")
            return Result.failure(e)
        }
    }

    /**
     * Stops BLE scanning and cleans up resources.
     */
    fun stopScanning() {
        if (!_isScanning.value) {
            Logger.d(TAG, "Not currently scanning")
            return
        }

        try {
            centralManager?.stopScan()
            Logger.d(TAG, "Stopped scanning")

            // Disconnect all pending peripherals
            pendingConnections.values.forEach { (peripheral, _) ->
                centralManager?.cancelPeripheralConnection(peripheral)
            }
            pendingConnections.clear()

            // Stop stale device cleanup
            staleDeviceJob?.cancel()
            staleDeviceJob = null
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping scan: ${e.message}")
        } finally {
            _isScanning.value = false
        }
    }

    /**
     * Clears all discovered nearby users.
     */
    fun clearDiscoveredUsers() {
        discoveredUsers.clear()
        processedPeripherals.clear()
        rssiSmoother.clear()
        _nearbyUsers.value = emptyList()
    }

    /**
     * Cleans up resources.
     */
    fun cleanup() {
        stopScanning()
        staleDeviceJob?.cancel()
        centralManager = null
        centralManagerDelegate = null
    }

    /**
     * Handles a discovered peripheral.
     */
    private fun handleDiscoveredPeripheral(
        peripheral: CBPeripheral,
        rssi: Int,
    ) {
        val peripheralId = peripheral.identifier.UUIDString

        // Check if we've recently processed this peripheral
        val lastProcessed = processedPeripherals[peripheralId]
        val now = currentTimeMillis()
        if (lastProcessed != null && now - lastProcessed < DEVICE_PROCESS_COOLDOWN_MS) {
            return
        }

        // Mark as being processed
        processedPeripherals[peripheralId] = now

        Logger.d(TAG, "Discovered Just FYI peripheral: $peripheralId, RSSI: $rssi")

        // Store peripheral with RSSI and connect to read characteristics
        pendingConnections[peripheralId] = Pair(peripheral, rssi)
        peripheral.delegate = centralManagerDelegate
        centralManager?.connectPeripheral(peripheral, options = null)
    }

    /**
     * Handles successful peripheral connection.
     */
    private fun handlePeripheralConnected(peripheral: CBPeripheral) {
        Logger.d(TAG, "Connected to peripheral: ${peripheral.identifier.UUIDString}")
        peripheral.discoverServices(listOf(justFyiServiceUuid))
    }

    /**
     * Handles peripheral disconnection.
     */
    private fun handlePeripheralDisconnected(peripheral: CBPeripheral) {
        val peripheralId = peripheral.identifier.UUIDString
        Logger.d(TAG, "Disconnected from peripheral: $peripheralId")
        pendingConnections.remove(peripheralId)
        partialUserData.remove(peripheralId)
    }

    /**
     * Handles service discovery completion.
     */
    private fun handleServicesDiscovered(
        peripheral: CBPeripheral,
        error: NSError?,
    ) {
        if (error != null) {
            Logger.e(TAG, "Service discovery failed: ${error.localizedDescription}")
            centralManager?.cancelPeripheralConnection(peripheral)
            return
        }

        @Suppress("UNCHECKED_CAST")
        val services = peripheral.services as? List<CBService> ?: emptyList()
        val justFyiService =
            services.find {
                it.UUID.UUIDString.equals(justFyiServiceUuid.UUIDString, ignoreCase = true)
            }

        if (justFyiService != null) {
            // Discover characteristics for this service
            peripheral.discoverCharacteristics(
                listOf(userIdCharacteristicUuid, usernameCharacteristicUuid),
                justFyiService,
            )
        } else {
            Logger.w(TAG, "Just FYI service not found on peripheral")
            centralManager?.cancelPeripheralConnection(peripheral)
        }
    }

    /**
     * Handles characteristic read completion.
     */
    private fun handleCharacteristicRead(
        peripheral: CBPeripheral,
        characteristic: CBCharacteristic,
        error: NSError?,
    ) {
        val peripheralId = peripheral.identifier.UUIDString

        if (error != null) {
            Logger.e(TAG, "Characteristic read failed: ${error.localizedDescription}")
            centralManager?.cancelPeripheralConnection(peripheral)
            return
        }

        val value = characteristic.value
        if (value == null) {
            Logger.w(TAG, "Characteristic value is null")
            return
        }

        val stringValue = nsDataToString(value)
        val characteristicUuid = characteristic.UUID.UUIDString

        when {
            characteristicUuid.equals(userIdCharacteristicUuid.UUIDString, ignoreCase = true) -> {
                // Store user ID hash and read username next
                partialUserData[peripheralId] = stringValue
                Logger.d(TAG, "Read user ID hash: ${stringValue.take(16)}...")

                // Find username characteristic and read it
                @Suppress("UNCHECKED_CAST")
                val services = peripheral.services as? List<CBService> ?: emptyList()
                val justFyiService =
                    services.find {
                        it.UUID.UUIDString.equals(justFyiServiceUuid.UUIDString, ignoreCase = true)
                    }

                @Suppress("UNCHECKED_CAST")
                val characteristics = justFyiService?.characteristics as? List<CBCharacteristic>
                val usernameChar =
                    characteristics?.find {
                        it.UUID.UUIDString.equals(usernameCharacteristicUuid.UUIDString, ignoreCase = true)
                    }

                if (usernameChar != null) {
                    peripheral.readValueForCharacteristic(usernameChar)
                } else {
                    Logger.w(TAG, "Username characteristic not found")
                    centralManager?.cancelPeripheralConnection(peripheral)
                }
            }
            characteristicUuid.equals(usernameCharacteristicUuid.UUIDString, ignoreCase = true) -> {
                // We have both pieces, create NearbyUser
                val userIdHash = partialUserData.remove(peripheralId)
                val username = stringValue
                Logger.d(TAG, "Read username: $username")

                if (userIdHash != null) {
                    // Get RSSI from stored value and apply smoothing
                    val rawRssi = pendingConnections[peripheralId]?.second ?: -70
                    val smoothedRssi = rssiSmoother.smooth(peripheralId, rawRssi)
                    val nearbyUser =
                        NearbyUser(
                            anonymousIdHash = userIdHash,
                            username = username,
                            signalStrength = smoothedRssi,
                            lastSeen = currentTimeMillis(),
                        )
                    addOrUpdateUser(nearbyUser)
                    Logger.d(TAG, "Added nearby user: $username, RSSI: $rawRssi (smoothed: $smoothedRssi)")
                }

                // Done reading, disconnect
                centralManager?.cancelPeripheralConnection(peripheral)
            }
        }
    }

    /**
     * Converts NSData to String using UTF-8 encoding.
     */
    private fun nsDataToString(data: NSData): String {
        val length = data.length.toInt()
        if (length == 0) return ""

        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, length.convert())
        }
        return bytes.decodeToString()
    }

    /**
     * Adds a new user or updates an existing one.
     */
    private fun addOrUpdateUser(user: NearbyUser) {
        discoveredUsers[user.anonymousIdHash] = user
        updateNearbyUsersList()
    }

    /**
     * Updates the exposed list of nearby users.
     */
    private fun updateNearbyUsersList() {
        val sortedUsers =
            discoveredUsers.values
                .sortedByDescending { it.signalStrength }
                .toList()
        _nearbyUsers.value = sortedUsers
    }

    /**
     * Starts background job to remove stale devices.
     */
    private fun startStaleDeviceCleanup() {
        staleDeviceJob =
            scope.launch {
                while (isActive) {
                    delay(STALE_CHECK_INTERVAL_MS)
                    removeStaleDevices()
                }
            }
    }

    /**
     * Removes devices that haven't been seen recently.
     */
    private fun removeStaleDevices() {
        val now = currentTimeMillis()
        val staleThreshold = Constants.Ble.STALE_DEVICE_THRESHOLD_MS

        val staleUsers =
            discoveredUsers
                .filter { (_, user) ->
                    now - user.lastSeen > staleThreshold
                }.keys
                .toList()

        if (staleUsers.isNotEmpty()) {
            staleUsers.forEach { key ->
                discoveredUsers.remove(key)
                Logger.d(TAG, "Removed stale user: $key")
            }
            updateNearbyUsersList()
        }

        // Clean up processed peripherals and RSSI smoother
        val stalePeripherals =
            processedPeripherals
                .filter { (_, timestamp) ->
                    now - timestamp > staleThreshold
                }.keys
                .toList()
        stalePeripherals.forEach { peripheralId ->
            processedPeripherals.remove(peripheralId)
            rssiSmoother.removeDevice(peripheralId)
        }
    }

    /**
     * Maps CBCentralManager state to BluetoothState.
     */
    private fun mapCBManagerState(state: Long): BluetoothState =
        when (state) {
            CBCentralManagerStatePoweredOn.toLong() -> BluetoothState.ON
            CBCentralManagerStatePoweredOff.toLong() -> BluetoothState.OFF
            CBCentralManagerStateResetting.toLong() -> BluetoothState.TURNING_OFF
            CBCentralManagerStateUnknown.toLong(),
            CBCentralManagerStateUnsupported.toLong(),
            CBCentralManagerStateUnauthorized.toLong(),
            -> BluetoothState.NOT_AVAILABLE
            else -> BluetoothState.NOT_AVAILABLE
        }

    /**
     * Gets current time in milliseconds.
     */
    private fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

    companion object {
        private const val TAG = "IosBleScanner"
        private const val STALE_CHECK_INTERVAL_MS = 10_000L
        private const val DEVICE_PROCESS_COOLDOWN_MS = 5_000L
    }
}

/**
 * CBCentralManager and CBPeripheral delegate implementation.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class CentralManagerDelegate(
    private val onStateUpdate: (Long) -> Unit,
    private val onPeripheralDiscovered: (CBPeripheral, Int) -> Unit,
    private val onPeripheralConnected: (CBPeripheral) -> Unit,
    private val onPeripheralDisconnected: (CBPeripheral) -> Unit,
    private val onServicesDiscovered: (CBPeripheral, NSError?) -> Unit,
    private val onCharacteristicRead: (CBPeripheral, CBCharacteristic, NSError?) -> Unit,
    private val userIdCharacteristicUuid: String,
) : NSObject(),
    CBCentralManagerDelegateProtocol,
    CBPeripheralDelegateProtocol {
    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        onStateUpdate(central.state.toLong())
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        onPeripheralDiscovered(didDiscoverPeripheral, RSSI.intValue)
    }

    override fun centralManager(
        central: CBCentralManager,
        didConnectPeripheral: CBPeripheral,
    ) {
        onPeripheralConnected(didConnectPeripheral)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        onPeripheralDisconnected(didDisconnectPeripheral)
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverServices: NSError?,
    ) {
        onServicesDiscovered(peripheral, didDiscoverServices)
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?,
    ) {
        if (error != null) {
            return
        }

        // Read user ID characteristic first
        @Suppress("UNCHECKED_CAST")
        val characteristics = didDiscoverCharacteristicsForService.characteristics as? List<CBCharacteristic>
        val userIdChar =
            characteristics?.find {
                it.UUID.UUIDString.equals(userIdCharacteristicUuid, ignoreCase = true)
            }
        userIdChar?.let { peripheral.readValueForCharacteristic(it) }
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        onCharacteristicRead(peripheral, didUpdateValueForCharacteristic, error)
    }
}
