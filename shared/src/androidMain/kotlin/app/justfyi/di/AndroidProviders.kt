package app.justfyi.di

import android.content.ClipboardManager
import android.content.Context
import app.justfyi.platform.AndroidClipboardService
import app.justfyi.platform.AndroidLocaleService
import app.justfyi.platform.AndroidNotificationPermissionHandler
import app.justfyi.platform.AndroidPlatformContext
import app.justfyi.platform.AndroidShareService
import app.justfyi.platform.AndroidZipService
import app.justfyi.platform.ClipboardService
import app.justfyi.platform.LocaleService
import app.justfyi.platform.NotificationPermissionHandler
import app.justfyi.platform.PlatformContext
import app.justfyi.platform.ShareService
import app.justfyi.platform.ZipService
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Android-specific providers for platform dependencies.
 * This module provides Android Context and other platform-specific instances.
 *
 * Providers:
 * - Context: Application context for Android-specific dependencies
 * - ClipboardManager: System service for clipboard operations
 * - ClipboardService: Platform-agnostic clipboard interface
 * - PlatformContext: Platform-agnostic context operations
 * - LocaleService: Platform-agnostic locale operations
 * - ShareService: Platform-agnostic file sharing interface
 * - ZipService: Platform-agnostic ZIP file creation interface
 */
@ContributesTo(AppScope::class)
interface AndroidProviders {
    companion object {
        /**
         * Reference to the application context.
         * Set during Application.onCreate() before the graph is created.
         */
        private var applicationContext: Context? = null

        /**
         * Initializes the Android providers with the application context.
         * Must be called in Application.onCreate() before any DI operations.
         */
        fun initialize(context: Context) {
            applicationContext = context.applicationContext
        }

        /**
         * Provides the application Context for Android-specific dependencies.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideContext(): Context =
            applicationContext
                ?: throw IllegalStateException(
                    "AndroidProviders.initialize() must be called in Application.onCreate()",
                )

        /**
         * Provides ClipboardManager system service.
         * Used by ProfileViewModel and OnboardingViewModel for ID copying.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideClipboardManager(context: Context): ClipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        /**
         * Provides ClipboardService using the Android implementation.
         * This is the platform-agnostic interface for clipboard operations.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideClipboardService(clipboardManager: ClipboardManager): ClipboardService =
            AndroidClipboardService(clipboardManager)

        /**
         * Provides PlatformContext using the Android implementation.
         * This is the platform-agnostic interface for context operations.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun providePlatformContext(context: Context): PlatformContext = AndroidPlatformContext(context)

        /**
         * Provides LocaleService using the Android implementation.
         * This is the platform-agnostic interface for locale operations.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideLocaleService(): LocaleService = AndroidLocaleService()

        /**
         * Provides ShareService using the Android implementation.
         * This is the platform-agnostic interface for file sharing operations.
         * Uses FileProvider for secure file URI generation and Intent.ACTION_SEND.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideShareService(context: Context): ShareService = AndroidShareService(context)

        /**
         * Provides ZipService using the Android implementation.
         * This is the platform-agnostic interface for ZIP file creation.
         * Used by SettingsViewModel for data export functionality.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideZipService(context: Context): ZipService = AndroidZipService(context)

        /**
         * Provides NotificationPermissionHandler using the Android implementation.
         * Checks POST_NOTIFICATIONS permission for Android 13+.
         * Used by OnboardingViewModel to determine if permissions step should be shown during recovery.
         */
        @Provides
        @SingleIn(AppScope::class)
        fun provideNotificationPermissionHandler(context: Context): NotificationPermissionHandler =
            AndroidNotificationPermissionHandler(context)
    }
}
