package com.example.geocamoff

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 100
        private const val REQUEST_CODE_OVERLAY = 102
    }

    private lateinit var startActivityForResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the activity result launcher
        startActivityForResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            // Handle overlay permission result
            if (Settings.canDrawOverlays(this)) {
                requestRequiredPermissions()
            } else {
                showPermissionDialogIfNeeded()
            }
        }
        setupNavigation()
        showPermissionDialogIfNeeded()
        // Add a delay and recheck permissions (for debugging)
        findViewById<View>(android.R.id.content).postDelayed({
            checkAllPermissions()
        }, 2000)
    }

    private fun checkAllPermissions() {
        val overlay = Settings.canDrawOverlays(this)
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val location = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

        Log.d("MainActivity", "=== Current Permission Status ===")
        Log.d("MainActivity", "Overlay: $overlay")
        Log.d("MainActivity", "Camera: $camera")
        Log.d("MainActivity", "Location: $location")
        Log.d("MainActivity", "Notification: $notification")
        Log.d("MainActivity", "Background Location: $backgroundLocation")
        Log.d("MainActivity", "All granted: ${overlay && camera && location && notification && backgroundLocation}")
    }

    private fun showPermissionDialogIfNeeded() {
        val needsOverlay = !Settings.canDrawOverlays(this)
        val needsCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        val needsLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED

        // Check additional permissions based on Android version
        val needsNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        } else false

        // Background location can only be requested if fine location is already granted
        val needsBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !needsLocation) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        } else false

        // Debug logging
        Log.d("MainActivity", "Permission Status:")
        Log.d("MainActivity", "Overlay: ${if (needsOverlay) "MISSING" else "GRANTED"}")
        Log.d("MainActivity", "Camera: ${if (needsCamera) "MISSING" else "GRANTED"}")
        Log.d("MainActivity", "Location: ${if (needsLocation) "MISSING" else "GRANTED"}")
        Log.d("MainActivity", "Notification: ${if (needsNotification) "MISSING" else "GRANTED"}")
        Log.d("MainActivity", "Background Location: ${if (needsBackgroundLocation) "MISSING" else "GRANTED"}")

        // If all essential permissions are granted, start the service directly
        // Note: Background location is not essential for basic functionality
        if (!needsOverlay && !needsCamera && !needsLocation && !needsNotification) {
            Log.d("MainActivity", "All essential permissions granted, starting service")
            startCameraDetectionService()

            // Request background location separately if needed
            if (needsBackgroundLocation) {
                requestBackgroundLocationLater()
            }
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_request, null)
        val message = dialogView.findViewById<TextView>(R.id.permission_message)
        val btnGrant = dialogView.findViewById<Button>(R.id.btn_grant)
        val needed = mutableListOf<String>()
        if (needsOverlay) needed.add("Display over other apps")
        if (needsCamera) needed.add("Camera")
        if (needsLocation) needed.add("Location")
        if (needsNotification) needed.add("Notifications")
        // Don't include background location in initial dialog - handle it separately

        // Move the message to resources
        message.text = getString(R.string.permission_dialog_message, needed.joinToString("\n"))

        // Show toast with missing permissions for debugging
        Toast.makeText(this, "Missing permissions: ${needed.joinToString(", ")}", Toast.LENGTH_LONG).show()
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        btnGrant.setOnClickListener {
            dialog.dismiss()
            if (needsOverlay) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
                startActivityForResultLauncher.launch(intent)
            } else {
                requestRequiredPermissions()
            }
        }

        dialog.show()
    }

    private fun setupNavigation() {
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_status -> {
                    openFragment(StatusFragment())
                    true
                }
                R.id.navigation_settings -> {
                    openFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
        // Default fragment
        openFragment(StatusFragment())
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )

        // Add Android 14+ specific permissions
        if (Build.VERSION.SDK_INT >= 34) {
            permissions.add("android.permission.FOREGROUND_SERVICE_CAMERA")
            permissions.add("android.permission.FOREGROUND_SERVICE_LOCATION")
        }

        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Don't add background location here - handle it separately after foreground location is granted

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            Log.d("MainActivity", "Requesting permissions: ${notGranted.joinToString(", ")}")
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        } else {
            Log.d("MainActivity", "All basic permissions granted, starting service")
            startCameraDetectionService()
            // Request background location separately if needed
            requestBackgroundLocationLater()
        }
    }

    private fun startCameraDetectionService() {
        startService(Intent(this, CameraDetectionService::class.java))
    }

    private fun requestBackgroundLocationLater() {
        // Request background location permission after a delay to ensure foreground location is established
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findViewById<View>(android.R.id.content).postDelayed({
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        REQUEST_CODE_PERMISSIONS + 1
                    )
                }
            }, 3000) // Wait 3 seconds before requesting background location
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d("MainActivity", "All basic permissions granted")
                    startCameraDetectionService()
                    // Request background location separately
                    requestBackgroundLocationLater()
                } else {
                    // Show rationale and re-request if denied
                    val denied = permissions.zip(grantResults.toTypedArray())
                        .filter { it.second != PackageManager.PERMISSION_GRANTED }
                        .map { it.first }
                    val rationale = denied.joinToString(", ") { perm ->
                        when (perm) {
                            Manifest.permission.CAMERA -> "Camera"
                            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION -> "Location"
                            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
                            else -> perm
                        }
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Permissions Required")
                        .setMessage("The following permissions are required for the app to work properly: $rationale.\nPlease grant them in the next steps.")
                        .setCancelable(false)
                        .setPositiveButton("Grant") { _, _ -> requestRequiredPermissions() }
                        .setNegativeButton("Exit") { _, _ -> finish() }
                        .show()
                }
            }
            REQUEST_CODE_PERMISSIONS + 1 -> {
                // Background location permission result
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "Background location permission granted")
                    Toast.makeText(this, "Background location access granted", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("MainActivity", "Background location permission denied")
                    Toast.makeText(this, "Background location denied - some features may be limited", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java. Use ActivityResultLauncher instead.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                requestRequiredPermissions()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Overlay Permission Required")
                    .setMessage("Overlay permission is required for warning displays. Please enable it in system settings.")
                    .setCancelable(false)
                    .setPositiveButton("Try Again") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
                        startActivityForResultLauncher.launch(intent)
                    }
                    .setNegativeButton("Exit") { _, _ -> finish() }
                    .show()
            }
        }
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        if (intent?.getBooleanExtra("start_overlay", false) == true) {
            startService(Intent(this, OverlayService::class.java))
            intent.removeExtra("start_overlay")
        }
    }
}