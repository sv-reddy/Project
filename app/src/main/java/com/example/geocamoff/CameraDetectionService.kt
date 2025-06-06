package com.example.geocamoff

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class CameraDetectionService : Service() {
    private var cameraManager: CameraManager? = null
    private var callback: CameraManager.AvailabilityCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val screenStateReceiver = ScreenStateReceiver()
    private var isReceiverRegistered = false

    companion object {
        private const val CHANNEL_ID = "camera_detection_channel"
        private const val NOTIFICATION_ID = 100
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_SERVICE" -> {
                Log.d("CameraDetection", "Received stop command, stopping self")
                stopSelf()
                return START_NOT_STICKY
            }
            "SCREEN_OFF_OPTIMIZATION" -> {
                Log.d("CameraDetection", "Optimizing for screen-off mode")
                optimizeForScreenOff()
                return START_STICKY
            }
            "USER_PRESENT_OPTIMIZATION" -> {
                Log.d("CameraDetection", "Optimizing for user presence")
                optimizeForUserPresent()
                return START_STICKY
            }
        }
        
        val isBootStart = intent?.action == "BOOT_START"
        
        Log.d("CameraDetection", "Service starting/restarting${if (isBootStart) " (after boot)" else ""}")
        
        // Register screen state receiver for power management
        registerScreenStateReceiver()
        
        // Acquire wake lock for background operation if screen is off
        acquireWakeLockIfNeeded()
        
        // Create persistent notification and start as foreground service if API level supports it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            val notification = createPersistentNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.d("CameraDetection", "Started as foreground service (API 26+)${if (isBootStart) " after boot" else ""}")
        } else {
            // For older API levels, just start monitoring without foreground notification
            Log.d("CameraDetection", "Started as regular service (API < 26)${if (isBootStart) " after boot" else ""}")
        }
          
        // Start camera monitoring
        startCameraDetection()
        
        // Return START_STICKY to ensure service restarts if killed
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera Detection Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors camera usage in restricted areas"
                enableLights(true)
                enableVibration(false)
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createPersistentNotification(): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Security Active")
            .setContentText("Monitoring camera usage in restricted areas")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true) // Makes notification persistent
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startCameraDetection() {
        try {
            cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            callback = object : CameraManager.AvailabilityCallback() {
                override fun onCameraUnavailable(cameraId: String) {
                    super.onCameraUnavailable(cameraId)
                    Log.d("CameraDetection", "Camera $cameraId unavailable (in use)")
                    StateManager.updateCameraState(this@CameraDetectionService, true)
                }
                
                override fun onCameraAvailable(cameraId: String) {
                    super.onCameraAvailable(cameraId)
                    Log.d("CameraDetection", "Camera $cameraId available (not in use)")
                    StateManager.updateCameraState(this@CameraDetectionService, false)
                }
            }
            
            callback?.let { 
                cameraManager?.registerAvailabilityCallback(it, null)
                Log.d("CameraDetection", "Camera callback registered successfully")
            }
        } catch (e: Exception) {
            Log.e("CameraDetection", "Error setting up camera detection: ${e.message}", e)
        }
    }

    private fun registerScreenStateReceiver() {
        try {
            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                registerReceiver(screenStateReceiver, filter)
                isReceiverRegistered = true
                Log.d("CameraDetection", "Screen state receiver registered")
            }
        } catch (e: Exception) {
            Log.e("CameraDetection", "Error registering screen state receiver: ${e.message}", e)
        }
    }

    private fun unregisterScreenStateReceiver() {
        try {
            if (isReceiverRegistered) {
                unregisterReceiver(screenStateReceiver)
                isReceiverRegistered = false
                Log.d("CameraDetection", "Screen state receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e("CameraDetection", "Error unregistering screen state receiver: ${e.message}", e)
        }
    }

    private fun acquireWakeLockIfNeeded() {
        try {
            if (!ScreenStateReceiver.isScreenOn() && (wakeLock == null || !wakeLock!!.isHeld)) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "GeoCamOff:CameraMonitoring"
                ).apply {
                    acquire(10 * 60 * 1000) // 10 minutes timeout
                    Log.d("CameraDetection", "Wake lock acquired for background monitoring")
                }
            }
        } catch (e: Exception) {
            Log.e("CameraDetection", "Error acquiring wake lock: ${e.message}", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    Log.d("CameraDetection", "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e("CameraDetection", "Error releasing wake lock: ${e.message}", e)
        }
    }

    private fun optimizeForScreenOff() {
        try {
            Log.d("CameraDetection", "Optimizing camera detection for screen-off mode")
            
            // Acquire wake lock for continued operation
            acquireWakeLockIfNeeded()
            
            // Ensure foreground service is running with high priority notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notification = createEnhancedNotification("Background monitoring active (screen off)")
                startForeground(NOTIFICATION_ID, notification)
            }
            
            // Re-register camera callback with enhanced monitoring
            restartCameraDetection()
            
        } catch (e: Exception) {
            Log.e("CameraDetection", "Error optimizing for screen off: ${e.message}", e)
        }
    }

    private fun optimizeForUserPresent() {
        try {
            Log.d("CameraDetection", "Optimizing camera detection for user presence")
            
            // Release wake lock since user is active
            releaseWakeLock()
            
            // Update notification to normal priority
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notification = createPersistentNotification()
                startForeground(NOTIFICATION_ID, notification)
            }
            
        } catch (e: Exception) {
            Log.e("CameraDetection", "Error optimizing for user present: ${e.message}", e)
        }
    }

    private fun restartCameraDetection() {
        try {
            // Stop existing monitoring
            callback?.let { cb ->
                cameraManager?.unregisterAvailabilityCallback(cb)
            }
            
            // Restart with fresh callback
            startCameraDetection()
            
            Log.d("CameraDetection", "Camera detection restarted")
        } catch (e: Exception) {
            Log.e("CameraDetection", "Error restarting camera detection: ${e.message}", e)
        }
    }

    private fun createEnhancedNotification(customText: String): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Security Active")
            .setContentText(customText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true) // Makes notification persistent
            .setPriority(NotificationCompat.PRIORITY_MAX) // Higher priority for screen-off
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("CameraDetection", "Service onCreate() called")        // Camera detection will be started in onStartCommand
    }

    override fun onDestroy() {
        Log.d("CameraDetection", "Service onDestroy() called")
        
        // Stop foreground service if running on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                Log.d("CameraDetection", "Foreground service stopped")
            } catch (e: Exception) {
                Log.w("CameraDetection", "Error stopping foreground service: ${e.message}", e)
            }
        }
        
        try {
            callback?.let { cb ->
                cameraManager?.unregisterAvailabilityCallback(cb)
                Log.d("CameraDetection", "Camera callback unregistered successfully")
            }
        } catch (e: Exception) {
            Log.e("CameraDetection", "Error during service cleanup: ${e.message}", e)
        } finally {
            callback = null
            cameraManager = null
        }
        
        // Unregister screen state receiver
        unregisterScreenStateReceiver()
        
        // Release wake lock if held
        releaseWakeLock()
        
        super.onDestroy()
    }
}
