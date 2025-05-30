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

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            callback = object : CameraManager.AvailabilityCallback() {                override fun onCameraUnavailable(cameraId: String) {
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
            cameraManager?.registerAvailabilityCallback(callback!!, null)
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cameraManager?.unregisterAvailabilityCallback(callback!!)
        }
        super.onDestroy()
    }
}
