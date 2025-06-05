package com.example.geocamoff

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val NOTIFICATION_ID = 1
        private var isServiceRunning = false
        private var notificationManager: NotificationManager? = null
    }

        override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("OverlayService", "onCreate called - isServiceRunning: $isServiceRunning")
        
        // If service is already running, don't recreate
        if (isServiceRunning) {
            Log.d("OverlayService", "Service already running, skipping onCreate")
            return
        }
        
        try {
            isServiceRunning = true
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            // Clean up any existing notifications first
            notificationManager?.cancel(NOTIFICATION_ID)
            
            // Create notification channel (safe to call multiple times)
            createNotificationChannel()
            
            // Only start as foreground service on API 26+ and if we have notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, createNotification())
                Log.d("OverlayService", "Started as foreground service")
            } else {
                // For older versions, just show notification without foreground service
                notificationManager?.notify(NOTIFICATION_ID, createNotification())
                Log.d("OverlayService", "Notification created for API < 26")
            }
            
            Log.d("OverlayService", "Notification created successfully")
        } catch (e: Exception) {            Log.e("OverlayService", "Error in onCreate: ${e.message}", e)
            // Clean up state and stop the service
            isServiceRunning = false
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val existingChannel = notificationManager?.getNotificationChannel(CHANNEL_ID)
                if (existingChannel == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Overlay Service Channel",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Shows notification when camera is used in restricted zone"
                        enableLights(true)
                        enableVibration(true)
                    }

                    notificationManager?.createNotificationChannel(channel)
                    Log.d("OverlayService", "Notification channel created")
                } else {
                    Log.d("OverlayService", "Notification channel already exists")
                }
            } catch (e: Exception) {
                Log.e("OverlayService", "Error creating notification channel: ${e.message}", e)
            }
        }
    }

    private fun createNotification(): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ Camera Alert")
            .setContentText("Camera detected in restricted zone - Please close camera app")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(false) // Allow dismissing the notification
            .setAutoCancel(true) // Auto dismiss when tapped
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("OverlayService", "onStartCommand called - isServiceRunning: $isServiceRunning")
        
        try {
            val isForegroundMode = intent?.getBooleanExtra("foreground_mode", false) ?: false
            
            // If service is already running and this is not foreground mode, just return
            if (isServiceRunning && !isForegroundMode) {
                Log.d("OverlayService", "Service already running, ignoring duplicate start")
                return START_STICKY
            }
            
            if (isForegroundMode) {
                Log.d("OverlayService", "Running in foreground mode - showing temporary notification")
                // Ensure notification manager is initialized
                if (notificationManager == null) {
                    notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                }
                
                // Show a temporary notification and stop the service after a delay
                createNotificationChannel()
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForeground(NOTIFICATION_ID, createNotification())
                } else {
                    notificationManager?.notify(NOTIFICATION_ID, createNotification())
                }
                
                // Stop the service after 5 seconds when app is in foreground
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        Log.d("OverlayService", "Auto-stopping foreground service after delay")
                        stopSelf()
                    } catch (e: Exception) {
                        Log.w("OverlayService", "Error stopping foreground service: ${e.message}", e)
                    }
                }, 5000)
                
                return START_NOT_STICKY
            }
            
            // Normal service mode
            if (!isServiceRunning) {
                onCreate() // Ensure service is properly initialized
            }
            
            return START_STICKY
        } catch (e: Exception) {
            Log.e("OverlayService", "Error in onStartCommand: ${e.message}", e)
            isServiceRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
    }    override fun onDestroy() {
        Log.d("OverlayService", "onDestroy called - cleaning up resources")
        
        try {
            isServiceRunning = false
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                Log.d("OverlayService", "Foreground service stopped successfully")
            } else {
                // For older versions, cancel the notification manually
                notificationManager?.cancel(NOTIFICATION_ID)
                Log.d("OverlayService", "Notification cancelled for API < 26")
            }
            
            // Clean up notification manager reference
            notificationManager = null
            
        } catch (e: Exception) {
            Log.w("OverlayService", "Error stopping foreground: ${e.message}", e)
        } finally {
            // Ensure the service state is reset even if cleanup fails
            isServiceRunning = false
            notificationManager = null
        }
        
        super.onDestroy()
        Log.d("OverlayService", "Service destroyed and cleaned up")
    }
}
