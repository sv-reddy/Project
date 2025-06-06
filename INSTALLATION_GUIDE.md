# GeoCamOff - Camera Detection App

## Installation Guide

### APK Files
- **GeoCamOff-v1.0-signed.apk** - Production release version (optimized, signed)
- **GeoCamOff-v1.0-debug-signed.apk** - Debug version (with debugging features)

### Installation Steps

1. **Download the APK**: Use the `GeoCamOff-v1.0-signed.apk` for normal use.

2. **Enable Unknown Sources**:
   - Go to Settings > Security (or Privacy & Security)
   - Enable "Install from Unknown Sources" or "Allow installation of apps from unknown sources"
   - Some devices: Settings > Apps & notifications > Special app access > Install unknown apps

3. **Install the APK**:
   - Transfer the APK to your Android device
   - Tap on the APK file to install
   - If Google Play Protect shows a warning, see the next section for steps to bypass it

4. **Bypassing Google Play Protect (if app is blocked)**:
   - If you see a message like "App blocked to protect your device":
     1. **Tap "Install anyway"** (if available):
        - On some devices, tap "Details" or "Show more" to reveal this option.
     2. **If no option to install anyway:**
        - Open the Google Play Store app
        - Tap your profile icon (top right) > Play Protect > Settings (gear icon)
        - Turn off "Scan apps with Play Protect"
        - Install the APK
        - Re-enable Play Protect after installation
     3. **If Play Protect continues to warn after install:**
        - Open Play Protect, find GeoCamOff, and mark it as safe/trusted

5. **Grant Permissions**:
   - The app will request several permissions on first launch
   - Grant all permissions for full functionality:
     - Camera access
     - Location access (including background location)
     - Notification access

6. **Enable Accessibility Service** (Recommended):
   - Go to Settings > Accessibility
   - Find "geocamoff" in the list
   - Enable the service for most reliable detection

### App Features

- **Camera Detection**: Monitors when camera apps are opened
- **Location Tracking**: Records location when camera is detected
- **Background Monitoring**: Works even when app is closed
- **Accessibility Service**: Provides system-level monitoring
- **Foreground Service**: Maintains monitoring with persistent notification

### Troubleshooting

- **App won't install**: Enable "Install from unknown sources"
- **Permissions denied**: Check Settings > Apps > GeoCamOff > Permissions
- **Not detecting camera**: Enable the accessibility service
- **Battery optimization**: Disable battery optimization for reliable background operation

### Technical Details

- **Minimum Android Version**: Android 7.0 (API 24)
- **Target Android Version**: Android 14 (API 35)
- **Signed with**: Self-signed certificate (valid until 2052)
- **Architecture**: Universal APK (supports all device architectures)

### Security

The app is signed with a self-signed certificate for distribution outside the Play Store. The certificate is included in the keystore file and is valid for 27+ years. The app requires various permissions for core functionality but does not collect or transmit personal data beyond local monitoring.
