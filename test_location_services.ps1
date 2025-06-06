# Location Services Test Script for GeoCamOff
# This script helps test the location services disabled feature

Write-Host "🧪 GeoCamOff Location Services Test" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

# Check if device is connected
$deviceCheck = adb devices
if ($deviceCheck -match "unauthorized") {
    Write-Host "❌ Device unauthorized. Please accept USB debugging prompt on device." -ForegroundColor Red
    exit 1
}
if (-not ($deviceCheck -match "device$")) {
    Write-Host "❌ No device connected. Please connect Android device with USB debugging enabled." -ForegroundColor Red
    exit 1
}

Write-Host "✅ Device connected" -ForegroundColor Green
Write-Host ""

# Function to check current location setting
function Get-LocationStatus {
    $status = adb shell settings get secure location_providers_allowed
    if ([string]::IsNullOrWhiteSpace($status) -or $status -eq "null") {
        return "DISABLED"
    } else {
        return "ENABLED ($status)"
    }
}

# Display current status
$currentStatus = Get-LocationStatus
Write-Host "📍 Current Location Services Status: $currentStatus" -ForegroundColor Yellow
Write-Host ""

Write-Host "🎯 Test Plan:" -ForegroundColor Cyan
Write-Host "1. Test with location services ENABLED (normal operation)" -ForegroundColor White
Write-Host "2. Test with location services DISABLED (should show restricted message)" -ForegroundColor White
Write-Host "3. Test camera blocking when location services are disabled" -ForegroundColor White
Write-Host ""

Write-Host "📋 Manual Test Steps:" -ForegroundColor Cyan
Write-Host "Step 1: Install and launch app" -ForegroundColor White
Write-Host "   - Verify normal geofence operation when location is ON" -ForegroundColor Gray
Write-Host ""
Write-Host "Step 2: Disable location services" -ForegroundColor White
Write-Host "   - Go to Settings > Location > Turn OFF" -ForegroundColor Gray
Write-Host "   - Or run: adb shell settings put secure location_providers_allowed ''" -ForegroundColor Gray
Write-Host ""
Write-Host "Step 3: Check app behavior" -ForegroundColor White
Write-Host "   - StatusFragment should show: 'Please turn on the location to gain access'" -ForegroundColor Gray
Write-Host "   - Camera apps should be blocked with notification" -ForegroundColor Gray
Write-Host ""
Write-Host "Step 4: Re-enable location services" -ForegroundColor White
Write-Host "   - Turn location back ON in settings" -ForegroundColor Gray
Write-Host "   - Or run: adb shell settings put secure location_providers_allowed 'gps,network'" -ForegroundColor Gray
Write-Host "   - Verify app returns to normal geofence checking" -ForegroundColor Gray
Write-Host ""

Write-Host "🔧 ADB Commands for Testing:" -ForegroundColor Cyan
Write-Host "Disable location:  adb shell settings put secure location_providers_allowed ''" -ForegroundColor Yellow
Write-Host "Enable location:   adb shell settings put secure location_providers_allowed 'gps,network'" -ForegroundColor Yellow
Write-Host "Check status:      adb shell settings get secure location_providers_allowed" -ForegroundColor Yellow
Write-Host "View app logs:     adb logcat | Select-String -Pattern '(StatusFragment|CameraAccessibilityService)'" -ForegroundColor Yellow
Write-Host ""

Write-Host "📱 Install/Launch Commands:" -ForegroundColor Cyan
Write-Host "Install APK:       adb install -r app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Yellow
Write-Host "Launch app:        adb shell am start -n com.example.geocamoff/.MainActivity" -ForegroundColor Yellow
Write-Host ""

$choice = Read-Host "Do you want to run automated location toggle test? (y/n)"
if ($choice -eq "y" -or $choice -eq "Y") {
    Write-Host ""
    Write-Host "🔄 Running automated test..." -ForegroundColor Green
    
    # Disable location
    Write-Host "⏱️  Disabling location services..." -ForegroundColor Yellow
    adb shell settings put secure location_providers_allowed ""
    Start-Sleep -Seconds 2
    
    $status = Get-LocationStatus
    Write-Host "📍 Location Status: $status" -ForegroundColor Yellow
    
    # Launch app to test
    Write-Host "🚀 Launching app..." -ForegroundColor Yellow
    adb shell am start -n com.example.geocamoff/.MainActivity
    
    Write-Host "✅ App launched with location services DISABLED" -ForegroundColor Green
    Write-Host "   Check the app - it should show 'Please turn on the location to gain access'" -ForegroundColor Gray
    Write-Host ""
    
    Read-Host "Press Enter when you've verified the disabled location message..."
    
    # Re-enable location
    Write-Host "⏱️  Re-enabling location services..." -ForegroundColor Yellow
    adb shell settings put secure location_providers_allowed "gps,network"
    Start-Sleep -Seconds 2
    
    $status = Get-LocationStatus
    Write-Host "📍 Location Status: $status" -ForegroundColor Yellow
    
    Write-Host "✅ Location services re-enabled" -ForegroundColor Green
    Write-Host "   The app should now return to normal geofence checking" -ForegroundColor Gray
    Write-Host ""
}

Write-Host "🎉 Location Services Test Setup Complete!" -ForegroundColor Green
Write-Host ""
Write-Host "💡 Key Points to Verify:" -ForegroundColor Cyan
Write-Host "• Location OFF → Always shows 'Please turn on location to gain access'" -ForegroundColor White
Write-Host "• Location OFF → Camera apps are blocked with alerts" -ForegroundColor White
Write-Host "• Location ON → Normal geofence behavior resumes" -ForegroundColor White
Write-Host "• Logs show 'Location services disabled - treating as restricted zone'" -ForegroundColor White
