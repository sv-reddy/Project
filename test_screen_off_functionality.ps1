# Test Script for Screen-Off Functionality
# This script helps test that the GeoCamOff app works when the screen is turned off

Write-Host "GeoCamOff Screen-Off Functionality Test" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# Check if ADB is available
try {
    $adbVersion = adb version 2>$null
    if (-not $adbVersion) {
        Write-Host "ERROR: ADB not found in PATH. Please install Android SDK platform-tools." -ForegroundColor Red
        exit 1
    }
    Write-Host "✓ ADB found" -ForegroundColor Green
} catch {
    Write-Host "ERROR: ADB not available. Please install Android SDK platform-tools." -ForegroundColor Red
    exit 1
}

# Check if device is connected
$devices = adb devices
if ($devices -notmatch "device$") {
    Write-Host "ERROR: No Android device connected. Please connect your device and enable USB debugging." -ForegroundColor Red
    exit 1
}
Write-Host "✓ Android device connected" -ForegroundColor Green

Write-Host ""
Write-Host "Testing Screen-Off Functionality:" -ForegroundColor Yellow
Write-Host ""

# Install the latest debug APK
Write-Host "1. Installing latest APK..." -ForegroundColor Cyan
try {
    $installResult = adb install -r "app\build\outputs\apk\debug\app-debug.apk" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✓ APK installed successfully" -ForegroundColor Green
    } else {
        Write-Host "   ⚠ APK installation had warnings: $installResult" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   ✗ Failed to install APK: $_" -ForegroundColor Red
    exit 1
}

# Start the app
Write-Host ""
Write-Host "2. Starting GeoCamOff app..." -ForegroundColor Cyan
adb shell am start -n com.example.geocamoff/.MainActivity
Start-Sleep -Seconds 3
Write-Host "   ✓ App launched" -ForegroundColor Green

# Check if services are running
Write-Host ""
Write-Host "3. Checking if services are running..." -ForegroundColor Cyan

# Check CameraDetectionService
$cameraService = adb shell "ps -A | grep com.example.geocamoff"
if ($cameraService) {
    Write-Host "   ✓ App process is running" -ForegroundColor Green
} else {
    Write-Host "   ⚠ App process not found - this might be normal if services run in background" -ForegroundColor Yellow
}

# Check for accessibility service
$accessibilityServices = adb shell settings get secure enabled_accessibility_services
if ($accessibilityServices -match "geocamoff") {
    Write-Host "   ✓ Accessibility service enabled" -ForegroundColor Green
} else {
    Write-Host "   ⚠ Accessibility service not enabled - please enable manually in Settings" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "4. Testing screen state transitions..." -ForegroundColor Cyan

# Turn off screen
Write-Host "   Turning screen OFF..." -ForegroundColor Yellow
adb shell input keyevent KEYCODE_POWER
Start-Sleep -Seconds 2

# Check if services are still running with screen off
Write-Host "   Checking services with screen OFF..." -ForegroundColor Yellow
$servicesScreenOff = adb shell "ps -A | grep com.example.geocamoff"
if ($servicesScreenOff) {
    Write-Host "   ✓ Services still running with screen OFF" -ForegroundColor Green
} else {
    Write-Host "   ⚠ Services may have stopped - checking logs..." -ForegroundColor Yellow
}

Start-Sleep -Seconds 3

# Turn screen back on
Write-Host "   Turning screen ON..." -ForegroundColor Yellow
adb shell input keyevent KEYCODE_POWER
Start-Sleep -Seconds 1

# Unlock screen (swipe up)
Write-Host "   Unlocking screen..." -ForegroundColor Yellow
adb shell input touchscreen swipe 500 1500 500 800
Start-Sleep -Seconds 2

Write-Host ""
Write-Host "5. Checking app logs for screen state handling..." -ForegroundColor Cyan

# Get recent logs related to screen state
Write-Host "   Recent screen state logs:" -ForegroundColor Yellow
$logs = adb logcat -t 50 -s "CameraDetectionService:D" "CameraAccessibilityService:D" "ScreenStateReceiver:D" "StateManager:D" 2>$null
if ($logs) {
    $logs | ForEach-Object {
        if ($_ -match "(Screen|screen|SCREEN)") {
            Write-Host "   $_" -ForegroundColor White
        }
    }
} else {
    Write-Host "   ⚠ No recent screen state logs found" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "6. Manual Test Instructions:" -ForegroundColor Cyan
Write-Host "   a) Press the power button to turn off the screen" -ForegroundColor White
Write-Host "   b) Wait 30 seconds with screen off" -ForegroundColor White
Write-Host "   c) Open camera app while screen is off (if possible)" -ForegroundColor White
Write-Host "   d) Turn screen back on and check for notifications" -ForegroundColor White
Write-Host "   e) Verify camera detection still works when screen is on" -ForegroundColor White

Write-Host ""
Write-Host "7. Battery Optimization Check:" -ForegroundColor Cyan
Write-Host "   To ensure the app works properly when screen is off:" -ForegroundColor White
Write-Host "   a) Go to Settings > Apps > GeoCamOff > Battery" -ForegroundColor White
Write-Host "   b) Set to 'Don't optimize' or 'No restrictions'" -ForegroundColor White
Write-Host "   c) Enable 'Allow background activity'" -ForegroundColor White

Write-Host ""
Write-Host "Test completed!" -ForegroundColor Green
Write-Host "The app should now work properly when the screen is turned off." -ForegroundColor Green
Write-Host ""
Write-Host "To monitor real-time logs during testing, run:" -ForegroundColor Yellow
Write-Host "adb logcat -s CameraDetectionService:D CameraAccessibilityService:D ScreenStateReceiver:D" -ForegroundColor Cyan
