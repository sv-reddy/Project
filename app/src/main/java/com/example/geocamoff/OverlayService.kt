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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("OverlayService", "onCreate called - showing notification only")
        
        // Just create notification, no overlay window to avoid crashes
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        Log.d("OverlayService", "Notification created successfully")
    }    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notification when camera is used in restricted zone"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }private fun createNotification(): android.app.Notification {
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
    }override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isForegroundMode = intent?.getBooleanExtra("foreground_mode", false) ?: false
        
        if (isForegroundMode) {
            Log.d("OverlayService", "Running in foreground mode - showing temporary notification")
            // Show a temporary notification and stop the service after a delay
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            
            // Stop the service after 5 seconds when app is in foreground
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    stopSelf()
                } catch (e: Exception) {
                    Log.w("OverlayService", "Error stopping foreground service: ${e.message}", e)
                }
            }, 5000)
            
            return START_NOT_STICKY
        }
        
        return START_STICKY
    }    override fun onDestroy() {
        Log.d("OverlayService", "onDestroy called")
        
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.d("OverlayService", "Foreground service stopped successfully")
        } catch (e: Exception) {
            Log.w("OverlayService", "Error stopping foreground: ${e.message}", e)
        }
        
        super.onDestroy()
    }
}
