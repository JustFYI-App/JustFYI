package app.justfyi.ble

import app.justfyi.util.Constants
import app.justfyi.util.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBATTErrorAttributeNotFound
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerStatePoweredOn
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMakeRange
import platform.Foundation.create
import platform.Foundation.subdataWithRange

/**
 * iOS implementation of GATT characteristic handling using CBPeripheralManager.
 *
 * This class manages:
 * - Creating and publishing the Just FYI GATT service
 * - Exposing user data (hashed ID, username) as readable characteristics
 * - Responding to read requests from connected centrals (other Just FYI devices)
 *
 * The GATT service structure matches the Android implementation:
 * - Service UUID: Constants.Ble.SERVICE_UUID
 * - User ID Hash Characteristic (read-only)
 * - Username Characteristic (read-only)
 *
 * This enables cross-platform discovery between Android and iOS devices.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosBleGattHandler {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var peripheralManager: CBPeripheralManager? = null

    // Current user data to expose via characteristics
    private var currentUserIdHash: String = ""
    private var currentUsername: String = ""

    // Service and characteristics
    private var justFyiService: CBMutableService? = null

    /**
     * Just FYI service UUID.
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

    /**
     * Initializes the GATT handler with a peripheral manager.
     * Must be called before start().
     *
     * @param manager The CBPeripheralManager to use for GATT service
     */
    fun initialize(manager: CBPeripheralManager) {
        this.peripheralManager = manager
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

        val manager = peripheralManager
        if (manager == null) {
            Logger.e(TAG, "CBPeripheralManager not initialized")
            return Result.failure(IllegalStateException("CBPeripheralManager not initialized"))
        }

        if (manager.state != CBPeripheralManagerStatePoweredOn) {
            Logger.e(TAG, "Bluetooth is not enabled")
            return Result.failure(IllegalStateException("Bluetooth is not enabled"))
        }

        try {
            // Store user data
            updateUserData(anonymousId, username)

            // Create the Just FYI service
            justFyiService = createJustFyiService()

            // Add service to peripheral manager
            manager.addService(justFyiService!!)

            _isRunning.value = true
            Logger.d(TAG, "GATT handler started successfully")
            return Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start GATT handler: ${e.message}")
            return Result.failure(e)
        }
    }

    /**
     * Stops the GATT handler and removes the service.
     */
    fun stop() {
        if (!_isRunning.value) {
            Logger.d(TAG, "GATT handler not running")
            return
        }

        try {
            justFyiService?.let { service ->
                peripheralManager?.removeService(service)
            }
            Logger.d(TAG, "GATT handler stopped")
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping GATT handler: ${e.message}")
        } finally {
            justFyiService = null
            _isRunning.value = false
        }
    }

    /**
     * Updates the user data exposed by the GATT characteristics.
     *
     * @param anonymousId The user's Firebase anonymous ID (will be hashed)
     * @param username The user's public username
     */
    fun updateUserData(
        anonymousId: String,
        username: String,
    ) {
        currentUserIdHash = hashAnonymousId(anonymousId)
        currentUsername = username
        Logger.d(TAG, "Updated user data - hash: ${currentUserIdHash.take(8)}..., username: $username")
    }

    /**
     * Cleans up resources.
     */
    fun cleanup() {
        stop()
        peripheralManager = null
    }

    /**
     * Handles a read request from a connected central.
     * This is called by the peripheral manager delegate.
     */
    fun handleReadRequest(request: CBATTRequest): Boolean {
        val characteristicUuid = request.characteristic.UUID.UUIDString

        Logger.d(TAG, "Read request for characteristic: $characteristicUuid")

        val responseData: NSData? =
            when {
                characteristicUuid.equals(userIdCharacteristicUuid.UUIDString, ignoreCase = true) -> {
                    Logger.d(TAG, "Responding with user ID hash")
                    stringToNSData(currentUserIdHash)
                }
                characteristicUuid.equals(usernameCharacteristicUuid.UUIDString, ignoreCase = true) -> {
                    Logger.d(TAG, "Responding with username: $currentUsername")
                    stringToNSData(currentUsername)
                }
                else -> {
                    Logger.w(TAG, "Unknown characteristic requested: $characteristicUuid")
                    null
                }
            }

        if (responseData == null) {
            peripheralManager?.respondToRequest(request, withResult = CBATTErrorAttributeNotFound)
            return false
        }

        // Handle offset for large values
        val offset = request.offset.toInt()
        val dataLength = responseData.length.toInt()

        val responseValue: NSData =
            if (offset >= dataLength) {
                stringToNSData("")
            } else {
                // Use subdataWithRange with proper ULong conversion
                val length = (dataLength - offset).toULong()
                responseData.subdataWithRange(NSMakeRange(offset.toULong(), length))
            }

        request.setValue(responseValue)
        peripheralManager?.respondToRequest(request, withResult = CBATTErrorSuccess)
        return true
    }

    /**
     * Handles service added callback.
     */
    fun handleServiceAdded(
        service: CBService,
        error: NSError?,
    ) {
        if (error != null) {
            Logger.e(TAG, "Failed to add service: ${error.localizedDescription}")
        } else {
            Logger.d(TAG, "Service added successfully: ${service.UUID.UUIDString}")
        }
    }

    /**
     * Creates the Just FYI BLE service with characteristics.
     */
    private fun createJustFyiService(): CBMutableService {
        val service = CBMutableService(justFyiServiceUuid, true)

        // Create user ID hash characteristic (read-only)
        val userIdCharacteristic =
            CBMutableCharacteristic(
                type = userIdCharacteristicUuid,
                properties = CBCharacteristicPropertyRead,
                value = null, // Dynamic value, handled in read request
                permissions = CBAttributePermissionsReadable,
            )

        // Create username characteristic (read-only)
        val usernameCharacteristic =
            CBMutableCharacteristic(
                type = usernameCharacteristicUuid,
                properties = CBCharacteristicPropertyRead,
                value = null, // Dynamic value, handled in read request
                permissions = CBAttributePermissionsReadable,
            )

        // Add characteristics to service
        service.setCharacteristics(listOf(userIdCharacteristic, usernameCharacteristic))

        return service
    }

    /**
     * Converts a String to NSData.
     */
    private fun stringToNSData(string: String): NSData {
        val bytes = string.encodeToByteArray()
        if (bytes.isEmpty()) {
            return NSData()
        }
        return bytes.usePinned { pinned ->
            NSData.create(
                bytes = pinned.addressOf(0),
                length = bytes.size.toULong(),
            )
        }
    }

    /**
     * Hashes the anonymous ID for privacy using SHA-256.
     * Uses CommonCrypto's CC_SHA256 to match Android's implementation.
     *
     * IMPORTANT: The ID is uppercased before hashing to ensure consistency
     * across the app and backend. All IDs are stored in uppercase.
     */
    private fun hashAnonymousId(anonymousId: String): String {
        // Uppercase before hashing to ensure consistency with Android
        val uppercasedId = anonymousId.uppercase()

        return try {
            val inputBytes = uppercasedId.encodeToByteArray()

            memScoped {
                val digest = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)

                inputBytes.usePinned { pinned ->
                    CC_SHA256(
                        pinned.addressOf(0),
                        inputBytes.size.toUInt(),
                        digest.reinterpret(),
                    )
                }

                // Convert digest bytes to hex string
                buildString {
                    for (i in 0 until CC_SHA256_DIGEST_LENGTH) {
                        val byte = digest[i].toInt() and 0xFF
                        append(HEX_CHARS[byte shr 4])
                        append(HEX_CHARS[byte and 0x0F])
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to hash anonymous ID with SHA-256: ${e.message}")
            // Fallback: use first 16 chars of uppercased ID (matches Android fallback)
            uppercasedId.take(16).padEnd(64, '0')
        }
    }

    companion object {
        private const val TAG = "IosBleGattHandler"
        private val HEX_CHARS =
            charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    }
}
