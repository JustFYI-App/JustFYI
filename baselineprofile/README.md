# Baseline Profile Generation

This module generates baseline profiles for the Just FYI app to optimize startup performance by pre-compiling critical code paths.

## Physical Device Requirements

Baseline profile generation requires a physical Android device with specific requirements:

- **Android 13+ (API 33+)** OR a **rooted device** for profile generation
- USB debugging enabled in Developer Options
- Device connected and recognized by `adb devices`
- Sufficient storage space for profile output

## Generating Baseline Profiles

1. Connect a compatible physical device via USB
2. Verify the device is recognized:
   ```bash
   adb devices
   ```
3. Run the profile generation command:
   ```bash
   ./gradlew :composeApp:generateBaselineProfile
   ```
4. Monitor the output for successful profile collection

## Generated Profile Output

Profiles are generated to:
```
composeApp/src/release/generated/baselineProfiles/baseline-prof.txt
```

The profile contains method signatures for:
- App startup paths (new user and returning user)
- Main screen navigation (Home, Profile, InteractionHistory, NotificationList, Settings)
- List scrolling behavior
- Onboarding flow steps

## Building Release with Profile

After generating the profile, build the release APK:
```bash
./gradlew :composeApp:assembleRelease
```

The baseline profile will be automatically bundled into the release build.

## Profile Generator Tests

The `BaselineProfileGenerator` class includes the following test methods:

| Method | Purpose |
|--------|---------|
| `generateNewUserStartupProfile()` | Profiles cold start to onboarding screen |
| `generateReturningUserStartupProfile()` | Profiles cold start to Home screen |
| `generateNavigationProfile()` | Profiles navigation between main screens |
| `generateScrollingProfile()` | Profiles list scrolling in InteractionHistory and NotificationList |
| `generateOnboardingProfile()` | Profiles all 4 onboarding steps |

## Notes

- Profile generation uses `useConnectedDevices = true` (not Gradle-managed devices)
- The Exposure Report flow is explicitly excluded from profiling
- Focus is on TTID (Time-to-Initial-Display) optimization only
- Animations are disabled during profile generation for consistency
