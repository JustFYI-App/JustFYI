package app.justfyi.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.justfyi.data.local.AndroidDatabaseDriverFactory
import app.justfyi.data.local.AndroidDatabaseKeyProvider
import app.justfyi.data.local.DatabaseDriverFactory
import app.justfyi.data.local.ExposureReportQueries
import app.justfyi.data.local.InteractionQueries
import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.NotificationQueries
import app.justfyi.data.local.SettingsQueries
import app.justfyi.data.local.UserQueries
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing database-related dependencies.
 * All database instances are scoped to DataScope for proper data layer isolation.
 *
 * This module provides:
 * - DatabaseKeyProvider for secure encryption key storage
 * - DatabaseDriverFactory for creating encrypted SQLite drivers (SQLCipher)
 * - JustFyiDatabase as the main database instance
 * - Individual query classes for each table (DAO equivalents)
 *
 * Scope: DataScope
 * - Database and query classes are part of the data layer
 * - Scoped together with Firebase services for consistent data access patterns
 */
@ContributesTo(DataScope::class)
interface DatabaseModule {
    companion object {
        /**
         * Provides the AndroidDatabaseKeyProvider for secure encryption key storage.
         * Uses Android Keystore + EncryptedDataStore.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideDatabaseKeyProvider(context: Context): AndroidDatabaseKeyProvider =
            AndroidDatabaseKeyProvider(context)

        /**
         * Provides the DatabaseDriverFactory for Android.
         * Uses SQLCipher for database encryption.
         * Requires application context and encryption key provider.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideDatabaseDriverFactory(
            context: Context,
            keyProvider: AndroidDatabaseKeyProvider,
        ): DatabaseDriverFactory = AndroidDatabaseDriverFactory(context, keyProvider)

        /**
         * Provides the SqlDriver instance.
         * This is the low-level driver used by SQLDelight.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideSqlDriver(driverFactory: DatabaseDriverFactory): SqlDriver = driverFactory.createDriver()

        /**
         * Provides the JustFyiDatabase instance.
         * This is the main entry point for all database operations.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideJustFyiDatabase(driver: SqlDriver): JustFyiDatabase = JustFyiDatabase(driver)

        /**
         * Provides UserQueries for User table operations.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideUserQueries(database: JustFyiDatabase): UserQueries = database.userQueries

        /**
         * Provides InteractionQueries for Interaction table operations.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideInteractionQueries(database: JustFyiDatabase): InteractionQueries = database.interactionQueries

        /**
         * Provides NotificationQueries for Notification table operations.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideNotificationQueries(database: JustFyiDatabase): NotificationQueries = database.notificationQueries

        /**
         * Provides ExposureReportQueries for ExposureReport table operations.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideExposureReportQueries(database: JustFyiDatabase): ExposureReportQueries =
            database.exposureReportQueries

        /**
         * Provides SettingsQueries for Settings table operations.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideSettingsQueries(database: JustFyiDatabase): SettingsQueries = database.settingsQueries
    }
}
