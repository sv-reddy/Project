package com.example.geocamoff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * Broadcast receiver to handle screen state changes (ON/OFF)
 * Manages wake locks and optimizes background operation when screen is off
 */
class ScreenStateReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenStateReceiver"
        private var wakeLock: PowerManager.WakeLock? = null
        private var isScreenOn = true
        
        fun isScreenOn(): Boolean = isScreenOn
        
        fun releaseWakeLock() {
            try {
                wakeLock?.let { wl ->
                    if (wl.isHeld) {
                        wl.release()
                        Log.d(TAG, "Wake lock released")
                    }
                }
                wakeLock = null
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock: ${e.message}", e)
            }
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                handleScreenOff(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                handleScreenOn(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                // User unlocked the device
                handleUserPresent(context)
            }
        }
    }
    
    private fun handleScreenOff(context: Context) {
        isScreenOn = false
        Log.d(TAG, "Screen turned OFF - implementing background optimizations")
        
        try {
            // Acquire a partial wake lock to keep location services active
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "GeoCamOff:BackgroundMonitoring"
            ).apply {
                // Set timeout to prevent indefinite hold (30 minutes max)
                acquire(30 * 60 * 1000) // 30 minutes
                Log.d(TAG, "Partial wake lock acquired for background monitoring")
            }
            
            // Notify services about screen state change
            notifyServicesScreenOff(context)
            
            // Request location services to continue in background
            optimizeForScreenOff(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling screen off: ${e.message}", e)
        }
    }
    
    private fun handleScreenOn(context: Context) {
        isScreenOn = true
        Log.d(TAG, "Screen turned ON - resuming normal operation")
        
        try {
            // Release wake lock since screen is on
            releaseWakeLock()
            
            // Notify services about screen state change
            notifyServicesScreenOn(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling screen on: ${e.message}", e)
        }
    }
    
    private fun handleUserPresent(context: Context) {
        Log.d(TAG, "User present - device unlocked")
        
        try {
            // Ensure services are running optimally when user is active
            optimizeForUserPresent(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling user present: ${e.message}", e)
        }
    }
    
    private fun notifyServicesScreenOff(context: Context) {
        try {
            // Update StateManager
            StateManager.setScreenState(false)
            
            // Notify accessibility service to prepare for screen-off mode
            val intent = Intent(context, CameraAccessibilityService::class.java).apply {
                action = "SCREEN_OFF"
            }
            
            // Send broadcast to running services
            val serviceIntent = Intent().apply {
                action = "com.example.geocamoff.SCREEN_STATE_CHANGED"
                putExtra("screen_on", false)
            }
            context.sendBroadcast(serviceIntent)
            
            Log.d(TAG, "Services notified of screen OFF state")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying services of screen off: ${e.message}", e)
        }
    }
    
    private fun notifyServicesScreenOn(context: Context) {
        try {
            // Update StateManager
            StateManager.setScreenState(true)
            
            // Send broadcast to running services
            val serviceIntent = Intent().apply {
                action = "com.example.geocamoff.SCREEN_STATE_CHANGED"
                putExtra("screen_on", true)
            }
            context.sendBroadcast(serviceIntent)
            
            Log.d(TAG, "Services notified of screen ON state")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying services of screen on: ${e.message}", e)
        }
    }
    
    private fun optimizeForScreenOff(context: Context) {
        try {
            // Start enhanced background monitoring service
            val serviceIntent = Intent(context, CameraDetectionService::class.java).apply {
                action = "SCREEN_OFF_OPTIMIZATION"
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "Background monitoring optimized for screen-off mode")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing for screen off: ${e.message}", e)
        }
    }
    
    private fun optimizeForUserPresent(context: Context) {
        try {
            // Ensure all services are active and responsive
            val serviceIntent = Intent(context, CameraDetectionService::class.java).apply {
                action = "USER_PRESENT_OPTIMIZATION"
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "Services optimized for user presence")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing for user present: ${e.message}", e)
        }
    }
}
