#!/bin/bash

# Enable job control to properly handle background processes
set -m

echo "=== Tixly App Deployment Script ==="

# Flag to track if we're in cleanup
CLEANUP_STARTED=false

# Function to cleanup emulator on script exit
cleanup_emulator() {
    # Prevent multiple cleanup calls
    if [ "$CLEANUP_STARTED" = true ]; then
        return
    fi
    CLEANUP_STARTED=true

    if [ ! -z "$EMULATOR_PID" ] && kill -0 "$EMULATOR_PID" 2>/dev/null; then
        echo ""
        echo "Shutting down emulator gracefully..."

        # Try to kill the entire process group first (if we have PGID)
        if [ ! -z "$EMULATOR_PGID" ]; then
            kill -TERM -$EMULATOR_PGID 2>/dev/null
        else
            # Fallback to killing just the process
            kill -TERM $EMULATOR_PID 2>/dev/null
        fi

        # Wait a bit for graceful shutdown
        sleep 3

        # Check if still running and force kill if needed
        if kill -0 "$EMULATOR_PID" 2>/dev/null; then
            echo "Force stopping emulator..."
            if [ ! -z "$EMULATOR_PGID" ]; then
                kill -KILL -$EMULATOR_PGID 2>/dev/null
            else
                kill -KILL $EMULATOR_PID 2>/dev/null
            fi
            sleep 1
        fi
        echo "✓ Emulator shutdown complete"
    fi
}

# Function to handle Ctrl+C
handle_interrupt() {
    echo ""
    echo "⚠ Script interrupted by user (Ctrl+C)"
    cleanup_emulator
    exit 130
}

# Function to handle terminal close (SIGHUP)
handle_terminal_close() {
    echo ""
    echo "⚠ Terminal closed - cleaning up..."
    cleanup_emulator
    exit 129
}

# Set up trap to cleanup on script exit and all termination signals
trap cleanup_emulator EXIT
trap handle_interrupt INT TERM
trap handle_terminal_close HUP

# Android SDK configuration
export ANDROID_HOME=/usr/local/pkg/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin

# Configuration
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.tixly.app.debug"  # Debug builds have .debug suffix
MAIN_ACTIVITY="TicketsActivity"
TEST_FILES_DIR="test-files"  # Test files directory in project root

# Clean build to ensure all changes are applied
echo "1. Cleaning previous build..."
./gradlew clean
if [ $? -ne 0 ]; then
    echo "✗ Clean failed"
    exit 1
fi
echo "✓ Clean completed!"

# Build the app
echo "2. Building the application..."
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo "✗ Build failed"
    exit 1
fi
echo "✓ APK built successfully!"

# Check for connected devices/emulators
echo "3. Checking device connection..."
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

    # Start emulator in background with data wipe
    echo "Starting emulator $AVD_NAME with data wipe..."
    # Start emulator in background, it will be in its own process group due to set -m
    emulator -avd $AVD_NAME -no-audio -no-snapshot-save -wipe-data &
    EMULATOR_PID=$!

    # Store the process group ID for cleanup
    EMULATOR_PGID=$(ps -o pgid= -p $EMULATOR_PID | tr -d ' ')

    echo "✓ Emulator started (PID: $EMULATOR_PID, PGID: $EMULATOR_PGID)"

    # Wait a moment for emulator window to appear
    sleep 2

    # Set emulator window to always on top using wmctrl
    if command -v wmctrl &> /dev/null; then
        # Find MAIN emulator window (not the control panel) and set it to always on top
        # The main window usually has "Android Emulator" in the title
        EMULATOR_WINDOW=$(wmctrl -l | grep -i "Android Emulator" | grep -v "Emulator$" | head -1 | awk '{print $1}')
        if [ ! -z "$EMULATOR_WINDOW" ]; then
            wmctrl -i -r "$EMULATOR_WINDOW" -b add,above
            echo "✓ Emulator main window set to 'Always on Top'"
        else
            # Fallback: try to find any emulator window
            EMULATOR_WINDOW=$(wmctrl -l | grep -i "emulator" | head -1 | awk '{print $1}')
            if [ ! -z "$EMULATOR_WINDOW" ]; then
                wmctrl -i -r "$EMULATOR_WINDOW" -b add,above
                echo "✓ Emulator window set to 'Always on Top'"
            fi
        fi
    else
        echo "⚠ wmctrl not installed, skipping 'Always on Top' setting"
        echo "  Install with: sudo apt-get install wmctrl"
    fi

    # Wait for emulator to boot
    echo "Waiting for emulator to boot (this may take several minutes)..."
    echo "Press Ctrl+C to cancel..."
    for i in {1..60}; do
        # Use a short sleep with trap check to allow Ctrl+C to work
        for _ in {1..5}; do
            sleep 1 || exit 1  # Exit on interrupt
        done

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

# Clear app data if app is already installed
echo "4. Clearing app data..."
if adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
    echo "App is installed, clearing data..."
    adb shell pm clear "$PACKAGE_NAME"
    echo "✓ App data cleared!"
else
    echo "App not installed yet, skipping data clear"
fi

# Install the app
echo "5. Installing application..."
INSTALL_RESULT=$(adb install -r "$APK_PATH" 2>&1)

if [[ $INSTALL_RESULT == *"Success"* ]]; then
    echo "✓ Application installed successfully!"
else
    echo "✗ Installation failed: $INSTALL_RESULT"
    exit 1
fi

# Copy test PDF if it exists
if [ -f "$TEST_FILES_DIR/ticket4.pdf" ]; then
    echo "6. Copying test PDF to device..."
    adb push "$TEST_FILES_DIR/ticket4.pdf" /sdcard/Download/
    echo "✓ ticket4.pdf copied to device"
fi

# Copy test ticket images if they exist
echo "7. Copying test ticket images to device..."

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
echo "8. Launching application..."
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PACKAGE_NAME/com.tixly.app.$MAIN_ACTIVITY"

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

    # Wait for emulator process to finish or for user to interrupt
    # This allows proper signal handling (Ctrl+C, terminal close)
    wait $EMULATOR_PID 2>/dev/null

    echo ""
    echo "✓ Emulator has stopped"
fi
