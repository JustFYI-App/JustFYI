# Screenshot Tests

Automated screenshot capture for app store listings in all supported languages and themes.

## Prerequisites

- Android device or emulator connected
- ADB configured: add `~/Library/Android/sdk/platform-tools` to your PATH

## Quick Start

Run the all-in-one script from the project root:

```bash
./scripts/generate-screenshots.sh
```

This will clear old screenshots, run tests, and copy results to the website folder.

## Manual Steps

### 1. Clear previous screenshots (optional)

```bash
adb shell rm -rf /sdcard/Pictures/screenshots/
```

### 2. Run the instrumented tests

```bash
./gradlew :androidApp:connectedAndroidTest
```

Or run only screenshot tests:

```bash
./gradlew :androidApp:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.justfyi.HomeScreenScreenshotTest
```

### 3. Pull screenshots from device

```bash
adb pull /sdcard/Pictures/screenshots/light/ website/public/screenshots/light/
adb pull /sdcard/Pictures/screenshots/dark/ website/public/screenshots/dark/
```

## Output Structure

Screenshots are saved in webp format, organized by theme and language:

```
website/public/screenshots/
├── light/
│   ├── en/
│   │   ├── 1.webp  (Scanning state)
│   │   ├── 2.webp  (3 users detected)
│   │   ├── 3.webp  (Profile screen)
│   │   ├── 4.webp  (Notification - exposure alert)
│   │   └── 5.webp  (Interaction history)
│   ├── de/
│   │   └── ... (same structure)
│   ├── es/
│   ├── fr/
│   └── pt/
└── dark/
    ├── en/
    │   └── ... (same structure)
    ├── de/
    ├── es/
    ├── fr/
    └── pt/
```

**Total screenshots:** 5 languages × 5 screens × 2 themes = 50 screenshots

## Themes

| Theme | Description |
|-------|-------------|
| light | Light mode with white backgrounds |
| dark  | Dark mode with dark backgrounds |

The website automatically shows the appropriate theme based on user's system preference using CSS `prefers-color-scheme`.

## Supported Languages

| Code | Language   | Sample Names (10 per language) |
|------|------------|--------------------------------|
| en   | English    | Alice, Bob, Charlie, Diana, Edward, Fiona, George, Hannah, Ivan, Julia |
| de   | German     | Hans, Anna, Klaus, Maria, Fritz, Greta, Otto, Liesel, Wolfgang, Ingrid |
| es   | Spanish    | Maria, Carlos, Elena, Pablo, Sofia, Miguel, Lucia, Fernando, Carmen, Diego |
| fr   | French     | Pierre, Marie, Jean, Sophie, Louis, Camille, Nicolas, Amelie, Thomas, Claire |
| pt   | Portuguese | Joao, Ana, Pedro, Beatriz, Lucas, Mariana, Rafael, Carolina, Tiago, Isabel |

## Screenshot States

1. **Scanning** - Empty home screen with scanning animation
2. **Users Detected** - Home screen showing 3 nearby users with different signal strengths
3. **Profile** - User profile screen with anonymous ID revealed
4. **Notification** - Notification list with one exposure alert (Herpes)
5. **History** - Interaction history showing 10 recorded contacts

## Adding New Screenshots

To add additional states, edit `HomeScreenScreenshotTest.kt`:

1. Create a new state function (e.g., `createNewState()`)
2. Create a new screen composable (e.g., `NewScreenForScreenshot()`)
3. Add a new test case in the `data()` function with the next screenshot number
4. Update this README with the new screenshot description
