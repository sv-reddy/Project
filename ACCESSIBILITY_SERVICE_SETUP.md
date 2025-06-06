# Camera Detection Accessibility Service Setup

## Overview
The app now uses an Android Accessibility Service for the most reliable camera detection. This provides persistent monitoring that continues even when the app is closed or the device is restarted.

## Why Accessibility Service?
- **Persistent**: Works even when app is closed
- **System-level**: Monitors all app launches and window changes
- **Reliable**: Survives device restarts and app kills
- **Accurate**: Detects camera apps immediately when opened

## Setup Instructions

### 1. Install and Launch the App
- Install the APK on your device
- Launch the app and grant all requested permissions (Camera, Location, Notifications)

### 2. Enable Accessibility Service
When you launch the app, it will automatically show a dialog asking you to enable the accessibility service:

1. Tap "Open Settings" in the dialog
2. You'll be taken to Android's Accessibility Settings
3. Find "geocamoff" in the list of accessibility services
4. Tap on it and toggle it ON
5. Grant any additional permissions when prompted

### 3. Manual Setup (if needed)
If the automatic dialog doesn't appear:

1. Go to Settings → Accessibility
2. Find "geocamoff" under Services
3. Enable the service
4. Return to the app

### 4. Verification
- The app will show "Accessibility Service Status: ENABLED" in the logs
- Camera detection will now work persistently in the background

## How It Works

### Camera App Detection
The accessibility service monitors for these camera apps:
- Stock Android Camera apps
- Samsung Camera
- Google Camera
- Huawei Camera
- OnePlus Camera
- Xiaomi Camera
- And many more manufacturer-specific camera apps

### Automatic Protection
When a camera app is detected in a restricted area:
1. **Alert**: Shows overlay notification
2. **Block**: Automatically attempts to close the camera app
3. **Methods**: Uses multiple closure techniques:
   - Back button press
   - Home button press
   - Recent apps navigation
   - Multiple attempts for reliability

### Restricted Area Detection
- Uses polygon geofencing for accurate area detection
- Loads restricted areas from `assets/restricted_areas.json`
- Real-time location checking with GPS

## Advantages Over Traditional Monitoring

| Feature | Traditional Service | Accessibility Service |
|---------|-------------------|---------------------|
| Persistence | Limited | Excellent |
| App closure survival | ❌ | ✅ |
| System restart survival | ❌ | ✅ |
| Battery optimization immunity | ❌ | ✅ |
| Detection accuracy | Good | Excellent |
| Response time | Delayed | Immediate |

## Troubleshooting

### Service Not Working
1. Verify accessibility service is enabled in Android settings
2. Check that location permissions are granted
3. Ensure app is not battery optimized
4. Restart the device if needed

### Camera Apps Not Detected
1. Check if your camera app is in the supported list
2. The service logs all window changes - check logs for your camera app
3. Report unsupported camera apps for future updates

### Location Issues
1. Ensure GPS is enabled
2. Grant location permissions including background location
3. Check that restricted areas JSON file exists in assets

## Privacy & Security
- The accessibility service only monitors window state changes
- No personal data is collected or transmitted
- Location data stays on device
- Service only activates for camera-related apps

## Technical Details
- Service file: `CameraAccessibilityService.kt`
- Configuration: `accessibility_service_config.xml`
- Supported events: Window state changes, content changes
- Permissions: `BIND_ACCESSIBILITY_SERVICE`
