package com.example.geocamoff

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class CameraAccessibilityService : AccessibilityService() {

    companion object {
        private var isServiceRunning = false
        
        fun isRunning(): Boolean = isServiceRunning
    }
    
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isReceiverRegistered = false
    
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("CameraAccessibilityService", "Screen turned off - optimizing for background")
                    StateManager.setScreenState(false)
                    optimizeForScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d("CameraAccessibilityService", "Screen turned on - optimizing for foreground")
                    StateManager.setScreenState(true)
                    optimizeForScreenOn()
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d("CameraAccessibilityService", "User present - full optimization")
                    StateManager.setScreenState(true)
                    optimizeForUserPresent()
                }
            }
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        
        Log.d("CameraAccessibilityService", "Service connected and running")
        
        // Check if this is after a boot by seeing if StateManager needs restoration
        try {
            // Ensure state is properly restored after potential boot
            StateManager.restoreAfterBoot(this)
            Log.d("CameraAccessibilityService", "State restoration check completed")
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error during state restoration: ${e.message}", e)
        }
        
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
        
        // Register screen state receiver for power management
        registerScreenStateReceiver()
        
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
            try {
                // First check if location services are enabled
                if (!isLocationServicesEnabled()) {
                    Log.w("CameraAccessibilityService", "Location services disabled - treating as restricted zone")
                    showCameraAlert(appName)
                    closeCameraApp()
                    return@launch
                }
                
                val location = getCurrentLocation()
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
                    Log.w("CameraAccessibilityService", "Could not get location for camera detection - treating as restricted zone")
                    showCameraAlert(appName)
                    closeCameraApp()
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
    }    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        
        // Unregister screen state receiver
        unregisterScreenStateReceiver()
        
        // Release wake lock if held
        releaseWakeLock()
        
        // Stop background location updates
        stopBackgroundLocationUpdates()
        
        Log.d("CameraAccessibilityService", "Accessibility service destroyed")
    }
    
    override fun onInterrupt() {
        Log.d("CameraAccessibilityService", "Accessibility service interrupted")
    }
      private fun isLocationServicesEnabled(): Boolean {
        return try {
            val locationManager = ContextCompat.getSystemService(this, LocationManager::class.java)
            locationManager?.let { lm ->
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || 
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } ?: false
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error checking location services: ${e.message}", e)
            false
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
                Log.d("CameraAccessibilityService", "Screen state receiver registered")
            }
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error registering screen state receiver: ${e.message}", e)
        }
    }
    
    private fun unregisterScreenStateReceiver() {
        try {
            if (isReceiverRegistered) {
                unregisterReceiver(screenStateReceiver)
                isReceiverRegistered = false
                Log.d("CameraAccessibilityService", "Screen state receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error unregistering screen state receiver: ${e.message}", e)
        }
    }
    
    private fun optimizeForScreenOff() {
        try {
            Log.d("CameraAccessibilityService", "Optimizing accessibility service for screen-off mode")
            
            // Acquire wake lock for continued operation
            acquireWakeLockIfNeeded()
            
            // Request background location updates if needed
            requestBackgroundLocationUpdates()
            
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error optimizing for screen off: ${e.message}", e)
        }
    }
    
    private fun optimizeForScreenOn() {
        try {
            Log.d("CameraAccessibilityService", "Optimizing accessibility service for screen-on mode")
            
            // Can reduce wake lock usage since user is potentially active
            // but keep some background capability
            
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error optimizing for screen on: ${e.message}", e)
        }
    }
    
    private fun optimizeForUserPresent() {
        try {
            Log.d("CameraAccessibilityService", "Optimizing accessibility service for user presence")
            
            // Release wake lock since user is active
            releaseWakeLock()
            
            // Stop background location updates
            stopBackgroundLocationUpdates()
            
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error optimizing for user present: ${e.message}", e)
        }
    }
    
    private fun acquireWakeLockIfNeeded() {
        try {
            if (wakeLock == null || !wakeLock!!.isHeld) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "GeoCamOff:AccessibilityService"
                ).apply {
                    acquire(15 * 60 * 1000) // 15 minutes timeout
                    Log.d("CameraAccessibilityService", "Wake lock acquired for background operation")
                }
            }
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error acquiring wake lock: ${e.message}", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    Log.d("CameraAccessibilityService", "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error releasing wake lock: ${e.message}", e)
        }
    }
    
    private fun requestBackgroundLocationUpdates() {
        try {
            // Request more frequent location updates for better accuracy when screen is off
            val locationRequest = LocationRequest.create().apply {
                interval = 30000 // 30 seconds
                fastestInterval = 15000 // 15 seconds
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient?.requestLocationUpdates(locationRequest, object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        // Update location cache for better camera detection
                        Log.d("CameraAccessibilityService", "Background location update received")
                    }
                }, null)
                Log.d("CameraAccessibilityService", "Background location updates requested")
            }
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error requesting background location updates: ${e.message}", e)
        }
    }
    
    private fun stopBackgroundLocationUpdates() {
        try {
            fusedLocationClient?.removeLocationUpdates(object : LocationCallback() {})
            Log.d("CameraAccessibilityService", "Background location updates stopped")
        } catch (e: Exception) {
            Log.e("CameraAccessibilityService", "Error stopping background location updates: ${e.message}", e)        }
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
