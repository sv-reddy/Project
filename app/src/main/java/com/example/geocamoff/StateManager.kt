package com.example.geocamoff

import android.content.Context
import android.content.Intent
import android.os.Build

object StateManager {
    private var isInRestrictedZone = false
    private var isCameraActive = false
    private var isAppInForeground = false
    private var isOverlayServiceRunning = false

    fun setAppForegroundState(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    fun updateGeofenceState(context: Context, inRestrictedZone: Boolean) {
        isInRestrictedZone = inRestrictedZone
        updateOverlayState(context)
    }

    fun updateCameraState(context: Context, cameraActive: Boolean) {
        isCameraActive = cameraActive
        updateOverlayState(context)
    }    private fun updateOverlayState(context: Context) {
        val shouldShowOverlay = isInRestrictedZone && isCameraActive
        
        android.util.Log.d("StateManager", "updateOverlayState: shouldShow=$shouldShowOverlay, isRunning=$isOverlayServiceRunning, inForeground=$isAppInForeground")
        
        // Don't show overlay if app is already in foreground
        if (isAppInForeground && shouldShowOverlay) {
            // Just show a notification instead of overlay when app is in foreground
            try {
                val notificationIntent = Intent(context, OverlayService::class.java)
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
        }
          // Handle overlay service lifecycle
        if (shouldShowOverlay && !isOverlayServiceRunning) {
            // Start service only if it's not already running
            try {
                val overlayIntent = Intent(context, OverlayService::class.java)
                
                if (Build.VERSION.SDK_INT >= 34) {
                    // On Android 14+, bring app to foreground before starting overlay
                    val activityIntent = Intent(context, MainActivity::class.java)
                    activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    activityIntent.putExtra("start_overlay", true)
                    context.startActivity(activityIntent)
                } else {
                    // Add a small delay when app is in background to prevent crash on immediate app closure
                    if (!isAppInForeground) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(overlayIntent)
                                } else {
                                    context.startService(overlayIntent)
                                }
                                isOverlayServiceRunning = true
                                android.util.Log.d("StateManager", "Started overlay service (delayed)")
                            } catch (e: Exception) {
                                android.util.Log.e("StateManager", "Error starting delayed overlay service: ${e.message}", e)
                            }
                        }, 500) // 500ms delay
                    } else {
                        // When app is in foreground, start immediately
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(overlayIntent)
                        } else {
                            context.startService(overlayIntent)
                        }
                        isOverlayServiceRunning = true
                    }
                }
                android.util.Log.d("StateManager", "Started overlay service")
            } catch (e: Exception) {
                android.util.Log.e("StateManager", "Error starting overlay service: ${e.message}", e)
            }
        } else if (!shouldShowOverlay && isOverlayServiceRunning) {
            // Stop service only if it's currently running
            try {
                val overlayIntent = Intent(context, OverlayService::class.java)
                context.stopService(overlayIntent)
                isOverlayServiceRunning = false
                android.util.Log.d("StateManager", "Stopped overlay service")
            } catch (e: Exception) {
                android.util.Log.w("StateManager", "Error stopping overlay service: ${e.message}", e)
            }
        }
    }
    
    // Method to reset service state (call this when app starts)
    fun resetServiceState() {
        isOverlayServiceRunning = false
        android.util.Log.d("StateManager", "Service state reset")
    }
}
