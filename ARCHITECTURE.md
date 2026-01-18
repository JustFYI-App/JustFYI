# Architecture Overview

This document describes the architecture of the Just FYI Kotlin Multiplatform application.

## Module Structure

```
justfyi/
├── shared/                 # KMP shared module (core logic)
├── androidApp/             # Android application entry point
├── iosApp/                 # iOS application (Xcode project)
└── baselineprofile/        # Android performance profiling
```

### shared/

The heart of the application. Contains all business logic, data models, and UI components shared between platforms.

```
shared/src/
├── commonMain/             # Platform-agnostic code
│   └── kotlin/app/justfyi/
│       ├── data/           # Repositories, data sources
│       ├── domain/         # Use cases, models, interfaces
│       ├── presentation/   # ViewModels, UI components, screens
│       ├── di/             # Dependency injection setup
│       └── util/           # Utilities, extensions
├── androidMain/            # Android-specific implementations
│   └── kotlin/app/justfyi/
│       ├── ble/            # Android BLE implementation
│       ├── data/local/     # Android database driver
│       ├── di/             # Android DI modules
│       └── platform/       # Android platform services
├── iosMain/                # iOS-specific implementations
│   └── kotlin/app/justfyi/
│       ├── ble/            # iOS Core Bluetooth implementation
│       ├── data/local/     # iOS database driver
│       ├── di/             # iOS DI modules
│       └── platform/       # iOS platform services
└── commonTest/             # Shared test code
```

### androidApp/

Minimal Android entry point that initializes the dependency graph and hosts the Compose UI.

### iosApp/

Swift/Xcode project that hosts the Compose Multiplatform UI on iOS.

## Layer Architecture

The application follows Clean Architecture principles:

```
┌─────────────────────────────────────────────┐
│              Presentation Layer             │
│  (Compose UI, ViewModels, Navigation)       │
└─────────────────────┬───────────────────────┘
                      │
┌─────────────────────▼───────────────────────┐
│               Domain Layer                  │
│    (Use Cases, Domain Models, Interfaces)   │
└─────────────────────┬───────────────────────┘
                      │
┌─────────────────────▼───────────────────────┐
│                Data Layer                   │
│  (Repositories, Data Sources, Firebase)     │
└─────────────────────────────────────────────┘
```

### Presentation Layer

- **Screens**: Compose UI screens (`HomeScreen`, `OnboardingScreen`, etc.)
- **ViewModels**: State holders using `ViewModel` pattern with Kotlin Flows
- **Components**: Reusable UI components (`JustFyiButton`, `JustFyiCard`, etc.)
- **Navigation**: Type-safe navigation using Compose Navigation

### Domain Layer

- **Use Cases**: Business logic operations (`RecordInteractionUseCase`, `ExposureReportUseCase`)
- **Models**: Domain entities (`Interaction`, `User`, `ExposureReport`, `STI`)
- **Repository Interfaces**: Abstractions for data access

### Data Layer

- **Repositories**: Implementation of domain interfaces
- **Local Data**: SQLDelight database with SQLCipher encryption
- **Remote Data**: Firebase (Firestore, Auth, Functions)
- **BLE**: Bluetooth Low Energy for interaction recording

## Key Patterns

### MVVM with Unidirectional Data Flow

```kotlin
class HomeViewModel(
    private val interactionUseCase: RecordInteractionUseCase,
    private val bleManager: BleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onStartScanning() {
        viewModelScope.launch {
            bleManager.startScanning()
                .collect { nearbyUsers ->
                    _uiState.update { it.copy(nearbyUsers = nearbyUsers) }
                }
        }
    }
}
```

### Repository Pattern

Repositories abstract data sources and handle caching:

```kotlin
// Domain layer interface
interface InteractionRepository {
    suspend fun saveInteraction(interaction: Interaction)
    fun getInteractions(): Flow<List<Interaction>>
}

// Data layer implementation
class InteractionRepositoryImpl(
    private val interactionQueries: InteractionQueries,
    private val firebaseProvider: FirebaseProvider,
    private val dispatchers: AppCoroutineDispatchers
) : InteractionRepository {
    // Local-first with background sync to Firebase
}
```

### Use Case Pattern

Use cases encapsulate business logic:

```kotlin
class RecordInteractionUseCase(
    private val interactionRepository: InteractionRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(partnerId: String): Result<Interaction> {
        // Validate, create interaction, save locally, queue sync
    }
}
```

## Dependency Injection

We use **Metro** for compile-time multiplatform dependency injection.

### Scopes

```kotlin
// Application scope - lives for app lifetime
@SingleIn(AppScope::class)

// Data scope - database and Firebase services
@SingleIn(DataScope::class)

// BLE scope - Bluetooth components
@SingleIn(BleScope::class)
```

### Graph Structure

```kotlin
@DependencyGraph(
    scope = AppScope::class,
    additionalScopes = [DataScope::class, BleScope::class]
)
interface AppGraph : CoreProviders, AppGraphCore, NavigationGraph {
    val dispatchers: AppCoroutineDispatchers
}
```

## Database Architecture

### SQLDelight + SQLCipher

- **Type-safe SQL**: Queries defined in `.sq` files
- **Encryption**: Database encrypted at rest using SQLCipher
- **Key Storage**: Database key stored in Android Keystore / iOS Keychain

### Schema

```
┌─────────────┐  ┌──────────────┐  ┌───────────────┐
│    User     │  │ Interaction  │  │ Notification  │
├─────────────┤  ├──────────────┤  ├───────────────┤
│ id          │  │ id           │  │ id            │
│ anonymousId │  │ partnerId    │  │ type          │
│ createdAt   │  │ timestamp    │  │ message       │
└─────────────┘  │ synced       │  │ read          │
                 └──────────────┘  └───────────────┘

┌─────────────────┐  ┌──────────────┐
│ ExposureReport  │  │   Settings   │
├─────────────────┤  ├──────────────┤
│ id              │  │ key          │
│ selectedSTIs    │  │ value        │
│ dateRange       │  └──────────────┘
│ submitted       │
└─────────────────┘
```

## Firebase Integration

### Services Used

| Service | Purpose |
|---------|---------|
| Anonymous Auth | User identity without credentials |
| Firestore | Cloud database for sync |
| Cloud Functions | Backend logic (exposure processing) |
| Cloud Messaging | Push notifications |
| Crashlytics | Crash reporting |

### Data Flow

```
Local SQLite ──► Repository ──► Firebase Firestore
                    │
                    ▼
              Cloud Function
                    │
                    ▼
              FCM Notification
```

## BLE Architecture

Bluetooth Low Energy is used for anonymous interaction recording.

### Components

```
┌─────────────────┐
│   BleManager    │  Coordinates scanning and advertising
├─────────────────┤
│ startScanning() │
│ startAdvertising()
└────────┬────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌────────┐ ┌────────────┐
│Scanner │ │ Advertiser │
└────────┘ └────────────┘
```

### Platform Implementations

- **Android**: `BluetoothLeScanner`, `BluetoothLeAdvertiser`, GATT Server
- **iOS**: `CBCentralManager`, `CBPeripheralManager`

## Navigation

Type-safe navigation using sealed classes:

```kotlin
sealed interface NavRoute {
    data object Home : NavRoute
    data object Profile : NavRoute
    data object Settings : NavRoute

    sealed interface Onboarding : NavRoute {
        data object Start : Onboarding
    }

    sealed interface ExposureReport : NavRoute {
        data object SelectSTI : ExposureReport
        data object SelectDate : ExposureReport
        data object Review : ExposureReport
    }
}
```

## Testing Strategy

### Unit Tests

- **Location**: `shared/src/commonTest/`
- **Focus**: Use cases, repositories, ViewModels
- **Tools**: JUnit, Kotest assertions, Turbine for Flows

### Test Patterns

```kotlin
@Test
fun `should emit nearby users when scanning`() = runTest {
    val viewModel = HomeViewModel(fakeUseCase, fakeBleManager)

    viewModel.uiState.test {
        viewModel.onStartScanning()

        val state = awaitItem()
        assertEquals(2, state.nearbyUsers.size)
    }
}
```

## Performance Considerations

- **Baseline Profiling**: Pre-compiled code paths for faster startup
- **Local-First**: All operations work offline, sync in background
- **Lazy Loading**: ViewModels created on demand
- **Flow Operators**: Use `stateIn`, `shareIn` to avoid redundant work
