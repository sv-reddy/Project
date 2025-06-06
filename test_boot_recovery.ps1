# Boot Recovery Test Script
# Tests that the app properly restores functionality after device restart

param(
    [string]$PackageName = "com.example.geocamoff",
    [string]$ApkPath = "app\debug\app-debug.apk",
    [switch]$SkipInstall,
    [switch]$WaitForManualSetup,
    [int]$BootWaitTimeout = 120
)

Write-Host "GeoCamOff Boot Recovery Test" -ForegroundColor Yellow
Write-Host "=============================" -ForegroundColor Yellow
Write-Host ""

# Function to check if device is connected
function Test-DeviceConnected {
    $devices = adb devices | Select-String "device$"
    return $devices.Count -gt 0
}

# Function to wait for device boot
function Wait-DeviceBoot {
    param([int]$TimeoutSeconds = 120)
    
    Write-Host "Waiting for device to boot completely..." -ForegroundColor Cyan
    $startTime = Get-Date
    
    do {
        Start-Sleep -Seconds 5
        $elapsed = ((Get-Date) - $startTime).TotalSeconds
        
        if ($elapsed -gt $TimeoutSeconds) {
            Write-Host "Timeout waiting for device boot!" -ForegroundColor Red
            return $false
        }
        
        $bootCompleted = adb shell getprop sys.boot_completed 2>$null
        $bootAnim = adb shell getprop init.svc.bootanim 2>$null
        
        Write-Host "Boot status: completed=$bootCompleted, animation=$bootAnim (${elapsed}s elapsed)" -ForegroundColor Gray
        
    } while ($bootCompleted -ne "1" -or $bootAnim -eq "running")
    
    # Additional wait for system to fully stabilize
    Write-Host "Boot completed, waiting for system stabilization..." -ForegroundColor Cyan
    Start-Sleep -Seconds 10
    
    return $true
}

# Function to check app permissions
function Test-AppPermissions {
    Write-Host "Checking app permissions..." -ForegroundColor Cyan
    
    $permissions = @(
        "android.permission.CAMERA",
        "android.permission.ACCESS_FINE_LOCATION", 
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.RECEIVE_BOOT_COMPLETED"
    )
    
    $allGranted = $true
    foreach ($perm in $permissions) {
        $result = adb shell pm list permissions -d -g | Select-String $perm
        if ($result) {
            $granted = adb shell pm list permissions -d -g | Select-String "$perm.*granted=true"
            if ($granted) {
                Write-Host "  ✓ $perm" -ForegroundColor Green
            } else {
                Write-Host "  ✗ $perm" -ForegroundColor Red
                $allGranted = $false
            }
        }
    }
    
    return $allGranted
}

# Function to check accessibility service
function Test-AccessibilityService {
    Write-Host "Checking accessibility service..." -ForegroundColor Cyan
    
    $enabledServices = adb shell settings get secure enabled_accessibility_services
    $isEnabled = $enabledServices -like "*$PackageName*"
    
    if ($isEnabled) {
        Write-Host "  ✓ Accessibility service is enabled" -ForegroundColor Green
        return $true
    } else {
        Write-Host "  ✗ Accessibility service is NOT enabled" -ForegroundColor Red
        return $false
    }
}

# Function to extract boot logs
function Get-BootLogs {
    Write-Host "Extracting boot-related logs..." -ForegroundColor Cyan
    
    Write-Host "Boot Receiver Logs:" -ForegroundColor Yellow
    $bootLogs = adb logcat -d | Select-String "BootReceiver"
    if ($bootLogs.Count -gt 0) {
        $bootLogs | ForEach-Object { Write-Host "  $($_.Line)" -ForegroundColor Gray }
    } else {
        Write-Host "  No BootReceiver logs found" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "StateManager Boot Logs:" -ForegroundColor Yellow
    $stateLogs = adb logcat -d | Select-String "StateManager.*boot"
    if ($stateLogs.Count -gt 0) {
        $stateLogs | ForEach-Object { Write-Host "  $($_.Line)" -ForegroundColor Gray }
    } else {
        Write-Host "  No StateManager boot logs found" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "CameraDetection Service Logs:" -ForegroundColor Yellow
    $serviceLogs = adb logcat -d | Select-String "CameraDetection.*boot"
    if ($serviceLogs.Count -gt 0) {
        $serviceLogs | ForEach-Object { Write-Host "  $($_.Line)" -ForegroundColor Gray }
    } else {
        Write-Host "  No CameraDetection boot logs found" -ForegroundColor Red
    }
}

# Function to test location services detection
function Test-LocationServicesDetection {
    Write-Host "Testing location services detection after boot..." -ForegroundColor Cyan
    
    # Clear logcat
    adb logcat -c
    
    # Disable location services
    Write-Host "  Disabling location services..." -ForegroundColor Gray
    adb shell settings put secure location_providers_allowed ""
    Start-Sleep -Seconds 2
    
    # Start the app
    Write-Host "  Starting app..." -ForegroundColor Gray
    adb shell am start -n "$PackageName/.MainActivity"
    Start-Sleep -Seconds 5
    
    # Check for location services disabled message
    $locationLogs = adb logcat -d | Select-String "location.*disabled|Please turn on the location"
    if ($locationLogs.Count -gt 0) {
        Write-Host "  ✓ Location services detection working" -ForegroundColor Green
        return $true
    } else {
        Write-Host "  ✗ Location services detection not working" -ForegroundColor Red
        return $false
    }
}

# Function to test camera blocking
function Test-CameraBlocking {
    Write-Host "Testing camera blocking after boot..." -ForegroundColor Cyan
    
    # Clear logcat
    adb logcat -c
    
    # Try to open camera app
    Write-Host "  Opening camera app..." -ForegroundColor Gray
    adb shell am start -a android.media.action.IMAGE_CAPTURE
    Start-Sleep -Seconds 3
    
    # Check for camera blocking logs
    $cameraLogs = adb logcat -d | Select-String "Camera.*blocked|Camera.*restricted"
    if ($cameraLogs.Count -gt 0) {
        Write-Host "  ✓ Camera blocking working" -ForegroundColor Green
        return $true
    } else {
        Write-Host "  ⚠ Camera blocking may not be working (check manually)" -ForegroundColor Yellow
        return $false
    }
}

# Main test execution
try {
    # Check device connection
    if (-not (Test-DeviceConnected)) {
        Write-Host "Error: No Android device connected!" -ForegroundColor Red
        Write-Host "Please connect a device and enable USB debugging." -ForegroundColor Yellow
        exit 1
    }
    
    Write-Host "Device connected successfully" -ForegroundColor Green
    
    # Install app if requested
    if (-not $SkipInstall) {
        if (Test-Path $ApkPath) {
            Write-Host "Installing app from $ApkPath..." -ForegroundColor Cyan
            adb install -r $ApkPath
            if ($LASTEXITCODE -ne 0) {
                Write-Host "App installation failed!" -ForegroundColor Red
                exit 1
            }
            Write-Host "App installed successfully" -ForegroundColor Green
        } else {
            Write-Host "APK file not found at $ApkPath" -ForegroundColor Red
            Write-Host "Please build the app first or use -SkipInstall" -ForegroundColor Yellow
            exit 1
        }
    }
    
    # Wait for manual setup if requested
    if ($WaitForManualSetup) {
        Write-Host ""
        Write-Host "Please manually:" -ForegroundColor Yellow
        Write-Host "1. Open the app and grant all permissions" -ForegroundColor Yellow
        Write-Host "2. Enable the accessibility service" -ForegroundColor Yellow
        Write-Host "3. Test that location detection works normally" -ForegroundColor Yellow
        Write-Host ""
        Read-Host "Press Enter when setup is complete and you want to test boot recovery"
    }
    
    # Store pre-boot state
    Write-Host ""
    Write-Host "Pre-Boot Verification" -ForegroundColor Yellow
    Write-Host "=====================" -ForegroundColor Yellow
    
    $preBootPermissions = Test-AppPermissions
    $preBootAccessibility = Test-AccessibilityService
    
    if (-not $preBootPermissions -or -not $preBootAccessibility) {
        Write-Host ""
        Write-Host "Warning: App not properly configured before boot test!" -ForegroundColor Red
        Write-Host "Boot receiver functionality may be limited." -ForegroundColor Yellow
        Write-Host ""
        
        if (-not $WaitForManualSetup) {
            $continue = Read-Host "Continue anyway? (y/N)"
            if ($continue -ne "y" -and $continue -ne "Y") {
                exit 1
            }
        }
    }
    
    # Reboot device
    Write-Host ""
    Write-Host "Rebooting Device" -ForegroundColor Yellow
    Write-Host "================" -ForegroundColor Yellow
    adb reboot
    
    # Wait for boot
    if (-not (Wait-DeviceBoot -TimeoutSeconds $BootWaitTimeout)) {
        Write-Host "Device boot timed out!" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Device boot completed successfully" -ForegroundColor Green
    
    # Post-boot analysis
    Write-Host ""
    Write-Host "Post-Boot Analysis" -ForegroundColor Yellow
    Write-Host "==================" -ForegroundColor Yellow
    
    # Extract and analyze logs
    Get-BootLogs
    
    Write-Host ""
    Write-Host "Post-Boot Verification" -ForegroundColor Yellow
    Write-Host "======================" -ForegroundColor Yellow
    
    # Check permissions (should be preserved)
    $postBootPermissions = Test-AppPermissions
    
    # Check accessibility service (should auto-restart)
    $postBootAccessibility = Test-AccessibilityService
    
    # Test functionality
    Write-Host ""
    Write-Host "Functionality Tests" -ForegroundColor Yellow
    Write-Host "===================" -ForegroundColor Yellow
    
    $locationTest = Test-LocationServicesDetection
    $cameraTest = Test-CameraBlocking
    
    # Summary
    Write-Host ""
    Write-Host "Boot Recovery Test Results" -ForegroundColor Yellow
    Write-Host "==========================" -ForegroundColor Yellow
    
    $results = @(
        @{ Name = "Permissions Preserved"; Result = $postBootPermissions },
        @{ Name = "Accessibility Service"; Result = $postBootAccessibility },
        @{ Name = "Location Detection"; Result = $locationTest },
        @{ Name = "Camera Blocking"; Result = $cameraTest }
    )
    
    $allPassed = $true
    foreach ($result in $results) {
        $status = if ($result.Result) { "✓ PASS" } else { "✗ FAIL"; $allPassed = $false }
        $color = if ($result.Result) { "Green" } else { "Red" }
        Write-Host "  $($result.Name): $status" -ForegroundColor $color
    }
    
    Write-Host ""
    if ($allPassed) {
        Write-Host "🎉 All boot recovery tests PASSED!" -ForegroundColor Green
        Write-Host "The app successfully restores functionality after device restart." -ForegroundColor Green
    } else {
        Write-Host "❌ Some boot recovery tests FAILED!" -ForegroundColor Red
        Write-Host "Check the logs above for details and troubleshooting information." -ForegroundColor Yellow
    }
    
    Write-Host ""
    Write-Host "Additional Commands for Manual Testing:" -ForegroundColor Cyan
    Write-Host "  Monitor logs: adb logcat | findstr /i `"bootreceiver statemanager cameradetection`"" -ForegroundColor Gray
    Write-Host "  Check services: adb shell dumpsys activity services $PackageName" -ForegroundColor Gray
    Write-Host "  Re-enable location: adb shell settings put secure location_providers_allowed `"gps,network`"" -ForegroundColor Gray
    
} catch {
    Write-Host ""
    Write-Host "Test execution error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
