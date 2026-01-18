package app.justfyi.di

import app.justfyi.ble.IosBleManager
import app.justfyi.ble.IosBleRepositoryImpl
import app.justfyi.domain.repository.BleRepository
import app.justfyi.domain.repository.UserRepository
import app.justfyi.platform.BlePermissionHandler
import app.justfyi.platform.IosBlePermissionHandler
import app.justfyi.util.AppCoroutineDispatchers
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing BLE-related dependencies for iOS.
 * All BLE components are scoped to BleScope for proper isolation of Bluetooth functionality.
 *
 * This module provides:
 * - IosBleManager: Coordinates Core Bluetooth operations (scanning, advertising, GATT)
 * - BlePermissionHandler: Checks and manages BLE permissions via Core Bluetooth authorization
 * - BleRepository: Coordinates all BLE operations (uses IosBleRepositoryImpl)
 *
 * On iOS, Core Bluetooth handles BLE operations differently than Android:
 * - CBCentralManager for scanning
 * - CBPeripheralManager for advertising
 * - Authorization is handled implicitly by the Core Bluetooth framework
 *
 * Scope: BleScope
 * - BLE components are isolated from the data layer for clear separation of concerns
 * - Allows independent lifecycle management for Bluetooth operations
 * - Simplifies permission handling and BLE state management
 */
@ContributesTo(BleScope::class)
interface IosBleModule {
    companion object {
        /**
         * Provides IosBleManager instance.
         * Coordinates all Core Bluetooth operations for scanning and advertising.
         */
        @Provides
        @SingleIn(BleScope::class)
        fun provideIosBleManager(): IosBleManager = IosBleManager()

        /**
         * Provides BlePermissionHandler for iOS.
         * Uses Core Bluetooth authorization APIs for permission checking.
         */
        @Provides
        @SingleIn(BleScope::class)
        fun provideBlePermissionHandler(): BlePermissionHandler = IosBlePermissionHandler()

        /**
         * Provides BleRepository implementation for iOS.
         * Coordinates advertising, scanning, and GATT operations via IosBleManager.
         *
         * Note: BleRepository depends on UserRepository from DataScope.
         * Metro's multi-scope support allows cross-scope dependency resolution.
         */
        @Provides
        @SingleIn(BleScope::class)
        fun provideBleRepository(
            bleManager: IosBleManager,
            userRepository: UserRepository,
            dispatchers: AppCoroutineDispatchers,
        ): BleRepository =
            IosBleRepositoryImpl(
                bleManager = bleManager,
                userRepository = userRepository,
                dispatchers = dispatchers,
            )
    }
}
