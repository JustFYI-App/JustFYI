package app.justfyi.platform

import app.justfyi.domain.repository.BleRepository
import app.justfyi.util.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.darwin.NSObject

/**
 * Singleton holder for IosLifecycleManager to work around
 * Kotlin/Native restrictions on companion objects in NSObject subclasses.
 */
object IosLifecycleManagerHolder {
    private var instance: IosLifecycleManager? = null

    /**
     * Initializes the lifecycle manager with the BLE repository.
     * Should be called once during app initialization.
     */
    fun initialize(bleRepository: BleRepository): IosLifecycleManager =
        instance ?: IosLifecycleManager(bleRepository).also {
            instance = it
            it.registerForLifecycleEvents()
        }

    /**
     * Gets the singleton instance.
     * Throws if not initialized.
     */
    fun getInstance(): IosLifecycleManager =
        instance ?: throw IllegalStateException(
            "IosLifecycleManager not initialized. Call initialize() first.",
        )

    /**
     * Returns whether the manager has been initialized.
     */
    fun isInitialized(): Boolean = instance != null

    /**
     * Clears the singleton instance. Used for testing.
     */
    fun reset() {
        instance?.cleanup()
        instance = null
    }
}

/**
 * iOS app lifecycle manager.
 *
 * Manages BLE start/stop based on app foreground/background state.
 * Registers for UIApplication lifecycle notifications to properly
 * handle BLE discovery when the app transitions between states.
 *
 * MVP behavior (foreground-only BLE):
 * - Starts BLE discovery when app enters foreground
 * - Stops BLE discovery when app enters background
 * - Does NOT use background modes for BLE (per spec)
 *
 * Usage:
 * 1. Create IosLifecycleManager with BleRepository dependency via IosLifecycleManagerHolder.initialize()
 * 2. The manager automatically registers for lifecycle events
 * 3. Call cleanup() when app terminates
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosLifecycleManager(
    private val bleRepository: BleRepository,
) : NSObject() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isRegistered = false
    private var isInForeground = true

    /**
     * Registers for iOS application lifecycle notifications.
     * Should be called once during app initialization.
     */
    fun registerForLifecycleEvents() {
        if (isRegistered) {
            Logger.d(TAG, "Already registered for lifecycle events")
            return
        }

        val notificationCenter = NSNotificationCenter.defaultCenter

        // Register for foreground notification
        notificationCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString("handleWillEnterForeground"),
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
        )

        // Register for active notification
        notificationCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString("handleDidBecomeActive"),
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
        )

        // Register for resign active notification
        notificationCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString("handleWillResignActive"),
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
        )

        // Register for background notification
        notificationCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString("handleDidEnterBackground"),
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
        )

        isRegistered = true
        Logger.d(TAG, "Registered for lifecycle events")
    }

    /**
     * Called when the app is about to enter the foreground.
     */
    @ObjCAction
    fun handleWillEnterForeground() {
        Logger.d(TAG, "App will enter foreground")
        // Prepare for foreground - actual start happens in didBecomeActive
    }

    /**
     * Called when the app becomes active (fully in foreground).
     */
    @ObjCAction
    fun handleDidBecomeActive() {
        Logger.d(TAG, "App did become active")
        isInForeground = true
        onEnterForeground()
    }

    /**
     * Called when the app is about to resign active state.
     */
    @ObjCAction
    fun handleWillResignActive() {
        Logger.d(TAG, "App will resign active")
        // Prepare for background - actual stop happens in didEnterBackground
    }

    /**
     * Called when the app enters the background.
     */
    @ObjCAction
    fun handleDidEnterBackground() {
        Logger.d(TAG, "App did enter background")
        isInForeground = false
        onEnterBackground()
    }

    /**
     * Handles app entering foreground.
     * Starts BLE discovery if conditions are met.
     */
    fun onEnterForeground() {
        Logger.d(TAG, "Handling foreground entry")
        scope.launch {
            try {
                // Check if BLE should be started
                if (bleRepository.isBleSupported() && bleRepository.hasRequiredPermissions()) {
                    Logger.d(TAG, "Starting BLE discovery on foreground")
                    val result = bleRepository.startDiscovery()
                    if (result.isSuccess) {
                        Logger.d(TAG, "BLE discovery started successfully")
                    } else {
                        Logger.e(TAG, "Failed to start BLE discovery: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Logger.d(TAG, "BLE not supported or permissions not granted")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error starting BLE discovery", e)
            }
        }
    }

    /**
     * Handles app entering background.
     * Stops BLE discovery for MVP (foreground-only).
     */
    fun onEnterBackground() {
        Logger.d(TAG, "Handling background entry")
        scope.launch {
            try {
                Logger.d(TAG, "Stopping BLE discovery on background")
                bleRepository.stopDiscovery()
                Logger.d(TAG, "BLE discovery stopped")
            } catch (e: Exception) {
                Logger.e(TAG, "Error stopping BLE discovery", e)
            }
        }
    }

    /**
     * Unregisters from lifecycle events and cleans up resources.
     * Should be called when the app terminates.
     */
    fun cleanup() {
        Logger.d(TAG, "Cleaning up lifecycle manager")

        if (isRegistered) {
            NSNotificationCenter.defaultCenter.removeObserver(this)
            isRegistered = false
        }

        // Stop any ongoing BLE operations
        scope.launch {
            try {
                bleRepository.stopDiscovery()
            } catch (e: Exception) {
                Logger.e(TAG, "Error during cleanup", e)
            }
        }
    }

    /**
     * Returns whether the app is currently in the foreground.
     */
    fun isAppInForeground(): Boolean = isInForeground
}

private const val TAG = "IosLifecycleManager"
