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
        private var notificationManager: NotificationManager? = null    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("OverlayService", "onCreate called - initializing service")
        
        try {
            isServiceRunning = true
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            // Clean up any existing notifications first
            notificationManager?.cancel(NOTIFICATION_ID)
            
            // Create notification channel (safe to call multiple times)
            createNotificationChannel()
            
            Log.d("OverlayService", "Service initialized successfully")
        } catch (e: Exception) {
            Log.e("OverlayService", "Error in onCreate: ${e.message}", e)
            // Clean up state and stop the service
            isServiceRunning = false
            try {
                stopSelf()
            } catch (stopE: Exception) {
                Log.w("OverlayService", "Error stopping service in onCreate catch: ${stopE.message}", stopE)
            }
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
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForeground(NOTIFICATION_ID, createNotification())
                    } else {
                        notificationManager?.notify(NOTIFICATION_ID, createNotification())
                    }
                } catch (e: Exception) {
                    Log.w("OverlayService", "Failed to start foreground in foreground mode: ${e.message}", e)
                    // Continue anyway, just log the error
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
            
            // Normal service mode - start as foreground service to prevent crashes
            Log.d("OverlayService", "Service started in normal mode")
            
            // Safely start as foreground service with proper error handling
            try {
                // Small delay to ensure service is properly initialized
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (isServiceRunning) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForeground(NOTIFICATION_ID, createNotification())
                                Log.d("OverlayService", "Started as foreground service (delayed)")
                            } else {
                                // For older versions, just show notification without foreground service
                                notificationManager?.notify(NOTIFICATION_ID, createNotification())
                                Log.d("OverlayService", "Notification created for API < 26 (delayed)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("OverlayService", "Failed to start foreground service (delayed): ${e.message}", e)
                    }
                }, 100) // Very small delay to ensure service is ready
            } catch (e: Exception) {
                Log.w("OverlayService", "Failed to schedule foreground service start: ${e.message}", e)
                // Don't crash the service if foreground start fails
            }
            
            return START_STICKY
        } catch (e: Exception) {
            Log.e("OverlayService", "Error in onStartCommand: ${e.message}", e)
            isServiceRunning = false
            try {
                stopSelf()
            } catch (stopE: Exception) {
                Log.w("OverlayService", "Error stopping service in onStartCommand catch: ${stopE.message}", stopE)
            }
            return START_NOT_STICKY
        }
    }override fun onDestroy() {
        Log.d("OverlayService", "onDestroy called - cleaning up resources")
        
        try {
            isServiceRunning = false
            
            // First try to stop foreground service properly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    Log.d("OverlayService", "Foreground service stopped successfully")
                } catch (e: Exception) {
                    Log.w("OverlayService", "Failed to stop foreground service cleanly: ${e.message}", e)
                    // Try alternative method
                    try {
                        stopForeground(true)
                        Log.d("OverlayService", "Foreground service stopped with fallback method")
                    } catch (e2: Exception) {
                        Log.w("OverlayService", "Fallback stop foreground also failed: ${e2.message}", e2)
                    }
                }
            } else {
                // For older versions, cancel the notification manually
                try {
                    notificationManager?.cancel(NOTIFICATION_ID)
                    Log.d("OverlayService", "Notification cancelled for API < 26")
                } catch (e: Exception) {
                    Log.w("OverlayService", "Failed to cancel notification: ${e.message}", e)
                }
            }
            
        } catch (e: Exception) {
            Log.w("OverlayService", "Error during cleanup: ${e.message}", e)
        } finally {
            // Ensure the service state is reset even if cleanup fails
            isServiceRunning = false
            notificationManager = null
            Log.d("OverlayService", "Service state reset")
        }
        
        try {
            super.onDestroy()
            Log.d("OverlayService", "Service destroyed successfully")
        } catch (e: Exception) {
            Log.w("OverlayService", "Error calling super.onDestroy(): ${e.message}", e)
        }
    }
}
