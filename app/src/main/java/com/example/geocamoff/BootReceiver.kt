package com.example.geocamoff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed - initializing app services")
              try {
                // Reset service state to ensure clean startup
                StateManager.resetServiceState()
                Log.d("BootReceiver", "Service state reset successfully")
                
                // Restore app state after boot
                StateManager.restoreAfterBoot(context)
                Log.d("BootReceiver", "App state restored after boot")
                
                // Check if we have required permissions before starting services
                if (hasRequiredPermissions(context)) {
                    Log.d("BootReceiver", "Required permissions available - starting services")
                    
                    // Start camera detection service
                    startCameraDetectionService(context)
                    
                    // The accessibility service should auto-restart after boot
                    // but we can verify its status
                    checkAccessibilityServiceStatus(context)
                    
                } else {
                    Log.w("BootReceiver", "Missing required permissions - services not started")
                }
                
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error during boot initialization: ${e.message}", e)
            }
        }
    }
    
    private fun hasRequiredPermissions(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            
            // Check essential permissions
            val cameraPermission = packageManager.checkPermission(
                android.Manifest.permission.CAMERA, packageName
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            val locationPermission = packageManager.checkPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION, packageName
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            // Notification permission is optional for older Android versions
            val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.checkPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS, packageName
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true // Not required for older versions
            }
            
            val hasPermissions = cameraPermission && locationPermission && notificationPermission
            Log.d("BootReceiver", "Permission check - Camera: $cameraPermission, Location: $locationPermission, Notification: $notificationPermission")
            
            hasPermissions
        } catch (e: Exception) {
            Log.e("BootReceiver", "Error checking permissions: ${e.message}", e)
            false
        }
    }
    
    private fun startCameraDetectionService(context: Context) {
        try {
            val serviceIntent = Intent(context, CameraDetectionService::class.java)
            serviceIntent.action = "BOOT_START"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                Log.d("BootReceiver", "CameraDetectionService started as foreground service")
            } else {
                context.startService(serviceIntent)
                Log.d("BootReceiver", "CameraDetectionService started as regular service")
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "Error starting CameraDetectionService: ${e.message}", e)
        }
    }
    
    private fun checkAccessibilityServiceStatus(context: Context) {
        try {
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            
            val isEnabled = enabledServices?.contains("${context.packageName}/.CameraAccessibilityService") == true
            Log.d("BootReceiver", "Accessibility service enabled: $isEnabled")
            
            if (!isEnabled) {
                Log.w("BootReceiver", "Accessibility service is not enabled - camera detection may not work properly")
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "Error checking accessibility service status: ${e.message}", e)
        }
    }
}
