package com.example.geocamoff

import android.app.Service
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi

class CameraDetectionService : Service() {
    private var cameraManager: CameraManager? = null
    private var callback: CameraManager.AvailabilityCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("CameraDetection", "Service onStartCommand() called")
        return START_NOT_STICKY // Don't restart if killed
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("CameraDetection", "Service onCreate() called")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
        } else {
            Log.w("CameraDetection", "Camera2 API not available on this device (API < 21)")
        }
    }    override fun onDestroy() {
        Log.d("CameraDetection", "Service onDestroy() called")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                callback?.let { cb ->
                    cameraManager?.unregisterAvailabilityCallback(cb)
                    Log.d("CameraDetection", "Camera callback unregistered successfully")
                }
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
