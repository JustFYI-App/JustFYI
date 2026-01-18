package app.justfyi.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import app.justfyi.util.Constants
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manages BLE advertising for the Just FYI app.
 * Broadcasts the user's hashed anonymous ID and username so other
 * Just FYI users can discover them.
 *
 * Advertising starts automatically when the app opens and stops when
 * it closes (no background advertising for MVP).
 *
 * The advertiser broadcasts:
 * - Just FYI Service UUID for discovery filtering
 * - Device name (not user data - that's in GATT characteristics)
 */
@Inject
class BleAdvertiser(
    private val context: Context,
) {
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private val advertiser: BluetoothLeAdvertiser? by lazy {
        bluetoothAdapter?.bluetoothLeAdvertiser
    }

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private var advertiseCallback: AdvertiseCallback? = null

    /**
     * Starts BLE advertising with the Just FYI service UUID.
     * The actual user data (hashed ID, username) is exposed via GATT characteristics
     * in the BleGattServer.
     *
     * @return Result indicating success or failure
     */
    fun startAdvertising(): Result<Unit> {
        if (_isAdvertising.value) {
            Log.d(TAG, "Already advertising")
            return Result.success(Unit)
        }

        val bleAdvertiser = advertiser
        if (bleAdvertiser == null) {
            Log.e(TAG, "BLE advertiser not available")
            return Result.failure(IllegalStateException("BLE advertiser not available"))
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            return Result.failure(IllegalStateException("Bluetooth is not enabled"))
        }

        val settings = buildAdvertiseSettings()
        val data = buildAdvertiseData()

        advertiseCallback =
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "Advertising started successfully")
                    _isAdvertising.value = true
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "Advertising failed with error code: $errorCode (${getErrorCodeName(errorCode)})")
                    _isAdvertising.value = false
                }
            }

        try {
            bleAdvertiser.startAdvertising(settings, data, advertiseCallback)
            Log.d(TAG, "Started advertising for service: ${Constants.Ble.SERVICE_UUID}")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising", e)
            return Result.failure(e)
        }
    }

    /**
     * Stops BLE advertising.
     * Should be called when the app goes to background.
     */
    fun stopAdvertising() {
        if (!_isAdvertising.value) {
            Log.d(TAG, "Not currently advertising")
            return
        }

        try {
            advertiseCallback?.let { callback ->
                advertiser?.stopAdvertising(callback)
                Log.d(TAG, "Stopped advertising")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        } finally {
            advertiseCallback = null
            _isAdvertising.value = false
        }
    }

    /**
     * Checks if the device supports BLE advertising.
     * Some devices support BLE scanning but not advertising.
     */
    fun isAdvertisingSupported(): Boolean = bluetoothAdapter?.isMultipleAdvertisementSupported == true

    /**
     * Builds the advertising settings.
     * Uses balanced mode for good range without excessive battery drain.
     */
    private fun buildAdvertiseSettings(): AdvertiseSettings =
        AdvertiseSettings
            .Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true) // Allow GATT connections for reading user data
            .setTimeout(0) // Don't timeout - we manage lifecycle manually
            .build()

    /**
     * Builds the advertising data.
     * Contains only the service UUID - actual user data is in GATT characteristics.
     */
    private fun buildAdvertiseData(): AdvertiseData {
        val serviceUuid = ParcelUuid(UUID.fromString(Constants.Ble.SERVICE_UUID))

        return AdvertiseData
            .Builder()
            .setIncludeDeviceName(false) // Don't expose device name
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(serviceUuid)
            .build()
    }

    /**
     * Gets a human-readable name for advertise error codes.
     */
    private fun getErrorCodeName(errorCode: Int): String =
        when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
            else -> "UNKNOWN"
        }

    companion object {
        private const val TAG = "BleAdvertiser"
    }
}
