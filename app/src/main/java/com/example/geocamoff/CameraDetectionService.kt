package com.example.geocamoff

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class CameraDetectionService : Service() {
    private var cameraManager: CameraManager? = null
    private var callback: CameraManager.AvailabilityCallback? = null

    companion object {
        private const val CHANNEL_ID = "camera_detection_channel"
        private const val NOTIFICATION_ID = 100
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            Log.d("CameraDetection", "Received stop command, stopping self")
            stopSelf()
            return START_NOT_STICKY
        }
        
        Log.d("CameraDetection", "Service starting/restarting")
        
        // Create persistent notification and start as foreground service if API level supports it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            val notification = createPersistentNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.d("CameraDetection", "Started as foreground service (API 26+)")
        } else {
            // For older API levels, just start monitoring without foreground notification
            Log.d("CameraDetection", "Started as regular service (API < 26)")
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
            notificationManager?.createNotificationChannel(channel)        }
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
        
        super.onDestroy()
    }
}
