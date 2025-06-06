package com.example.geocamoff

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class CameraAccessibilityService : AccessibilityService() {    companion object {
        private var isServiceRunning = false
        
        fun isRunning(): Boolean = isServiceRunning
    }
    
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
      override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        
        Log.d("CameraAccessibilityService", "Service connected and running")
        
        // Configure the accessibility service
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        
        this.serviceInfo = info
        
        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        Log.d("CameraAccessibilityService", "Accessibility service configured for camera monitoring")
    }
      override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val packageName = it.packageName?.toString()
                    val className = it.className?.toString()
                    
                    Log.d("CameraAccessibilityService", "Window changed: $packageName - $className")
                    
                    // Check if camera-related app is opened
                    if (isCameraApp(packageName, className)) {
                        Log.w("CameraAccessibilityService", "Camera app detected: $packageName")
                        handleCameraDetection(packageName ?: "Unknown")
                    }
                }
                else -> {
                    // We only care about window state changes for camera detection
                    // All other accessibility events are ignored
                }
            }
        }
    }
      private fun isCameraApp(packageName: String?, className: String?): Boolean {
        val cameraPackages = listOf(
            "com.android.camera",
            "com.android.camera2",
            "com.sec.android.app.camera",
            "com.huawei.camera",
            "com.oneplus.camera",
            "com.xiaomi.camera",
            "org.codeaurora.snapcam",
            "com.motorola.camera",
            "com.sonyericsson.android.camera",
            "com.htc.camera",
            "com.lge.camera",
            "com.google.android.GoogleCamera",
            "com.oppo.camera",
            "com.vivo.camera",
            "com.miui.camera",
            "com.asus.camera",
            "com.honor.camera",
            "com.realme.camera",
            "com.nothing.camera",
            "com.tcl.camera",
            "com.infinix.camera"
        )
        
        val cameraClasses = listOf(
            "CameraActivity",
            "Camera2Activity", 
            "SecCameraActivity",
            "com.android.camera.CameraLauncher",
            "com.android.camera.VideoCamera",
            "com.android.camera.Camera",
            "CameraMainActivity",
            "SecureCamera",
            "CaptureActivity",
            "PhotoActivity",
            "VideoActivity"
        )

        // Check package name
        val packageMatch = cameraPackages.any { packageName?.contains(it, ignoreCase = true) == true }
        
        // Check class name
        val classMatch = cameraClasses.any { className?.contains(it, ignoreCase = true) == true }
        
        // Additional check for camera-related keywords in activity names
        val keywordMatch = packageName?.let { pkg ->
            pkg.contains("camera", ignoreCase = true) || 
            pkg.contains("photo", ignoreCase = true) ||
            pkg.contains("video", ignoreCase = true)
        } ?: false

        return packageMatch || classMatch || keywordMatch
    }
    
    private fun handleCameraDetection(appName: String) {
        serviceScope.launch {
            try {                val location = getCurrentLocation()
                if (location != null) {
                    val restrictedAreas = RestrictedAreaLoader.loadRestrictedAreas(this@CameraAccessibilityService)
                    val isInRestrictedArea = restrictedAreas.any { area ->
                        PolygonGeofenceUtils.isInsidePolygonGeofence(
                            LatLngPoint(location.latitude, location.longitude),                        area
                        )
                    }
                    
                    if (isInRestrictedArea) {
                        Log.w("CameraAccessibilityService", "Camera detected in restricted area!")
                        showCameraAlert(appName)
                        
                        // Multiple attempts to close the camera app
                        closeCameraApp()
                    } else {
                        Log.d("CameraAccessibilityService", "Camera detected but not in restricted area")
                    }
                } else {
                    Log.w("CameraAccessibilityService", "Could not get location for camera detection")
                }
            } catch (e: Exception) {
                Log.e("CameraAccessibilityService", "Error handling camera detection: ${e.message}", e)
            }
        }
    }
      private suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            // Check for location permission
            val fineLocationPermission = ContextCompat.checkSelfPermission(
                this@CameraAccessibilityService,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            val coarseLocationPermission = ContextCompat.checkSelfPermission(
                this@CameraAccessibilityService,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
            
            if (fineLocationPermission != PackageManager.PERMISSION_GRANTED && 
                coarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
                Log.w("CameraAccessibilityService", "Location permissions not granted")
                return@withContext null
            }
            
            val location = fusedLocationClient?.lastLocation?.await()
            if (location != null) {
                Log.d("CameraAccessibilityService", "Location obtained: ${location.latitude}, ${location.longitude}")
                return@withContext location
            } else {
                Log.w("CameraAccessibilityService", "No cached location available")
                return@withContext null
            }
        } catch (e: SecurityException) {
            Log.e("CameraAccessibilityService", "Security exception accessing location: ${e.message}", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error getting location: ${e.message}", e)
            return@withContext null
        }
    }
    
    private fun showCameraAlert(appName: String) {
        try {
            val notificationIntent = Intent(this, NotificationService::class.java).apply {
                putExtra("app_name", appName)
                putExtra("detected_by", "accessibility_service")            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(notificationIntent)
            } else {
                startService(notificationIntent)
            }
            Log.d("CameraAccessibilityService", "Camera alert notification started for app: $appName")
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error showing camera alert: ${e.message}", e)
        }
    }
    
    private suspend fun closeCameraApp() {
        try {
            Log.d("CameraAccessibilityService", "Attempting to close camera app...")
            
            // Method 1: Back button
            delay(500)
            performGlobalAction(GLOBAL_ACTION_BACK)
            Log.d("CameraAccessibilityService", "Pressed back button")
            
            // Method 2: Back button again (in case of confirmation dialog)
            delay(500)
            performGlobalAction(GLOBAL_ACTION_BACK)
            Log.d("CameraAccessibilityService", "Pressed back button again")
            
            // Method 3: Home button
            delay(500)
            performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d("CameraAccessibilityService", "Pressed home button")
            
            // Method 4: Recent apps and try to close
            delay(500)
            performGlobalAction(GLOBAL_ACTION_RECENTS)
            Log.d("CameraAccessibilityService", "Opened recent apps")
            
            // Give a moment then go home again
            delay(1000)
            performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d("CameraAccessibilityService", "Returned to home")
            
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error closing camera app: ${e.message}", e)
        }
    }
      override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        Log.d("CameraAccessibilityService", "Accessibility service destroyed")
    }
    
    override fun onInterrupt() {
        Log.d("CameraAccessibilityService", "Accessibility service interrupted")
    }
}

// Extension function to convert Task to suspending function
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.exception != null) {
                cont.resumeWith(Result.failure(task.exception!!))
            } else {
                cont.resumeWith(Result.success(task.result))
            }
        }
    }
}
