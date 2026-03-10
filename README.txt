================================================================================
                              TIXLY - Ticket Manager
================================================================================

Version: 1.0.0
Platform: Android
Min SDK: 24 (Android 7.0) | Target SDK: 34 (Android 14)

================================================================================
                                 DESCRIPTION
================================================================================

Tixly is an Android application for managing event tickets with calendar
integration. The app allows you to scan tickets from PDFs and images,
automatically extract event information, and sync with the device calendar.

Key Features:
- Scan tickets from PDF files
- Scan tickets from images (PNG, JPG)
- Automatic event information extraction
- Calendar integration with reminders
- View and manage saved tickets
- AdMob integration for monetization

================================================================================
                                GETTING STARTED
================================================================================

1. Quick Start for Testing (Linux):
   ----------------------------------------------------------------------------
   chmod +x deploy.sh
   ./deploy.sh

   The script will automatically:
   - Build debug APK
   - Start emulator (if needed)
   - Install the app
   - Copy test files
   - Launch the app

2. Build with Gradle:
   ----------------------------------------------------------------------------
   ./gradlew assembleDebug          # Debug version
   ./gradlew assembleRelease        # Release version
   ./gradlew installDebug           # Install on device

   Output:
   - Debug APK:   app/build/outputs/apk/debug/app-debug.apk
   - Release APK: app/build/outputs/apk/release/app-release.apk

3. Using Android Studio:
   ----------------------------------------------------------------------------
   - File → Open → select Tixly folder
   - Run → Run 'app' (Shift+F10)

================================================================================

