package app.justfyi.di

import android.content.Context
import app.justfyi.ble.BleAdvertiser
import app.justfyi.ble.BleGattServer
import app.justfyi.ble.BleRepositoryImpl
import app.justfyi.ble.BleScanner
import app.justfyi.domain.repository.BleRepository
import app.justfyi.domain.repository.UserRepository
import app.justfyi.platform.BlePermissionHandler
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import app.justfyi.ble.BlePermissionHandler as AndroidBlePermissionHandler

/**
 * Metro DI module providing BLE-related dependencies.
 * All BLE components are scoped to BleScope for proper isolation of Bluetooth functionality.
 *
 * This module provides:
 * - BleAdvertiser: Handles BLE advertising
 * - BleScanner: Handles BLE scanning and device discovery
 * - BleGattServer: Exposes user data via GATT characteristics
 * - BlePermissionHandler: Checks and manages BLE permissions (both Android-specific and common interface)
 * - BleRepository: Coordinates all BLE operations
 *
 * Scope: BleScope
 * - BLE components are isolated from the data layer for clear separation of concerns
 * - Allows independent lifecycle management for Bluetooth operations
 * - Simplifies permission handling and BLE state management
 */
@ContributesTo(BleScope::class)
interface BleModule {
    companion object {
        /**
         * Provides BleAdvertiser instance.
         * Manages BLE advertising to make the user discoverable.
         */
        @Provides
        @SingleIn(BleScope::class)
        fun provideBleAdvertiser(context: Context): BleAdvertiser = BleAdvertiser(context)

        /**
         * Provides BleScanner instance.
         * Scans for and discovers nearby Just FYI users.
         */
        @Provides
        @SingleIn(BleScope::class)
        fun provideBleScanner(context: Context): BleScanner = BleScanner(context)

        /**
         * Provides BleGattServer instance.
         * Exposes user data (hashed ID, username) to connected devices.
         */
        @Provides
        @SingleIn(BleScope::class)
        fun provideBleGattServer(context: Context): BleGattServer = BleGattServer(context)

        /**
         * Provides Android-specific BlePermissionHandler instance.
         * Handles BLE permission checks for different Android versions.
         */
        @Provides
        @SingleIn(BleScope::class)
        fun provideAndroidBlePermissionHandler(context: Context): AndroidBlePermissionHandler =
            AndroidBlePermissionHandler(context)

        /**
         * Provides the common BlePermissionHandler interface.
         * Used by ViewModels in commonMain that need permission handling.
         * Wraps the Android-specific implementation.
         */
        @Provides
        @SingleIn(BleScope::class)
        fun provideBlePermissionHandler(androidHandler: AndroidBlePermissionHandler): BlePermissionHandler =
            object : BlePermissionHandler {
                override fun getRequiredPermissions(): List<String> = androidHandler.getRequiredPermissions()

                override fun hasAllPermissions(): Boolean = androidHandler.hasAllPermissions()

                override fun getPermissionRationale(): String = androidHandler.getPermissionRationale()
            }

        /**
         * Provides BleRepository implementation.
         * Coordinates advertising, scanning, and GATT server operations.
         *
         * Note: BleRepository depends on UserRepository from DataScope.
         * Metro's multi-scope support allows cross-scope dependency resolution.
         */
        @Provides
        @SingleIn(BleScope::class)
        fun provideBleRepository(
            context: Context,
            bleAdvertiser: BleAdvertiser,
            bleScanner: BleScanner,
            bleGattServer: BleGattServer,
            permissionHandler: AndroidBlePermissionHandler,
            userRepository: UserRepository,
            dispatchers: AppCoroutineDispatchers,
        ): BleRepository =
            BleRepositoryImpl(
                context = context,
                bleAdvertiser = bleAdvertiser,
                bleScanner = bleScanner,
                bleGattServer = bleGattServer,
                permissionHandler = permissionHandler,
                userRepository = userRepository,
                dispatchers = dispatchers,
            )
    }
}
