package app.justfyi.ble

import app.justfyi.domain.model.NearbyUser
import app.justfyi.domain.repository.BleError
import app.justfyi.domain.repository.BleRepository
import app.justfyi.domain.repository.BluetoothState
import app.justfyi.domain.repository.UserRepository
import app.justfyi.util.AppCoroutineDispatchers
import app.justfyi.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS implementation of BleRepository using IosBleManager.
 *
 * This repository:
 * - Coordinates BLE advertising and scanning via IosBleManager
 * - Provides a Flow of nearby users discovered via scanning
 * - Manages Bluetooth state changes
 * - Handles iOS-specific permission checking
 *
 * The implementation mirrors the Android BleRepositoryImpl structure
 * to maintain consistency across platforms.
 */
class IosBleRepositoryImpl(
    private val bleManager: IosBleManager,
    private val userRepository: UserRepository,
    private val dispatchers: AppCoroutineDispatchers,
) : BleRepository {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override val isDiscovering: StateFlow<Boolean>
        get() = bleManager.isDiscovering

    override val bluetoothState: StateFlow<BluetoothState>
        get() = bleManager.bluetoothState

    init {
        // Monitor Bluetooth state changes
        scope.launch {
            bluetoothState.collect { state ->
                Logger.d(TAG, "Bluetooth state changed: $state")

                // If Bluetooth was turned off, stop discovery
                if (state == BluetoothState.OFF && isDiscovering.value) {
                    stopDiscovery()
                }
            }
        }

        // Observe user changes and update BLE when username changes
        scope.launch {
            userRepository.observeCurrentUser().collect { user ->
                if (user != null && isDiscovering.value) {
                    Logger.d(TAG, "User data changed while discovering, updating BLE")
                    bleManager.updateUserData(user.anonymousId, user.username)
                }
            }
        }
    }

    override suspend fun startDiscovery(): Result<Unit> =
        withContext(dispatchers.io) {
            Logger.d(TAG, "Starting BLE discovery")

            // Check if Bluetooth is enabled FIRST
            // This is important because some BLE checks may return incorrect results
            // when Bluetooth is off
            if (bluetoothState.value != BluetoothState.ON) {
                Logger.e(TAG, "Bluetooth is disabled")
                return@withContext Result.failure(BleError.BluetoothDisabled)
            }

            // Check if BLE is supported (now that Bluetooth is on, we can accurately check)
            if (!isBleSupported()) {
                Logger.e(TAG, "BLE is not supported on this device")
                return@withContext Result.failure(BleError.NotSupported)
            }

            // Check permissions (iOS handles this differently - via Core Bluetooth authorization)
            if (!hasRequiredPermissions()) {
                Logger.e(TAG, "Required permissions not granted")
                return@withContext Result.failure(
                    BleError.PermissionDenied(getRequiredPermissions()),
                )
            }

            // Get current user data for advertising
            val currentUser = userRepository.getCurrentUser()
            if (currentUser == null) {
                Logger.e(TAG, "No user signed in")
                return@withContext Result.failure(
                    BleError.AdvertisingFailed("User not signed in"),
                )
            }

            // Start discovery with user credentials
            val result =
                bleManager.startDiscovery(
                    anonymousId = currentUser.anonymousId,
                    username = currentUser.username,
                )

            if (result.isSuccess) {
                Logger.d(TAG, "BLE discovery started successfully")
            } else {
                Logger.e(TAG, "BLE discovery failed: ${result.exceptionOrNull()?.message}")
            }

            result
        }

    override suspend fun stopDiscovery() {
        withContext(dispatchers.io) {
            Logger.d(TAG, "Stopping BLE discovery")
            bleManager.stopDiscovery()
            Logger.d(TAG, "BLE discovery stopped")
        }
    }

    override fun getNearbyUsers(): Flow<List<NearbyUser>> = bleManager.getNearbyUsers()

    override suspend fun clearNearbyUsers() {
        withContext(dispatchers.io) {
            bleManager.clearNearbyUsers()
        }
    }

    override fun isBleSupported(): Boolean = bleManager.isBleSupported()

    override fun hasRequiredPermissions(): Boolean {
        // On iOS, Core Bluetooth handles authorization internally
        // The permission state is reflected in the CBManager state:
        // - CBManagerStateUnauthorized means no permission
        // - CBManagerStatePoweredOn means permission granted and Bluetooth is on
        //
        // If we get to this point with Bluetooth ON, permissions are granted
        return bluetoothState.value != BluetoothState.NOT_AVAILABLE
    }

    override fun getRequiredPermissions(): List<String> {
        // iOS uses Info.plist keys for permission descriptions, not runtime permissions
        // These are for documentation/display purposes
        return listOf(
            "NSBluetoothAlwaysUsageDescription",
            "NSBluetoothPeripheralUsageDescription",
        )
    }

    /**
     * Cleans up resources. Should be called when the repository is no longer needed.
     */
    fun cleanup() {
        bleManager.cleanup()
    }

    companion object {
        private const val TAG = "IosBleRepositoryImpl"
    }
}
