#!/bin/bash

# Play Store Screenshot Generation Script
# Generates marketing screenshots and feature graphic for Google Play Store.
#
# Output:
#   - 25 marketing screenshots (5 screens x 5 languages) at 1080x1920
#   - 1 feature graphic at 1024x500
#
# Prerequisites:
#   - JDK 17 or later
#   - Android device or emulator connected (for marketing screenshots)
#   - ADB in PATH (for marketing screenshots)
#
# Usage:
#   ./scripts/generate-playstore-screenshots.sh

set -e

# Define project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# Define paths
OUTPUT_DIR="playstore-screenshots"
SNAPSHOT_DIR="androidApp/src/test/snapshots/images"
LANGUAGES="en de es fr pt"

echo "=== Play Store Screenshot Generation ==="
echo ""

echo "Step 1/5: Cleaning previous output..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
echo "Done."
echo ""

echo "Step 2/5: Generating feature graphic with Roborazzi (no device needed)..."
./gradlew :androidApp:recordRoborazziDebug --tests "*.FeatureGraphicTest" --quiet || {
    echo "Warning: Feature graphic generation may have failed."
}
echo "Done."
echo ""

# Check if adb is available for marketing screenshots
if ! command -v adb &> /dev/null; then
    echo "Warning: adb not found in PATH"
    echo "Add Android SDK platform-tools to your PATH to generate marketing screenshots:"
    echo "  export PATH=\$PATH:~/Library/Android/sdk/platform-tools"
    echo ""
    echo "Skipping marketing screenshots..."
    SKIP_MARKETING=true
else
    # Check if device is connected
    if ! adb devices | grep -q "device$"; then
        echo "Warning: No Android device connected"
        echo "Connect a device or start an emulator to generate marketing screenshots"
        echo ""
        echo "Skipping marketing screenshots..."
        SKIP_MARKETING=true
    else
        SKIP_MARKETING=false
    fi
fi

if [ "$SKIP_MARKETING" = false ]; then
    echo "Step 3/5: Clearing previous screenshots from device..."
    adb shell rm -rf /sdcard/Pictures/marketing-screenshots/
    echo "Done."
    echo ""

    echo "Step 4/5: Generating marketing screenshots (requires device)..."
    ./gradlew :androidApp:connectedAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=app.justfyi.MarketingScreenshotTest \
        --quiet || {
        echo "Warning: Some tests may have failed. Proceeding to collect screenshots..."
    }
    echo "Done."
    echo ""

    echo "Step 5/5: Collecting and organizing screenshots..."
    # Create language directories
    for lang in $LANGUAGES; do
        mkdir -p "$OUTPUT_DIR/$lang"
        adb pull "/sdcard/Pictures/marketing-screenshots/$lang/" "$OUTPUT_DIR/$lang/" 2>/dev/null || true
    done
else
    echo "Step 3-4/5: Skipped (no device connected)"
    echo ""
    echo "Step 5/5: Collecting feature graphic..."
fi

# Copy feature graphic from Roborazzi output
FEATURE_GRAPHIC="$SNAPSHOT_DIR/feature_graphic.png"
if [ -f "$FEATURE_GRAPHIC" ]; then
    cp "$FEATURE_GRAPHIC" "$OUTPUT_DIR/feature-graphic.png"
    echo "  Feature graphic copied (1024x500)"
else
    echo "  Warning: Feature graphic not found at $FEATURE_GRAPHIC"
fi
echo "Done."
echo ""

# Verify output
echo "=== Verifying output ==="
SCREENSHOTS_FOUND=0
for lang in $LANGUAGES; do
    for i in 1 2 3 4 5; do
        if [ -f "$OUTPUT_DIR/$lang/$i.png" ]; then
            SCREENSHOTS_FOUND=$((SCREENSHOTS_FOUND + 1))
        fi
    done
done
if [ -f "$OUTPUT_DIR/feature-graphic.png" ]; then
    SCREENSHOTS_FOUND=$((SCREENSHOTS_FOUND + 1))
fi

if [ "$SKIP_MARKETING" = true ]; then
    TOTAL_EXPECTED=1
    echo "  Feature graphic only mode (no device connected)"
else
    TOTAL_EXPECTED=26
fi

if [ $SCREENSHOTS_FOUND -eq $TOTAL_EXPECTED ]; then
    echo "  All $TOTAL_EXPECTED files generated successfully!"
else
    echo "  Warning: Found $SCREENSHOTS_FOUND of $TOTAL_EXPECTED expected files"
fi
echo ""

echo "=== Play Store Screenshot Generation Complete ==="
echo ""
echo "Screenshots saved to:"
if [ "$SKIP_MARKETING" = false ]; then
    echo "  $OUTPUT_DIR/{en,de,es,fr,pt}/1-5.png  (25 marketing screenshots @ 1080x1920)"
fi
echo "  $OUTPUT_DIR/feature-graphic.png       (1 feature graphic @ 1024x500)"
echo ""
if [ "$SKIP_MARKETING" = false ]; then
    echo "Screen mapping:"
    echo "  1.png = Scanning"
    echo "  2.png = Users Detected"
    echo "  3.png = Profile"
    echo "  4.png = Notification"
    echo "  5.png = History"
    echo ""
fi
echo "Total: $SCREENSHOTS_FOUND files"
