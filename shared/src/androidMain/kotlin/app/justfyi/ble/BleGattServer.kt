package app.justfyi.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import app.justfyi.util.Constants
import app.justfyi.util.HashUtils
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the BLE GATT server for the Just FYI app.
 * Exposes the user's hashed anonymous ID and username as readable characteristics
 * so other Just FYI devices can identify this user.
 *
 * The GATT server provides:
 * - Just FYI Service (custom UUID from Constants)
 * - User ID Hash Characteristic (read-only)
 * - Username Characteristic (read-only)
 *
 * Connected clients can read these characteristics to get the user's identity.
 *
 * DOMAIN-SEPARATED HASHING:
 * The anonymous ID is hashed using HashUtils.hashForInteraction() which uses
 * no salt prefix (SHA256(uid.uppercase())). This ensures the hash matches:
 * - The ownerId stored in interactions collection
 * - The partnerAnonymousId used for chain traversal queries
 *
 * All hashing is now consolidated in the common HashUtils module.
 */
@Inject
class BleGattServer(
    private val context: Context,
) {
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private var gattServer: BluetoothGattServer? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Current user data to expose via characteristics
    private var currentUserIdHash: String = ""
    private var currentUsername: String = ""

    // Track connected devices
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()

    private val gattCallback =
        object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int,
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Client connected: ${device.address}")
                        connectedDevices[device.address] = device
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Client disconnected: ${device.address}")
                        connectedDevices.remove(device.address)
                    }
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic,
            ) {
                Log.d(TAG, "Read request for characteristic: ${characteristic.uuid}")

                val response: ByteArray =
                    when (characteristic.uuid.toString().lowercase()) {
                        Constants.Ble.USER_ID_CHARACTERISTIC_UUID.lowercase() -> {
                            Log.d(TAG, "Responding with user ID hash")
                            currentUserIdHash.toByteArray(Charsets.UTF_8)
                        }
                        Constants.Ble.USERNAME_CHARACTERISTIC_UUID.lowercase() -> {
                            Log.d(TAG, "Responding with username: $currentUsername")
                            currentUsername.toByteArray(Charsets.UTF_8)
                        }
                        else -> {
                            Log.w(TAG, "Unknown characteristic requested: ${characteristic.uuid}")
                            ByteArray(0)
                        }
                    }

                // Handle offset for large values (pagination)
                val responseSlice =
                    if (offset < response.size) {
                        response.sliceArray(offset until response.size)
                    } else {
                        ByteArray(0)
                    }

                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    responseSlice,
                )
            }

            override fun onServiceAdded(
                status: Int,
                service: BluetoothGattService,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Service added successfully: ${service.uuid}")
                } else {
                    Log.e(TAG, "Failed to add service: $status")
                }
            }
        }

    /**
     * Starts the GATT server with the user's data.
     *
     * @param anonymousId The user's Firebase anonymous ID (will be hashed for privacy)
     * @param username The user's public username
     * @return Result indicating success or failure
     */
    fun start(
        anonymousId: String,
        username: String,
    ): Result<Unit> {
        if (_isRunning.value) {
            // Update user data and return success
            updateUserData(anonymousId, username)
            return Result.success(Unit)
        }

        val manager = bluetoothManager
        if (manager == null) {
            Log.e(TAG, "BluetoothManager not available")
            return Result.failure(IllegalStateException("BluetoothManager not available"))
        }

        if (manager.adapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            return Result.failure(IllegalStateException("Bluetooth is not enabled"))
        }

        try {
            // Store user data
            updateUserData(anonymousId, username)

            // Open GATT server
            gattServer = manager.openGattServer(context, gattCallback)
            if (gattServer == null) {
                Log.e(TAG, "Failed to open GATT server")
                return Result.failure(IllegalStateException("Failed to open GATT server"))
            }

            // Create and add the Just FYI service
            val service = createJustFyiService()
            val added = gattServer?.addService(service) ?: false

            if (!added) {
                Log.e(TAG, "Failed to add Just FYI service")
                gattServer?.close()
                gattServer = null
                return Result.failure(IllegalStateException("Failed to add Just FYI service"))
            }

            _isRunning.value = true
            Log.d(TAG, "GATT server started successfully")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GATT server", e)
            gattServer?.close()
            gattServer = null
            return Result.failure(e)
        }
    }

    /**
     * Stops the GATT server and cleans up resources.
     */
    fun stop() {
        if (!_isRunning.value) {
            Log.d(TAG, "GATT server not running")
            return
        }

        try {
            gattServer?.close()
            Log.d(TAG, "GATT server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GATT server", e)
        } finally {
            gattServer = null
            connectedDevices.clear()
            _isRunning.value = false
        }
    }

    /**
     * Updates the user data exposed by the GATT server.
     * Can be called while the server is running to update the username.
     *
     * Uses HashUtils.hashForInteraction() to hash the anonymous ID, ensuring
     * consistency with the backend and other parts of the app.
     *
     * @param anonymousId The user's Firebase anonymous ID (will be hashed)
     * @param username The user's public username
     */
    fun updateUserData(
        anonymousId: String,
        username: String,
    ) {
        // Use common HashUtils for consistent hashing across the app
        // hashForInteraction uses no salt prefix: SHA256(uid.uppercase())
        currentUserIdHash = HashUtils.hashForInteraction(anonymousId)
        currentUsername = username
        Log.d(TAG, "Updated user data - hash: ${currentUserIdHash.take(8)}..., username: $username")
    }

    /**
     * Gets the number of currently connected devices.
     */
    fun getConnectedDeviceCount(): Int = connectedDevices.size

    /**
     * Creates the Just FYI BLE service with characteristics.
     */
    private fun createJustFyiService(): BluetoothGattService {
        val service =
            BluetoothGattService(
                UUID.fromString(Constants.Ble.SERVICE_UUID),
                BluetoothGattService.SERVICE_TYPE_PRIMARY,
            )

        // User ID Hash characteristic (read-only)
        val userIdCharacteristic =
            BluetoothGattCharacteristic(
                UUID.fromString(Constants.Ble.USER_ID_CHARACTERISTIC_UUID),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )
        service.addCharacteristic(userIdCharacteristic)

        // Username characteristic (read-only)
        val usernameCharacteristic =
            BluetoothGattCharacteristic(
                UUID.fromString(Constants.Ble.USERNAME_CHARACTERISTIC_UUID),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )
        service.addCharacteristic(usernameCharacteristic)

        return service
    }

    companion object {
        private const val TAG = "BleGattServer"
    }
}
