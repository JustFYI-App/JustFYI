package app.justfyi.di

import app.cash.sqldelight.db.SqlDriver
import app.justfyi.data.local.DatabaseDriverFactory
import app.justfyi.data.local.DatabaseKeyProvider
import app.justfyi.data.local.ExposureReportQueries
import app.justfyi.data.local.InteractionQueries
import app.justfyi.data.local.IosDatabaseDriverFactory
import app.justfyi.data.local.IosDatabaseKeyProvider
import app.justfyi.data.local.JustFyiDatabase
import app.justfyi.data.local.NotificationQueries
import app.justfyi.data.local.SettingsQueries
import app.justfyi.data.local.UserQueries
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Metro DI module providing database-related dependencies for iOS.
 * All database instances are scoped to DataScope for proper data layer isolation.
 *
 * This module provides:
 * - DatabaseKeyProvider for secure encryption key storage (iOS Keychain)
 * - DatabaseDriverFactory using encrypted NativeSqliteDriver for iOS
 * - JustFyiDatabase as the main database instance
 * - Individual query classes for each table (DAO equivalents)
 *
 * Scope: DataScope
 * - Database and query classes are part of the data layer
 * - Scoped together with Firebase services for consistent data access patterns
 */
@ContributesTo(DataScope::class)
interface IosDatabaseModule {
    companion object {
        /**
         * Provides the DatabaseKeyProvider for secure encryption key storage.
         * Uses iOS Keychain for secure storage.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideDatabaseKeyProvider(): DatabaseKeyProvider = IosDatabaseKeyProvider()

        /**
         * Provides the DatabaseDriverFactory for iOS.
         * Uses encrypted NativeSqliteDriver with SQLCipher.
         */
        @Provides
        @SingleIn(DataScope::class)
        fun provideDatabaseDriverFactory(keyProvider: DatabaseKeyProvider): DatabaseDriverFactory =
            IosDatabaseDriverFactory(keyProvider)

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
