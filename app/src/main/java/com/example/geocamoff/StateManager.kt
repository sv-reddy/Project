package com.example.geocamoff

import android.content.Context
import android.content.Intent
import android.os.Build

object StateManager {
    private var isInRestrictedZone = false
    private var isCameraActive = false
    private var isAppInForeground = false
    private var isNotificationServiceRunning = false
    private var isScreenOn = true // New: Track screen state

    fun setAppForegroundState(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    fun setScreenState(screenOn: Boolean) {
        isScreenOn = screenOn
        android.util.Log.d("StateManager", "Screen state changed: ${if (screenOn) "ON" else "OFF"}")
        // Screen state changes can affect background processing behavior
    }

    fun isScreenOn(): Boolean = isScreenOn

    fun updateGeofenceState(context: Context, inRestrictedZone: Boolean) {
        isInRestrictedZone = inRestrictedZone
        updateNotificationState(context)
    }

    fun updateCameraState(context: Context, cameraActive: Boolean) {
        isCameraActive = cameraActive
        updateNotificationState(context)
    }    private fun updateNotificationState(context: Context) {
        val shouldShowNotification = isInRestrictedZone && isCameraActive
        
        android.util.Log.d("StateManager", "updateNotificationState: shouldShow=$shouldShowNotification, isRunning=$isNotificationServiceRunning, inForeground=$isAppInForeground")
          // Don't show notification if app is already in foreground
        if (isAppInForeground && shouldShowNotification) {
            // Just show a notification when app is in foreground
            try {
                val notificationIntent = Intent(context, NotificationService::class.java)
                notificationIntent.putExtra("foreground_mode", true)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(notificationIntent)
                } else {
                    context.startService(notificationIntent)
                }
                android.util.Log.d("StateManager", "Started foreground notification service")
            } catch (e: Exception) {
                android.util.Log.e("StateManager", "Error starting foreground notification: ${e.message}", e)
            }
            return
        }        // Handle notification service lifecycle
        if (shouldShowNotification && !isNotificationServiceRunning) {
            // Start service only if it's not already running
            try {
                val notificationIntent = Intent(context, NotificationService::class.java)
                
                if (Build.VERSION.SDK_INT >= 34) {
                    // On Android 14+, bring app to foreground before starting notification
                    val activityIntent = Intent(context, MainActivity::class.java)
                    activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    activityIntent.putExtra("start_notification", true)
                    context.startActivity(activityIntent)
                } else {
                    // Add a small delay when app is in background to prevent crash on immediate app closure
                    if (!isAppInForeground) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(notificationIntent)
                                } else {
                                    context.startService(notificationIntent)
                                }
                                isNotificationServiceRunning = true
                                android.util.Log.d("StateManager", "Started notification service (delayed)")
                            } catch (e: Exception) {
                                android.util.Log.e("StateManager", "Error starting delayed notification service: ${e.message}", e)
                            }
                        }, 500) // 500ms delay
                    } else {
                        // When app is in foreground, start immediately
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(notificationIntent)
                        } else {
                            context.startService(notificationIntent)
                        }
                        isNotificationServiceRunning = true
                    }
                }
                android.util.Log.d("StateManager", "Started notification service")
            } catch (e: Exception) {
                android.util.Log.e("StateManager", "Error starting notification service: ${e.message}", e)
            }
        } else if (!shouldShowNotification && isNotificationServiceRunning) {
            // Stop service only if it's currently running
            try {
                val notificationIntent = Intent(context, NotificationService::class.java)
                context.stopService(notificationIntent)
                isNotificationServiceRunning = false
                android.util.Log.d("StateManager", "Stopped notification service")
            } catch (e: Exception) {
                android.util.Log.w("StateManager", "Error stopping notification service: ${e.message}", e)
            }
        }
    }
      // Method to reset service state (call this when app starts)
    fun resetServiceState() {
        isNotificationServiceRunning = false
        android.util.Log.d("StateManager", "Service state reset")
    }
    
    // Method to restore state after boot (called by BootReceiver)
    fun restoreAfterBoot(context: Context) {
        try {
            android.util.Log.d("StateManager", "Restoring state after boot")
            
            // Reset all states to safe defaults
            isInRestrictedZone = false
            isCameraActive = false
            isAppInForeground = false
            isNotificationServiceRunning = false
            
            // Check current location services status and treat as restricted if disabled
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                                   locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            
            if (!isLocationEnabled) {
                android.util.Log.d("StateManager", "Location services disabled after boot - treating as restricted zone")
                isInRestrictedZone = true
            }
            
            android.util.Log.d("StateManager", "State restored after boot - Location services: $isLocationEnabled, In restricted zone: $isInRestrictedZone")
        } catch (e: Exception) {
            android.util.Log.e("StateManager", "Error restoring state after boot: ${e.message}", e)
        }
    }
}
