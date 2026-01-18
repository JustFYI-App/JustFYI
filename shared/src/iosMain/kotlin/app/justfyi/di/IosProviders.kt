package app.justfyi.di

import app.justfyi.platform.ClipboardService
import app.justfyi.platform.IosClipboardService
import app.justfyi.platform.IosLocaleService
import app.justfyi.platform.IosNotificationPermissionHandler
import app.justfyi.platform.IosPlatformContext
import app.justfyi.platform.IosShareService
import app.justfyi.platform.IosZipService
import app.justfyi.platform.LocaleService
import app.justfyi.platform.NotificationPermissionHandler
import app.justfyi.platform.PlatformContext
import app.justfyi.platform.ShareService
import app.justfyi.platform.ZipService
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * iOS-specific providers for platform dependencies.
 * This module provides iOS implementations of platform-agnostic interfaces.
 *
 * Providers:
 * - ClipboardService: UIPasteboard wrapper for clipboard operations
 * - PlatformContext: iOS-specific context operations (URL opening, version)
 * - LocaleService: iOS-specific locale operations
 * - ShareService: UIActivityViewController wrapper for file sharing
 * - ZipService: Foundation/posix wrapper for ZIP file creation
 *
 * All providers are scoped to AppScope for application-wide availability.
 */
@ContributesTo(AppScope::class)
interface IosProviders {
    companion object {
        /**
         * Provides ClipboardService using the iOS implementation.
         * This is the platform-agnostic interface for clipboard operations.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideClipboardService(): ClipboardService = IosClipboardService()

        /**
         * Provides PlatformContext using the iOS implementation.
         * This is the platform-agnostic interface for context operations.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun providePlatformContext(): PlatformContext = IosPlatformContext()

        /**
         * Provides LocaleService using the iOS implementation.
         * This is the platform-agnostic interface for locale operations.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideLocaleService(): LocaleService = IosLocaleService()

        /**
         * Provides ShareService using the iOS implementation.
         * This is the platform-agnostic interface for file sharing operations.
         * Uses UIActivityViewController for native share sheet.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideShareService(): ShareService = IosShareService()

        /**
         * Provides ZipService using the iOS implementation.
         * This is the platform-agnostic interface for ZIP file creation.
         * Used by SettingsViewModel for data export functionality.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideZipService(): ZipService = IosZipService()

        /**
         * Provides NotificationPermissionHandler using the iOS implementation.
         * Returns false conservatively to ensure permissions step is shown during recovery.
         * Used by OnboardingViewModel to determine if permissions step should be shown.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideNotificationPermissionHandler(): NotificationPermissionHandler = IosNotificationPermissionHandler()
    }
}
