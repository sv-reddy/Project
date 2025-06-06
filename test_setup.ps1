# Android Camera Detection App - Installation and Setup Test Script (PowerShell)
# This script helps verify that the accessibility service is properly configured

Write-Host "=== Android Camera Detection App - Setup Verification ===" -ForegroundColor Cyan
Write-Host ""

# Check if ADB is available
try {
    $adbVersion = adb version 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "ADB not found"
    }
    Write-Host "✅ ADB found" -ForegroundColor Green
} catch {
    Write-Host "❌ ADB not found. Please install Android SDK platform tools." -ForegroundColor Red
    exit 1
}

# Check if device is connected
Write-Host "🔍 Checking for connected Android device..." -ForegroundColor Yellow
$devices = adb devices 2>$null
$deviceCount = ($devices | Select-String "device$" | Measure-Object).Count

if ($deviceCount -eq 0) {
    Write-Host "❌ No Android device found. Please connect a device and enable USB debugging." -ForegroundColor Red
    exit 1
} else {
    Write-Host "✅ Android device connected" -ForegroundColor Green
}

# Get device info
Write-Host ""
Write-Host "📱 Device Information:" -ForegroundColor Cyan
$model = adb shell getprop ro.product.model
$androidVersion = adb shell getprop ro.build.version.release
$apiLevel = adb shell getprop ro.build.version.sdk
Write-Host "Model: $model" -ForegroundColor White
Write-Host "Android Version: $androidVersion" -ForegroundColor White
Write-Host "API Level: $apiLevel" -ForegroundColor White
Write-Host ""

# Install the APK if it exists
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apkPath) {
    Write-Host "📦 Installing APK..." -ForegroundColor Yellow
    adb install -r $apkPath
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ APK installed successfully" -ForegroundColor Green
    } else {
        Write-Host "❌ APK installation failed" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "⚠️  APK not found at $apkPath" -ForegroundColor Yellow
    Write-Host "   Please run: .\gradlew assembleDebug" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# Check if app is installed
Write-Host "🔍 Verifying app installation..." -ForegroundColor Yellow
$packageName = "com.example.geocamoff"
$packages = adb shell pm list packages
if ($packages -match $packageName) {
    Write-Host "✅ App package found: $packageName" -ForegroundColor Green
} else {
    Write-Host "❌ App package not found" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Launch the app
Write-Host "🚀 Launching the app..." -ForegroundColor Yellow
adb shell am start -n "$packageName/.MainActivity"

Write-Host ""
Write-Host "📋 Next Steps:" -ForegroundColor Cyan
Write-Host "1. Grant all permissions when prompted (Camera, Location, Notifications)" -ForegroundColor White
Write-Host "2. When the accessibility service dialog appears, tap 'Open Settings'" -ForegroundColor White
Write-Host "3. Find 'geocamoff' in the accessibility services list" -ForegroundColor White
Write-Host "4. Enable the service" -ForegroundColor White
Write-Host "5. Return to the app" -ForegroundColor White
Write-Host ""
Write-Host "🔍 To check accessibility service status:" -ForegroundColor Cyan
Write-Host "   adb shell settings get secure enabled_accessibility_services" -ForegroundColor Gray
Write-Host ""
Write-Host "📱 To view app logs:" -ForegroundColor Cyan
Write-Host "   adb logcat | Select-String -Pattern '(MainActivity|CameraAccessibilityService)'" -ForegroundColor Gray
Write-Host ""
Write-Host "✅ Setup verification complete!" -ForegroundColor Green
Write-Host ""
Write-Host "🧪 Testing Tips:" -ForegroundColor Cyan
Write-Host "- Open a camera app outside restricted areas (should work normally)" -ForegroundColor White
Write-Host "- Create a test restricted area and open camera inside it (should be blocked)" -ForegroundColor White
Write-Host "- Close the main app and test camera detection (should still work)" -ForegroundColor White
Write-Host "- Restart the device and test again (accessibility service should auto-start)" -ForegroundColor White
