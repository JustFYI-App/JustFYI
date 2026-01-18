#!/bin/bash

# Screenshot Generation Script
# Generates app screenshots for all languages and themes, then copies to website.
#
# Prerequisites:
# - Android device or emulator connected
# - ADB in PATH (add ~/Library/Android/sdk/platform-tools to your PATH)
#
# Usage:
#   ./scripts/generate-screenshots.sh

set -e  # Exit on error

echo "=== Screenshot Generation ==="
echo ""

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo "Error: adb not found in PATH"
    echo "Add Android SDK platform-tools to your PATH:"
    echo "  export PATH=\$PATH:~/Library/Android/sdk/platform-tools"
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "Error: No Android device connected"
    echo "Connect a device or start an emulator first"
    exit 1
fi

echo "Step 1/4: Clearing previous screenshots from device..."
adb shell rm -rf /sdcard/Pictures/screenshots/
echo "Done."
echo ""

echo "Step 2/4: Running screenshot tests (this may take a few minutes)..."
./gradlew :androidApp:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.justfyi.HomeScreenScreenshotTest
echo "Done."
echo ""

echo "Step 3/4: Cleaning website screenshots directory..."
rm -rf website/src/assets/screenshots/light
rm -rf website/src/assets/screenshots/dark
echo "Done."
echo ""

echo "Step 4/4: Pulling screenshots from device..."
mkdir -p website/src/assets/screenshots
adb pull /sdcard/Pictures/screenshots/light website/src/assets/screenshots/
adb pull /sdcard/Pictures/screenshots/dark website/src/assets/screenshots/
echo "Done."
echo ""

echo "=== Screenshot Generation Complete ==="
echo ""
echo "Screenshots saved to:"
echo "  website/src/assets/screenshots/light/{en,de,es,fr,pt}/1-5.webp"
echo "  website/src/assets/screenshots/dark/{en,de,es,fr,pt}/1-5.webp"
echo ""
echo "Total: 50 screenshots (5 languages × 5 screens × 2 themes)"
