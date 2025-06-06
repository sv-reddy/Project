#!/usr/bin/env bash

# Android Camera Detection App - Installation and Setup Test Script
# This script helps verify that the accessibility service is properly configured

echo "=== Android Camera Detection App - Setup Verification ==="
echo ""

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "❌ ADB not found. Please install Android SDK platform tools."
    exit 1
fi

# Check if device is connected
echo "🔍 Checking for connected Android device..."
DEVICE_COUNT=$(adb devices | grep -c "device$")

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "❌ No Android device found. Please connect a device and enable USB debugging."
    exit 1
else
    echo "✅ Android device connected"
fi

# Get device info
echo ""
echo "📱 Device Information:"
echo "Model: $(adb shell getprop ro.product.model)"
echo "Android Version: $(adb shell getprop ro.build.version.release)"
echo "API Level: $(adb shell getprop ro.build.version.sdk)"
echo ""

# Install the APK if it exists
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "📦 Installing APK..."
    adb install -r "$APK_PATH"
    if [ $? -eq 0 ]; then
        echo "✅ APK installed successfully"
    else
        echo "❌ APK installation failed"
        exit 1
    fi
else
    echo "⚠️  APK not found at $APK_PATH"
    echo "   Please run: ./gradlew assembleDebug"
    exit 1
fi

echo ""

# Check if app is installed
echo "🔍 Verifying app installation..."
PACKAGE_NAME="com.example.geocamoff"
if adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
    echo "✅ App package found: $PACKAGE_NAME"
else
    echo "❌ App package not found"
    exit 1
fi

echo ""

# Launch the app
echo "🚀 Launching the app..."
adb shell am start -n "$PACKAGE_NAME/.MainActivity"

echo ""
echo "📋 Next Steps:"
echo "1. Grant all permissions when prompted (Camera, Location, Notifications)"
echo "2. When the accessibility service dialog appears, tap 'Open Settings'"
echo "3. Find 'geocamoff' in the accessibility services list"
echo "4. Enable the service"
echo "5. Return to the app"
echo ""
echo "🔍 To check accessibility service status:"
echo "   adb shell settings get secure enabled_accessibility_services"
echo ""
echo "📱 To view app logs:"
echo "   adb logcat | grep -E '(MainActivity|CameraAccessibilityService)'"
echo ""
echo "✅ Setup verification complete!"
echo ""
echo "🧪 Testing Tips:"
echo "- Open a camera app outside restricted areas (should work normally)"
echo "- Create a test restricted area and open camera inside it (should be blocked)"
echo "- Close the main app and test camera detection (should still work)"
echo "- Restart the device and test again (accessibility service should auto-start)"
