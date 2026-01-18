package app.justfyi.ble

/**
 * Smooths BLE RSSI (signal strength) values using Exponential Moving Average (EMA).
 *
 * BLE RSSI values are notoriously noisy due to multipath interference, reflections,
 * device orientation, and radio characteristics. This class applies EMA filtering
 * to produce stable signal strength readings.
 *
 * Formula: smoothedRSSI = α * newRSSI + (1-α) * previousSmoothedRSSI
 *
 * @param alpha Smoothing factor (0.0-1.0). Lower = smoother but slower response.
 *              Default 0.2 provides good balance for typical BLE use cases.
 */
class RssiSmoother(
    private val alpha: Float = DEFAULT_ALPHA,
) {
    private val deviceRssiMap = mutableMapOf<String, Float>()

    /**
     * Smooths an RSSI reading for a specific device.
     *
     * @param deviceId Unique identifier for the device (e.g., MAC address or user hash)
     * @param rssi The raw RSSI value from the BLE scan
     * @return The smoothed RSSI value
     */
    fun smooth(
        deviceId: String,
        rssi: Int,
    ): Int {
        val previousSmoothed = deviceRssiMap[deviceId]

        val smoothed =
            if (previousSmoothed == null) {
                // First reading for this device - use raw value
                rssi.toFloat()
            } else {
                // Apply EMA: smoothed = α * new + (1-α) * previous
                alpha * rssi + (1 - alpha) * previousSmoothed
            }

        deviceRssiMap[deviceId] = smoothed
        return smoothed.toInt()
    }

    /**
     * Removes a device from tracking (e.g., when it goes out of range).
     *
     * @param deviceId Unique identifier for the device
     */
    fun removeDevice(deviceId: String) {
        deviceRssiMap.remove(deviceId)
    }

    /**
     * Clears all tracked devices.
     */
    fun clear() {
        deviceRssiMap.clear()
    }

    companion object {
        /**
         * Default smoothing factor.
         * 0.2 provides good noise reduction while still responding to real signal changes.
         * - Lower values (0.1) = smoother but slower to respond
         * - Higher values (0.3-0.4) = faster response but more jittery
         */
        const val DEFAULT_ALPHA = 0.2f
    }
}
