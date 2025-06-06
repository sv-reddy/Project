# Location Services Disabled Feature Test

## Feature Description
When location services are turned off on the device, the app should:
1. Display "Please turn on the location to gain access" message in StatusFragment
2. Treat the device as being in a restricted zone (StateManager.updateGeofenceState(context, true))
3. Block camera access in CameraAccessibilityService when location services are disabled

## Implementation Summary

### Files Modified:
1. **StatusFragment.kt**: 
   - Added `isLocationServicesEnabled()` helper function
   - Modified `requestLocationUpdate()` to check location services first
   - Shows location services disabled message and treats as restricted zone

2. **CameraAccessibilityService.kt**:
   - Added `isLocationServicesEnabled()` helper function
   - Modified `handleCameraDetection()` to check location services first
   - Blocks camera and shows alert when location services are disabled

3. **strings.xml**:
   - Added `location_services_disabled` string resource

### Testing Steps:

#### Manual Testing:
1. **Install and run the app** with location services enabled (verify normal operation)
2. **Turn off location services** in device settings:
   - Go to Settings > Location > Turn off Location
3. **Open the app** and check the StatusFragment displays: "Please turn on the location to gain access"
4. **Try to open camera app** - should be blocked with notification alert
5. **Turn location services back on** - app should return to normal geofence checking

#### ADB Testing Commands:
```bash
# Disable location services
adb shell settings put secure location_providers_allowed ""

# Enable location services  
adb shell settings put secure location_providers_allowed "gps,network"

# Check current location setting
adb shell settings get secure location_providers_allowed
```

### Expected Behavior:
- **Location Services OFF**: Always treated as restricted zone, camera blocked
- **Location Services ON**: Normal geofence checking based on actual location
- **No location permission**: Shows permission denied message (existing behavior)
- **Location services OFF + No permission**: Shows location services disabled message (priority)

### Code Logic Flow:
1. Check if location services enabled (`LocationManager.isProviderEnabled()`)
2. If disabled → Show message + Block camera (treat as restricted)
3. If enabled → Check permissions → Get location → Check geofences
4. If location unavailable → Block camera (treat as restricted - existing fallback)

This implementation ensures robust camera blocking when location services are disabled, providing the security feature requested.
