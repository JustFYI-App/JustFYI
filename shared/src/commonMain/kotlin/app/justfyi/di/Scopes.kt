package app.justfyi.di

/**
 * Scope markers for Metro DI framework.
 *
 * Just FYI uses a three-scope organization for dependency injection:
 * - AppScope: Application-level singletons
 * - DataScope: Database and Firebase services
 * - BleScope: BLE-specific components
 *
 * Each scope follows Metro's abstract class pattern with a private constructor,
 * preventing instantiation while allowing the class to be used as a scope marker
 * in @SingleIn and @ContributesTo annotations.
 */

/**
 * Application-level scope marker.
 *
 * Dependencies scoped to AppScope live for the entire application lifecycle.
 * This scope is the root scope and contains core infrastructure dependencies.
 *
 * Responsibilities:
 * - Coroutine dispatchers (AppCoroutineDispatchers)
 * - Application logger instance
 * - System services (ClipboardManager, etc.)
 * - Other application-wide singletons
 *
 * Usage:
 * ```
 * @SingleIn(AppScope::class)
 * @DependencyGraph(AppScope::class)
 * interface AppGraph { ... }
 *
 * @ContributesTo(AppScope::class)
 * interface SomeModule { ... }
 * ```
 */
abstract class AppScope private constructor()

/**
 * Data layer scope marker.
 *
 * Dependencies scoped to DataScope are responsible for data persistence
 * and remote data services. This scope contains database and Firebase
 * related dependencies.
 *
 * Responsibilities:
 * - SQLDelight database driver and database instance
 * - SQLDelight query classes (UserQueries, InteractionQueries, NotificationQueries,
 *   ExposureReportQueries, SettingsQueries)
 * - Firebase services (Auth, Firestore, Functions, Messaging)
 * - Data repositories that depend on database/Firebase
 *
 * Usage:
 * ```
 * @ContributesTo(DataScope::class)
 * interface DatabaseModule { ... }
 *
 * @SingleIn(DataScope::class)
 * fun provideJustFyiDatabase(driver: SqlDriver): JustFyiDatabase
 * ```
 */
abstract class DataScope private constructor()

/**
 * BLE (Bluetooth Low Energy) scope marker.
 *
 * Dependencies scoped to BleScope are responsible for Bluetooth
 * functionality and proximity detection features.
 *
 * Responsibilities:
 * - BleAdvertiser: Handles BLE advertising to make user discoverable
 * - BleScanner: Scans for and discovers nearby Just FYI users
 * - BleGattServer: Exposes user data via GATT characteristics
 * - BlePermissionHandler: Manages BLE permission checks
 * - BleRepository: Coordinates all BLE operations
 *
 * Usage:
 * ```
 * @ContributesTo(BleScope::class)
 * interface BleModule { ... }
 *
 * @SingleIn(BleScope::class)
 * fun provideBleAdvertiser(context: Context): BleAdvertiser
 * ```
 */
abstract class BleScope private constructor()

/**
 * Activity-level scope marker.
 *
 * Dependencies scoped to ActivityScope live for the duration of an Activity.
 * This scope is retained for backward compatibility but may be deprecated
 * in future refactoring as ViewModels transition to using the three-scope
 * hierarchy (AppScope, DataScope, BleScope) with Metro DI.
 *
 * @deprecated Consider using AppScope, DataScope, or BleScope instead
 * for clearer dependency organization.
 */
abstract class ActivityScope private constructor()
