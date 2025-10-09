#!/bin/bash

echo "=== Tixly App Deployment Script ==="

# Function to cleanup emulator on script exit
cleanup_emulator() {
    if [ ! -z "$EMULATOR_PID" ] && kill -0 "$EMULATOR_PID" 2>/dev/null; then
        echo ""
        echo "Shutting down emulator gracefully..."
        # First try gentle termination
        kill -TERM "$EMULATOR_PID" 2>/dev/null
        sleep 3

        # Check if still running and force kill if needed
        if kill -0 "$EMULATOR_PID" 2>/dev/null; then
            echo "Force stopping emulator..."
            kill -KILL "$EMULATOR_PID" 2>/dev/null
            sleep 1
        fi
        echo "✓ Emulator shutdown complete"
    fi
}

# Set up trap to cleanup on script exit
trap cleanup_emulator EXIT INT TERM

# Android SDK configuration
export ANDROID_HOME=/usr/local/pkg/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin

# Configuration
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.tixly.app.debug"  # Debug builds have .debug suffix
MAIN_ACTIVITY="TicketsActivity"
TEST_FILES_DIR="test-files"  # Test files directory in project root

# Build the app
echo "1. Building the application..."
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo "✗ Build failed"
    exit 1
fi
echo "✓ APK built successfully!"

# Check for connected devices/emulators
echo "2. Checking device connection..."
DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)

if [ $DEVICES -eq 0 ]; then
    echo "⚠ No device found. Starting emulator..."

    # Check for available AVDs
    AVD_LIST=$(emulator -list-avds)
    if [ -z "$AVD_LIST" ]; then
        echo "Creating new emulator..."
        avdmanager create avd -n TixlyEmulator -k "android-35" --device "pixel_5" --force
        AVD_NAME="TixlyEmulator"
    else
        AVD_NAME=$(echo "$AVD_LIST" | head -1)
        echo "Using existing emulator: $AVD_NAME"
    fi

    # Start emulator in background
    echo "Starting emulator $AVD_NAME..."
    emulator -avd $AVD_NAME -no-audio -no-snapshot-save &
    EMULATOR_PID=$!

    # Wait for emulator to boot
    echo "Waiting for emulator to boot (this may take several minutes)..."
    for i in {1..60}; do
        sleep 5
        if adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; then
            echo "✓ Emulator booted successfully!"
            break
        fi
        echo "Waiting... ($i/60)"
    done

    # Final check
    DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
    if [ $DEVICES -eq 0 ]; then
        echo "✗ Failed to connect to emulator"
        exit 1
    fi
else
    echo "✓ Device already connected"
fi

# Show connected devices
echo "Connected devices:"
adb devices

# Install the app
echo "3. Installing application..."
INSTALL_RESULT=$(adb install -r "$APK_PATH" 2>&1)

if [[ $INSTALL_RESULT == *"Success"* ]]; then
    echo "✓ Application installed successfully!"
else
    echo "✗ Installation failed: $INSTALL_RESULT"
    exit 1
fi

# Copy test PDF if it exists
if [ -f "$TEST_FILES_DIR/ticket4.pdf" ]; then
    echo "4. Copying test PDF to device..."
    adb push "$TEST_FILES_DIR/ticket4.pdf" /sdcard/Download/
    echo "✓ ticket4.pdf copied to device"
fi

# Copy test ticket images if they exist
echo "5. Copying test ticket images to device..."

TICKETS_COPIED=0
if [ -f "$TEST_FILES_DIR/ticket1.png" ]; then
    adb push "$TEST_FILES_DIR/ticket1.png" /sdcard/Download/
    echo "✓ ticket1.png copied to device"
    ((TICKETS_COPIED++))
fi

if [ -f "$TEST_FILES_DIR/ticket2.png" ]; then
    adb push "$TEST_FILES_DIR/ticket2.png" /sdcard/Download/
    echo "✓ ticket2.png copied to device"
    ((TICKETS_COPIED++))
fi

if [ -f "$TEST_FILES_DIR/ticket3.jpg" ]; then
    adb push "$TEST_FILES_DIR/ticket3.jpg" /sdcard/Download/
    echo "✓ ticket3.jpg copied to device"
    ((TICKETS_COPIED++))
fi

if [ $TICKETS_COPIED -eq 0 ]; then
    echo "⚠ No test ticket images found in $TEST_FILES_DIR"
else
    echo "✓ $TICKETS_COPIED test ticket images copied to device"
fi

# Launch the app
echo "6. Launching application..."
adb shell am start -n "$PACKAGE_NAME/com.tixly.app.$MAIN_ACTIVITY"

echo ""
echo "✓ Tixly app deployed and launched successfully!"
echo ""
echo "Available features:"
echo "- Tap 'Scan PDF' to process PDF files"
echo "- Tap 'View Tickets' to see saved tickets"
echo "- Tap 'Exit' to close the application"
echo ""
echo "Test files available in Downloads folder:"
if [ -f "$TEST_FILES_DIR/ticket4.pdf" ]; then
    echo "- ticket4.pdf (PDF format)"
fi
if [ $TICKETS_COPIED -gt 0 ]; then
    echo "- ticket1.png, ticket2.png, ticket3.jpg (Image formats)"
    echo ""
    echo "To test image scanning:"
    echo "1. Open file manager on emulator"
    echo "2. Navigate to Downloads folder"
    echo "3. Select ticket images to test scanning functionality"
fi

# Note about emulator management
if [ ! -z "$EMULATOR_PID" ] && kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo ""
    echo "✓ Emulator is running in background"
    echo "  Press Ctrl+C or close this terminal to stop the emulator"
    echo "  To manually stop it: adb emu kill"
    echo ""
    echo "Emulator will keep running until you close this terminal..."

    # Keep script running until user interrupts
    while true; do
        sleep 1
        # Check if emulator is still running
        if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
            echo "Emulator has stopped unexpectedly"
            break
        fi
    done
fi
