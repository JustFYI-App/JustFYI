package app.justfyi.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import app.justfyi.domain.model.NearbyUser
import app.justfyi.util.Constants
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages BLE scanning for nearby Just FYI users.
 * Discovers devices advertising the Just FYI service UUID and reads their
 * user data (hashed ID and username) via GATT connection.
 *
 * The scanner:
 * - Filters for Just FYI service UUID only
 * - Connects via GATT to read user characteristics
 * - Emits a list of nearby users as a Flow
 * - Filters duplicates and removes stale devices
 */
@Inject
class BleScanner(
    private val context: Context,
) {
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private val scanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _nearbyUsers = MutableStateFlow<List<NearbyUser>>(emptyList())
    val nearbyUsers: StateFlow<List<NearbyUser>> = _nearbyUsers.asStateFlow()

    // Map of anonymousIdHash -> NearbyUser for deduplication
    private val discoveredUsers = ConcurrentHashMap<String, NearbyUser>()

    // Map of deviceAddress -> anonymousIdHash for signal strength updates
    private val deviceToUserMapping = ConcurrentHashMap<String, String>()

    // Track devices we're currently connecting to
    private val pendingConnections = ConcurrentHashMap<String, BluetoothGatt>()

    // Track devices we've already processed to avoid repeated connections
    private val processedDevices = ConcurrentHashMap<String, Long>()

    // RSSI smoother for stable signal strength readings
    private val rssiSmoother = RssiSmoother()

    private var scanCallback: ScanCallback? = null
    private var staleDeviceJob: Job? = null
    private var scanWatchdogJob: Job? = null

    // Track last scan result time for watchdog
    @Volatile
    private var lastScanResultTime: Long = 0

    /**
     * Starts scanning for nearby Just FYI devices.
     * Filters for devices advertising the Just FYI service UUID.
     *
     * @return Result indicating success or failure
     */
    fun startScanning(): Result<Unit> {
        if (_isScanning.value) {
            Log.d(TAG, "Already scanning")
            return Result.success(Unit)
        }

        val bleScanner = scanner
        if (bleScanner == null) {
            Log.e(TAG, "BLE scanner not available")
            return Result.failure(IllegalStateException("BLE scanner not available"))
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            return Result.failure(IllegalStateException("Bluetooth is not enabled"))
        }

        // Clear previous data
        discoveredUsers.clear()
        deviceToUserMapping.clear()
        processedDevices.clear()
        rssiSmoother.clear()
        _nearbyUsers.value = emptyList()

        val filters = buildScanFilters()
        val settings = buildScanSettings()

        scanCallback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    handleScanResult(result)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { handleScanResult(it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Scan failed with error code: $errorCode (${getScanErrorName(errorCode)})")
                    _isScanning.value = false
                    handleScanFailure(errorCode)
                }
            }

        try {
            bleScanner.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            lastScanResultTime = System.currentTimeMillis()
            Log.d(TAG, "Started scanning for Just FYI devices")

            // Start stale device cleanup job
            startStaleDeviceCleanup()

            // Start scan watchdog to detect stuck scanner
            startScanWatchdog()

            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scanning", e)
            return Result.failure(e)
        }
    }

    /**
     * Stops BLE scanning and cleans up resources.
     */
    fun stopScanning() {
        if (!_isScanning.value) {
            Log.d(TAG, "Not currently scanning")
            return
        }

        try {
            scanCallback?.let { callback ->
                scanner?.stopScan(callback)
                Log.d(TAG, "Stopped scanning")
            }

            // Close all pending GATT connections
            pendingConnections.values.forEach { gatt ->
                try {
                    gatt.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing GATT connection", e)
                }
            }
            pendingConnections.clear()

            // Stop stale device cleanup
            staleDeviceJob?.cancel()
            staleDeviceJob = null

            // Stop watchdog
            scanWatchdogJob?.cancel()
            scanWatchdogJob = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        } finally {
            scanCallback = null
            _isScanning.value = false
        }
    }

    /**
     * Clears all discovered nearby users.
     */
    fun clearDiscoveredUsers() {
        discoveredUsers.clear()
        deviceToUserMapping.clear()
        processedDevices.clear()
        rssiSmoother.clear()
        _nearbyUsers.value = emptyList()
    }


    /**
     * Handles a scan result by connecting to the device and reading its characteristics.
     */
    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val deviceAddress = device.address
        val now = System.currentTimeMillis()

        // Track that we received a result (for watchdog)
        lastScanResultTime = now

        // Check if we've recently processed this device
        val lastProcessed = processedDevices[deviceAddress]
        if (lastProcessed != null && now - lastProcessed < DEVICE_PROCESS_COOLDOWN_MS) {
            // Update signal strength for existing users from this device
            updateSignalStrengthForDevice(deviceAddress, result.rssi)
            return
        }

        // Mark device as being processed
        processedDevices[deviceAddress] = now

        Log.d(TAG, "Discovered Just FYI device: $deviceAddress, RSSI: ${result.rssi}")

        // Connect to device to read characteristics
        connectAndReadCharacteristics(device, result.rssi)
    }

    /**
     * Updates signal strength for a user from a specific device.
     * Uses EMA smoothing for stable readings.
     */
    private fun updateSignalStrengthForDevice(
        deviceAddress: String,
        rssi: Int,
    ) {
        val userIdHash = deviceToUserMapping[deviceAddress] ?: return
        val existingUser = discoveredUsers[userIdHash] ?: return

        // Apply RSSI smoothing using device address as key
        val smoothedRssi = rssiSmoother.smooth(deviceAddress, rssi)

        val updatedUser =
            existingUser.copy(
                signalStrength = smoothedRssi,
                lastSeen = System.currentTimeMillis(),
            )
        discoveredUsers[userIdHash] = updatedUser
        updateNearbyUsersList()
        Log.d(TAG, "Updated signal strength for ${existingUser.username}: $rssi dBm (smoothed: $smoothedRssi dBm)")
    }

    /**
     * Connects to a device via GATT and reads the user characteristics.
     */
    private fun connectAndReadCharacteristics(
        device: BluetoothDevice,
        rssi: Int,
    ) {
        Log.d(TAG, "Initiating GATT connection to ${device.address}")
        try {
            val gattCallback =
                object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int,
                    ) {
                        val statusName = getGattStatusName(status)
                        val stateName = getConnectionStateName(newState)
                        Log.d(
                            TAG,
                            "GATT connection state changed: ${device.address} - status=$statusName, newState=$stateName",
                        )

                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                if (status == BluetoothGatt.GATT_SUCCESS) {
                                    Log.d(
                                        TAG,
                                        "Connected to GATT server: ${device.address}, requesting MTU...",
                                    )
                                    // Request larger MTU to read full SHA256 hash (64 bytes)
                                    // Default MTU is 23, which truncates the hash
                                    gatt.requestMtu(GATT_MTU_SIZE)
                                } else {
                                    Log.e(TAG, "Connection failed with status: $statusName")
                                    pendingConnections.remove(device.address)
                                    gatt.close()
                                }
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                Log.d(TAG, "Disconnected from GATT server: ${device.address}")
                                pendingConnections.remove(device.address)
                                gatt.close()
                            }
                        }
                    }

                    override fun onMtuChanged(
                        gatt: BluetoothGatt,
                        mtu: Int,
                        status: Int,
                    ) {
                        val statusName = getGattStatusName(status)
                        Log.d(TAG, "MTU changed for ${device.address}: mtu=$mtu, status=$statusName")
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "MTU negotiation successful, discovering services...")
                            gatt.discoverServices()
                        } else {
                            // MTU negotiation failed, try with default MTU anyway
                            Log.w(TAG, "MTU negotiation failed, trying with default MTU...")
                            gatt.discoverServices()
                        }
                    }

                    override fun onServicesDiscovered(
                        gatt: BluetoothGatt,
                        status: Int,
                    ) {
                        val statusName = getGattStatusName(status)
                        Log.d(TAG, "Services discovered for ${device.address}: status=$statusName")

                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val service = gatt.getService(UUID.fromString(Constants.Ble.SERVICE_UUID))
                            if (service != null) {
                                Log.d(TAG, "Found Just FYI service, reading USER_ID characteristic...")
                                // Read user ID hash first
                                val userIdChar =
                                    service.getCharacteristic(
                                        UUID.fromString(Constants.Ble.USER_ID_CHARACTERISTIC_UUID),
                                    )
                                if (userIdChar != null) {
                                    val readStarted = gatt.readCharacteristic(userIdChar)
                                    Log.d(TAG, "Read USER_ID characteristic initiated: $readStarted")
                                } else {
                                    Log.w(TAG, "User ID characteristic not found on ${device.address}")
                                    gatt.disconnect()
                                }
                            } else {
                                Log.w(TAG, "Just FYI service not found on device ${device.address}")
                                gatt.disconnect()
                            }
                        } else {
                            Log.e(TAG, "Service discovery failed for ${device.address} with status: $statusName")
                            gatt.disconnect()
                        }
                    }

                    @Deprecated(
                        "Deprecated in API 33",
                        ReplaceWith("onCharacteristicRead(gatt, characteristic, value, status)"),
                    )
                    @Suppress("DEPRECATION")
                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int,
                    ) {
                        val statusName = getGattStatusName(status)
                        Log.d(
                            TAG,
                            "Characteristic read callback (legacy): ${characteristic.uuid}, status=$statusName",
                        )
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            handleCharacteristicRead(gatt, characteristic, characteristic.value, rssi)
                        } else {
                            Log.e(TAG, "Characteristic read failed for ${device.address} with status: $statusName")
                            partialUserData.remove(device.address)
                            gatt.disconnect()
                        }
                    }

                    // Android 13+ callback
                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int,
                    ) {
                        val statusName = getGattStatusName(status)
                        Log.d(
                            TAG,
                            "Characteristic read callback (API 33+): ${characteristic.uuid}, status=$statusName",
                        )
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            handleCharacteristicRead(gatt, characteristic, value, rssi)
                        } else {
                            Log.e(TAG, "Characteristic read failed for ${device.address} with status: $statusName")
                            partialUserData.remove(device.address)
                            gatt.disconnect()
                        }
                    }
                }

            val gatt = device.connectGatt(context, false, gattCallback)
            if (gatt != null) {
                pendingConnections[device.address] = gatt
                Log.d(TAG, "GATT connection object created for ${device.address}")
            } else {
                Log.e(TAG, "Failed to create GATT connection for ${device.address}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to device: ${device.address}", e)
        }
    }

    /**
     * Gets a human-readable name for GATT status codes.
     */
    private fun getGattStatusName(status: Int): String =
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> "SUCCESS"
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> "READ_NOT_PERMITTED"
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "WRITE_NOT_PERMITTED"
            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "INSUFFICIENT_AUTHENTICATION"
            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "REQUEST_NOT_SUPPORTED"
            BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "INSUFFICIENT_ENCRYPTION"
            BluetoothGatt.GATT_INVALID_OFFSET -> "INVALID_OFFSET"
            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "INVALID_ATTRIBUTE_LENGTH"
            BluetoothGatt.GATT_CONNECTION_CONGESTED -> "CONNECTION_CONGESTED"
            BluetoothGatt.GATT_FAILURE -> "FAILURE"
            133 -> "GATT_ERROR (133)" // Common Android-specific error
            else -> "UNKNOWN ($status)"
        }

    /**
     * Gets a human-readable name for connection state.
     */
    private fun getConnectionStateName(state: Int): String =
        when (state) {
            BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
            BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
            BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
            BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
            else -> "UNKNOWN ($state)"
        }

    // Track partial data during multi-read
    private val partialUserData = ConcurrentHashMap<String, String>() // deviceAddress -> userIdHash

    /**
     * Handles characteristic read results and builds NearbyUser objects.
     */
    private fun handleCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        rssi: Int,
    ) {
        val deviceAddress = gatt.device.address
        val characteristicUuid = characteristic.uuid.toString()

        when (characteristicUuid.lowercase()) {
            Constants.Ble.USER_ID_CHARACTERISTIC_UUID.lowercase() -> {
                // Store user ID hash and read username next
                val userIdHash = String(value, Charsets.UTF_8)
                partialUserData[deviceAddress] = userIdHash
                Log.d(TAG, "Read user ID hash: $userIdHash")

                // Now read username
                val service = gatt.getService(UUID.fromString(Constants.Ble.SERVICE_UUID))
                val usernameChar =
                    service?.getCharacteristic(
                        UUID.fromString(Constants.Ble.USERNAME_CHARACTERISTIC_UUID),
                    )
                if (usernameChar != null) {
                    gatt.readCharacteristic(usernameChar)
                } else {
                    Log.w(TAG, "Username characteristic not found")
                    gatt.disconnect()
                }
            }
            Constants.Ble.USERNAME_CHARACTERISTIC_UUID.lowercase() -> {
                // We have both pieces of data, create NearbyUser
                val userIdHash = partialUserData.remove(deviceAddress)
                val username = String(value, Charsets.UTF_8)
                Log.d(TAG, "Read username: $username")

                if (userIdHash != null) {
                    // Apply initial RSSI smoothing
                    val smoothedRssi = rssiSmoother.smooth(deviceAddress, rssi)
                    val nearbyUser =
                        NearbyUser(
                            anonymousIdHash = userIdHash,
                            username = username,
                            signalStrength = smoothedRssi,
                            lastSeen = System.currentTimeMillis(),
                        )
                    // Store device address -> user mapping for signal strength updates
                    deviceToUserMapping[deviceAddress] = userIdHash
                    addOrUpdateUser(nearbyUser)
                    Log.d(
                        TAG,
                        "Added nearby user: $username (hash: ${userIdHash.take(
                            8,
                        )}...), RSSI: $rssi (smoothed: $smoothedRssi)",
                    )
                }

                // Done reading, disconnect
                gatt.disconnect()
            }
        }
    }

    /**
     * Adds a new user or updates an existing one (by anonymousIdHash).
     */
    private fun addOrUpdateUser(user: NearbyUser) {
        discoveredUsers[user.anonymousIdHash] = user
        updateNearbyUsersList()
    }

    /**
     * Updates the exposed list of nearby users, sorted by signal strength.
     */
    private fun updateNearbyUsersList() {
        val sortedUsers =
            discoveredUsers.values
                .sortedByDescending { it.signalStrength }
                .toList()
        _nearbyUsers.value = sortedUsers
    }

    /**
     * Starts a background job that removes stale devices periodically.
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
        val now = System.currentTimeMillis()
        val staleThreshold = Constants.Ble.STALE_DEVICE_THRESHOLD_MS

        val staleUsers =
            discoveredUsers
                .filter { (_, user) ->
                    now - user.lastSeen > staleThreshold
                }.keys

        if (staleUsers.isNotEmpty()) {
            staleUsers.forEach { key ->
                discoveredUsers.remove(key)
                Log.d(TAG, "Removed stale user: $key")
            }
            updateNearbyUsersList()
        }

        // Also clean up processedDevices and RSSI smoother to allow re-connection
        val staleDevices =
            processedDevices
                .filter { (_, timestamp) ->
                    now - timestamp > staleThreshold
                }.keys
        staleDevices.forEach { deviceAddress ->
            processedDevices.remove(deviceAddress)
            rssiSmoother.removeDevice(deviceAddress)
        }
    }

    /**
     * Builds scan filters for Just FYI service UUID.
     */
    private fun buildScanFilters(): List<ScanFilter> {
        val serviceUuid = ParcelUuid(UUID.fromString(Constants.Ble.SERVICE_UUID))
        val filter =
            ScanFilter
                .Builder()
                .setServiceUuid(serviceUuid)
                .build()
        return listOf(filter)
    }

    /**
     * Builds scan settings for balanced mode.
     */
    private fun buildScanSettings(): ScanSettings =
        ScanSettings
            .Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0) // Report results immediately
            .build()

    /**
     * Gets a human-readable name for scan error codes.
     */
    private fun getScanErrorName(errorCode: Int): String =
        when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REGISTRATION_FAILED"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
            else -> "UNKNOWN"
        }

    /**
     * Handles scan failure with automatic retry.
     * Retries indefinitely every RETRY_DELAY_MS as long as Bluetooth is enabled.
     */
    private fun handleScanFailure(errorCode: Int) {
        // Don't retry for unsupported features
        if (errorCode == ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED) {
            Log.e(TAG, "BLE scanning not supported on this device")
            return
        }

        Log.d(TAG, "Scheduling scan retry in ${RETRY_DELAY_MS}ms")

        scope.launch {
            delay(RETRY_DELAY_MS)
            if (!_isScanning.value && bluetoothAdapter?.isEnabled == true) {
                Log.d(TAG, "Retrying scan after failure...")
                startScanning()
            }
        }
    }

    /**
     * Starts a watchdog that restarts the scanner if no results are received.
     * This handles the case where the scanner silently stops working.
     */
    private fun startScanWatchdog() {
        scanWatchdogJob?.cancel()
        scanWatchdogJob = scope.launch {
            while (isActive && _isScanning.value) {
                delay(SCAN_WATCHDOG_INTERVAL_MS)

                if (!_isScanning.value) break

                val timeSinceLastResult = System.currentTimeMillis() - lastScanResultTime

                // If we haven't received any scan results in a while, restart the scanner
                // But only if there are other devices advertising (we can't know this for sure,
                // so we use a longer timeout)
                if (timeSinceLastResult > SCAN_WATCHDOG_TIMEOUT_MS) {
                    Log.w(
                        TAG,
                        "No scan results for ${timeSinceLastResult}ms, restarting scanner..."
                    )
                    restartScanning()
                }
            }
        }
    }

    /**
     * Restarts the scanner by stopping and starting it again.
     * This can help recover from stuck states.
     */
    private fun restartScanning() {
        Log.d(TAG, "Restarting BLE scanner...")

        // Stop current scan
        try {
            scanCallback?.let { callback ->
                scanner?.stopScan(callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan during restart", e)
        }

        // Close pending GATT connections
        pendingConnections.values.forEach { gatt ->
            try {
                gatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GATT during restart", e)
            }
        }
        pendingConnections.clear()

        // Reset state but keep discovered users
        scanCallback = null
        _isScanning.value = false

        // Small delay before restarting
        scope.launch {
            delay(500)
            if (bluetoothAdapter?.isEnabled == true) {
                startScanning()
            }
        }
    }

    companion object {
        private const val TAG = "BleScanner"
        private const val STALE_CHECK_INTERVAL_MS = 10_000L
        private const val DEVICE_PROCESS_COOLDOWN_MS = 5_000L // Don't re-process device for 5s

        // Retry delay when scan fails - retry indefinitely every 8 seconds
        private const val RETRY_DELAY_MS = 8_000L

        // Watchdog configuration - restart scanner if no results for this long
        private const val SCAN_WATCHDOG_INTERVAL_MS = 30_000L // Check every 30s
        private const val SCAN_WATCHDOG_TIMEOUT_MS = 60_000L // Restart if no results for 60s

        // GATT MTU size - request 128 bytes to fit SHA256 hash (64 hex chars) + overhead
        // Default MTU is 23 bytes which truncates the hash
        private const val GATT_MTU_SIZE = 128
    }
}
