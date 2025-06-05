package com.example.geocamoff

import android.content.Context
import android.content.Intent
import android.os.Build

object StateManager {
    private var isInRestrictedZone = false
    private var isCameraActive = false
    private var isAppInForeground = false

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
    }

    private fun updateOverlayState(context: Context) {
        val shouldShowOverlay = isInRestrictedZone && isCameraActive
        
        // Don't show overlay if app is already in foreground
        if (isAppInForeground && shouldShowOverlay) {
            // Just show a notification instead of overlay when app is in foreground
            val notificationIntent = Intent(context, OverlayService::class.java)
            notificationIntent.putExtra("foreground_mode", true)
            context.startService(notificationIntent)
            return
        }
        
        val overlayIntent = Intent(context, OverlayService::class.java)
        if (shouldShowOverlay) {
            if (Build.VERSION.SDK_INT >= 34) {
                // On Android 14+, bring app to foreground before starting overlay
                val activityIntent = Intent(context, MainActivity::class.java)
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                activityIntent.putExtra("start_overlay", true)
                context.startActivity(activityIntent)
            } else {
                context.startService(overlayIntent)
            }
        } else {
            context.stopService(overlayIntent)
        }
    }
}
