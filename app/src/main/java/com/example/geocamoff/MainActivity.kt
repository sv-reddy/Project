package com.example.geocamoff

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 100
        private const val REQUEST_CODE_OVERLAY = 102
    }

    private enum class PermissionType {
        OVERLAY,
        NOTIFICATION,
        BACKGROUND_LOCATION,
        RUNTIME
    }

    private lateinit var startActivityForResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the activity result launcher
        startActivityForResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // Handle overlay permission result
            if (Settings.canDrawOverlays(this)) {
                requestRequiredPermissions()
            } else {
                showPermissionDialogIfNeeded()
            }
        }

        setupNavigation()
        showPermissionDialogIfNeeded()
    }

    private fun showPermissionDialogIfNeeded() {        val needsOverlay = !Settings.canDrawOverlays(this)
        val needsCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        val needsLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        
        if (!needsOverlay && !needsCamera && !needsLocation) {
            requestRequiredPermissions()
            return
        }
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_request, null)
        val message = dialogView.findViewById<TextView>(R.id.permission_message)
        val btnGrant = dialogView.findViewById<Button>(R.id.btn_grant)
        val needed = mutableListOf<String>()
        if (needsOverlay) needed.add("Display over other apps")
        if (needsCamera) needed.add("Camera")
        if (needsLocation) needed.add("Location")
        message.text = "This app needs the following permissions to function:\n\n${needed.joinToString("\n")}\n\nPlease grant them in the next steps."
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnGrant.setOnClickListener {
            dialog.dismiss()
            if (needsOverlay) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
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

    private fun requestRequiredPermissions() {        val permissions = mutableListOf(
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

        // Add background location for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        } else {
            startCameraDetectionService()
        }
    }

    private fun startCameraDetectionService() {
        startService(Intent(this, CameraDetectionService::class.java))
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
                    startCameraDetectionService()
                } else {
                    // Show rationale and re-request if denied
                    val denied = permissions.zip(grantResults.toTypedArray())
                        .filter { it.second != PackageManager.PERMISSION_GRANTED }
                        .map { it.first }
                    val rationale = denied.joinToString(", ") { perm ->
                        when (perm) {
                            Manifest.permission.CAMERA -> "Camera"
                            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Location"
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
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OVERLAY) {            if (Settings.canDrawOverlays(this)) {
                requestRequiredPermissions()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Overlay Permission Required")
                    .setMessage("Overlay permission is required for warning displays. Please enable it in system settings.")
                    .setCancelable(false)
                    .setPositiveButton("Try Again") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
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