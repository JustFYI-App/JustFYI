# Just FYI

**Stay Safe, Stay Anonymous**

A privacy-first mobile app for anonymous STI contact tracing. No names, no emails, no phone numbers—just anonymous IDs protecting public health.

## Overview

Just FYI helps people anonymously record sexual encounters and notify previous partners if they test positive for an STI. Using Bluetooth Low Energy, the app enables contact tracing in communities where anonymity is essential.

### Key Features

- **Anonymous Interaction Recording** - Record encounters via BLE without exchanging personal information
- **Privacy-First Design** - No personal data collected, users represented only by random IDs
- **Exposure Notifications** - Notify past partners anonymously if you test positive
- **Offline Support** - Fully functional without network, syncs in background
- **Multi-language** - English, German, Spanish, French, and Portuguese

## Tech Stack

| Category | Technology |
|----------|------------|
| Framework | Kotlin Multiplatform (KMP) |
| UI | Compose Multiplatform + Material 3 |
| Database | SQLDelight + SQLCipher |
| Backend | Firebase (Auth, Firestore, Functions, FCM) |
| DI | Metro |
| BLE | Android BLE APIs / Core Bluetooth |

## Getting Started

### Prerequisites

- **JDK 17** or higher
- **Android Studio** Ladybug or later
- **Xcode 15+** (for iOS development)

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/JustFYI-App/JustFYI.git
   cd justfyi
   ```

2. Open in Android Studio

3. Sync Gradle and build

### Build Commands

**Android:**
```bash
# Debug build
./gradlew :androidApp:assembleDebug

# Release build
./gradlew :androidApp:assembleRelease

# Run tests
./gradlew :shared:testDebugUnitTest
```

**iOS:**
```bash
# Build iOS framework
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Open in Xcode
open iosApp/iosApp.xcworkspace
```

### Code Quality

```bash
# Format code
./gradlew ktlintFormat

# Check style
./gradlew ktlintCheck

# Static analysis
./gradlew detekt

# Run all checks
./gradlew ktlintCheck detekt
```

### Screenshot Generation

Generate app store screenshots for all languages and themes:

```bash
./scripts/generate-screenshots.sh
```

This captures 50 screenshots (5 languages × 5 screens × 2 themes) and copies them to `website/public/screenshots/`.

See [androidApp/src/androidTest/README.md](androidApp/src/androidTest/README.md) for details.

## Project Structure

```
justfyi/
├── shared/                 # KMP shared module
│   └── src/
│       ├── commonMain/     # Shared Kotlin code
│       ├── androidMain/    # Android-specific implementations
│       ├── iosMain/        # iOS-specific implementations
│       └── commonTest/     # Shared tests
├── androidApp/             # Android application
├── iosApp/                 # iOS application (Xcode project)
├── baselineprofile/        # Android baseline profiling
└── .github/workflows/      # CI/CD pipelines
```

For detailed architecture, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Documentation

- [Architecture Overview](ARCHITECTURE.md)
- [Contributing Guidelines](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
