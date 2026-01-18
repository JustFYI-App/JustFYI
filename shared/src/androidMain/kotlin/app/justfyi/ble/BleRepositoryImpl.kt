package app.justfyi.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.repository.BleError
import app.justfyi.domain.repository.BleRepository
import app.justfyi.domain.repository.BluetoothState
import app.justfyi.domain.repository.UserRepository
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implementation of BleRepository for Android.
 * Coordinates BLE advertising, scanning, and GATT server operations.
 *
 * This repository:
 * - Starts advertising and scanning when discovery begins
 * - Provides a Flow of nearby users discovered via scanning
 * - Manages Bluetooth state changes
 * - Handles permission checks
 */
@Inject
class BleRepositoryImpl(
    private val context: Context,
    private val bleAdvertiser: BleAdvertiser,
    private val bleScanner: BleScanner,
    private val bleGattServer: BleGattServer,
    private val permissionHandler: BlePermissionHandler,
    private val userRepository: UserRepository,
    private val dispatchers: AppCoroutineDispatchers,
) : BleRepository {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _bluetoothState = MutableStateFlow(BluetoothState.NOT_AVAILABLE)
    override val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    private var bluetoothReceiver: BroadcastReceiver? = null

    init {
        // Initialize Bluetooth state
        updateBluetoothState()
        registerBluetoothReceiver()

        // Observe user changes and update BLE GATT server when username changes
        scope.launch {
            userRepository.observeCurrentUser().collect { user ->
                if (user != null && _isDiscovering.value) {
                    Log.d(TAG, "User data changed while discovering, updating GATT server")
                    bleGattServer.updateUserData(user.anonymousId, user.username)
                }
            }
        }
    }

    override suspend fun startDiscovery(): Result<Unit> =
        withContext(dispatchers.io) {
            Log.d(TAG, "Starting BLE discovery")

            // Check if Bluetooth is enabled FIRST
            // This is important because isMultipleAdvertisementSupported returns false
            // when Bluetooth is off, which would incorrectly report "BLE not supported"
            if (bluetoothAdapter?.isEnabled != true) {
                Log.e(TAG, "Bluetooth is disabled")
                return@withContext Result.failure(BleError.BluetoothDisabled)
            }

            // Check if BLE is supported (now that Bluetooth is on, we can accurately check)
            if (!isBleSupported()) {
                Log.e(TAG, "BLE is not supported on this device")
                return@withContext Result.failure(BleError.NotSupported)
            }

            // Check permissions
            if (!hasRequiredPermissions()) {
                val missing = permissionHandler.getMissingPermissions()
                Log.e(TAG, "Missing permissions: $missing")
                return@withContext Result.failure(BleError.PermissionDenied(missing))
            }

            // Get current user data for GATT server
            val currentUser = userRepository.getCurrentUser()
            if (currentUser == null) {
                Log.e(TAG, "No user signed in")
                return@withContext Result.failure(
                    BleError.AdvertisingFailed("User not signed in"),
                )
            }

            // Start GATT server
            val gattResult = bleGattServer.start(currentUser.anonymousId, currentUser.username)
            if (gattResult.isFailure) {
                Log.e(TAG, "Failed to start GATT server", gattResult.exceptionOrNull())
                return@withContext Result.failure(
                    BleError.GattServerFailed(
                        gattResult.exceptionOrNull()?.message ?: "Unknown error",
                    ),
                )
            }

            // Start advertising
            val advertiseResult = bleAdvertiser.startAdvertising()
            if (advertiseResult.isFailure) {
                Log.e(TAG, "Failed to start advertising", advertiseResult.exceptionOrNull())
                // Stop GATT server since we couldn't advertise
                bleGattServer.stop()
                return@withContext Result.failure(
                    BleError.AdvertisingFailed(
                        advertiseResult.exceptionOrNull()?.message ?: "Unknown error",
                    ),
                )
            }

            // Start scanning
            val scanResult = bleScanner.startScanning()
            if (scanResult.isFailure) {
                Log.e(TAG, "Failed to start scanning", scanResult.exceptionOrNull())
                // Stop advertising and GATT server since we couldn't scan
                bleAdvertiser.stopAdvertising()
                bleGattServer.stop()
                return@withContext Result.failure(
                    BleError.ScanFailed(
                        scanResult.exceptionOrNull()?.message ?: "Unknown error",
                    ),
                )
            }

            _isDiscovering.value = true
            Log.d(TAG, "BLE discovery started successfully")
            Result.success(Unit)
        }

    override suspend fun stopDiscovery() {
        withContext(dispatchers.io) {
            Log.d(TAG, "Stopping BLE discovery")

            bleScanner.stopScanning()
            bleAdvertiser.stopAdvertising()
            bleGattServer.stop()

            _isDiscovering.value = false
            Log.d(TAG, "BLE discovery stopped")
        }
    }

    override fun getNearbyUsers(): Flow<List<NearbyUser>> = bleScanner.nearbyUsers

    override suspend fun clearNearbyUsers() {
        withContext(dispatchers.io) {
            bleScanner.clearDiscoveredUsers()
        }
    }

    override fun isBleSupported(): Boolean {
        // Check if device has Bluetooth LE feature
        val hasBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

        // Check if adapter is available
        val hasAdapter = bluetoothAdapter != null

        // Check if advertising is supported
        val canAdvertise = bleAdvertiser.isAdvertisingSupported()

        Log.d(TAG, "BLE support - hasBle: $hasBle, hasAdapter: $hasAdapter, canAdvertise: $canAdvertise")

        return hasBle && hasAdapter && canAdvertise
    }

    override fun hasRequiredPermissions(): Boolean = permissionHandler.hasAllPermissions()

    override fun getRequiredPermissions(): List<String> = permissionHandler.getRequiredPermissions()

    /**
     * Updates the exposed Bluetooth state based on adapter state.
     */
    private fun updateBluetoothState() {
        val adapter = bluetoothAdapter
        _bluetoothState.value =
            when {
                adapter == null -> BluetoothState.NOT_AVAILABLE
                adapter.isEnabled -> BluetoothState.ON
                else -> BluetoothState.OFF
            }
    }

    /**
     * Registers a BroadcastReceiver to listen for Bluetooth state changes.
     */
    private fun registerBluetoothReceiver() {
        bluetoothReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                        val state =
                            intent.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.ERROR,
                            )

                        _bluetoothState.value =
                            when (state) {
                                BluetoothAdapter.STATE_ON -> BluetoothState.ON
                                BluetoothAdapter.STATE_OFF -> BluetoothState.OFF
                                BluetoothAdapter.STATE_TURNING_ON -> BluetoothState.TURNING_ON
                                BluetoothAdapter.STATE_TURNING_OFF -> BluetoothState.TURNING_OFF
                                else -> BluetoothState.NOT_AVAILABLE
                            }

                        Log.d(TAG, "Bluetooth state changed: ${_bluetoothState.value}")

                        // If Bluetooth was turned off, stop discovery
                        if (_bluetoothState.value == BluetoothState.OFF && _isDiscovering.value) {
                            scope.launch {
                                stopDiscovery()
                            }
                        }
                    }
                }
            }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothReceiver, filter)
    }

    /**
     * Cleans up resources. Should be called when the repository is no longer needed.
     */
    fun cleanup() {
        try {
            bluetoothReceiver?.let { receiver ->
                context.unregisterReceiver(receiver)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering Bluetooth receiver", e)
        }
        bluetoothReceiver = null
    }

    companion object {
        private const val TAG = "BleRepositoryImpl"
    }
}
