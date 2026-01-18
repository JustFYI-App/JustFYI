# Contributing to Just FYI

Thank you for your interest in contributing to Just FYI! This document provides guidelines and instructions for contributing.

## Development Environment Setup

### Prerequisites

- **JDK 17** - Required for Kotlin/Gradle
- **Android Studio Ladybug** (2024.2.1) or later
- **Kotlin Multiplatform plugin** - Install via Android Studio Settings > Plugins > Marketplace
- **Xcode 15+** - For iOS development
- **CocoaPods** - For iOS dependencies (`sudo gem install cocoapods`)

### Initial Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/JustFYI-App/JustFYI.git
   cd justfyi
   ```

2. **Install git hooks:**
   ```bash
   ./scripts/setup-hooks.sh
   ```
   This installs a pre-push hook that runs tests before pushing.

3. **Open in Android Studio:**
   - Open the project root directory
   - Wait for Gradle sync to complete
   - Build the project

4. **iOS Setup (if developing iOS):**
   ```bash
   cd iosApp
   pod install
   open iosApp.xcworkspace
   ```

## Building the Project

### Android

```bash
# Debug build
./gradlew :androidApp:assembleDebug

# Release build
./gradlew :androidApp:assembleRelease

# Install on connected device
./gradlew :androidApp:installDebug
```

### iOS

```bash
# Build shared framework
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Then build in Xcode
open iosApp/iosApp.xcworkspace
```

### Running Tests

```bash
# All shared module tests
./gradlew :shared:testDebugUnitTest

# Specific test class
./gradlew :shared:testDebugUnitTest --tests "*.YourTestClass"
```

## Code Style

We use **ktlint** and **detekt** for code quality enforcement.

### Checking Code Style

```bash
# Check formatting
./gradlew ktlintCheck

# Check static analysis
./gradlew detekt

# Run all checks
./gradlew ktlintCheck detekt
```

### Auto-formatting

```bash
# Auto-fix formatting issues
./gradlew ktlintFormat
```

### Key Style Guidelines

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions small and focused
- Write self-documenting code; add comments only when necessary
- Use `@Composable` functions in PascalCase

## Proposing Changes

### Discuss Before Implementing

For larger features or significant changes, please **open an issue first** to discuss your proposal before starting implementation. This helps:

- Ensure the change aligns with project goals
- Avoid duplicate work
- Get early feedback on the approach
- Identify potential issues before investing time

**When to open an issue first:**
- New features or major enhancements
- Architectural changes
- Changes affecting multiple modules
- Removing or deprecating functionality

**When you can skip the issue:**
- Bug fixes with clear cause
- Documentation improvements
- Small refactors or code cleanup
- Dependency updates

## Making Changes

### Branch Naming

Use descriptive branch names:

```
feature/add-exposure-notification
fix/ble-connection-timeout
refactor/repository-pattern
docs/update-readme
```

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add exposure notification flow
fix: resolve BLE scanning on Android 14
docs: update setup instructions
refactor: extract interaction repository
test: add unit tests for exposure report
chore: update dependencies
```

### Pull Request Process

1. **Create a feature branch** from `main`
2. **Make your changes** with clear, focused commits
3. **Run tests and linting:**
   ```bash
   ./gradlew ktlintCheck detekt test
   ```
4. **Push your branch** and create a PR
5. **Fill out the PR template** with:
   - Summary of changes
   - Related issues
   - Testing performed
6. **Address review feedback**

### PR Requirements

- All CI checks must pass
- Code must be formatted (`ktlintCheck`)
- Tests must pass
- New features should include tests

## Testing Guidelines

### Unit Tests

- Place tests in `shared/src/commonTest/kotlin/`
- Use descriptive test names: `should return empty list when no interactions exist`
- Test behavior, not implementation
- Use fakes over mocks when possible

### Test Structure

```kotlin
class ExposureReportUseCaseTest {

    @Test
    fun `should create report with selected STIs`() {
        // Given
        val useCase = ExposureReportUseCase(fakeRepository)

        // When
        val result = useCase.createReport(listOf(STI.HIV))

        // Then
        assertEquals(1, result.selectedSTIs.size)
    }
}
```

## Architecture Guidelines

- Follow Clean Architecture layers (presentation, domain, data)
- Use ViewModels for UI state management
- Repositories abstract data sources
- Use Cases contain business logic
- See [ARCHITECTURE.md](ARCHITECTURE.md) for details

## Getting Help

- Open an issue for bugs or feature requests
- Check existing issues before creating new ones
- Ask questions in discussions

## Code of Conduct

Be respectful and constructive in all interactions. We're building software to help communities stay healthy.
