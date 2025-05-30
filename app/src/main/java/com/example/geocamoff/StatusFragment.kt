package com.example.geocamoff

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import android.content.SharedPreferences
import android.provider.Settings

class StatusFragment : Fragment() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var statusText: TextView
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_status, container, false)
        statusText = view.findViewById(R.id.status_text)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        prefs = requireContext().getSharedPreferences("geofences", Context.MODE_PRIVATE)
        // Check overlay permission
        if (!Settings.canDrawOverlays(requireContext())) {
            statusText.text = "Overlay permission required. Tap to grant."
            statusText.setOnClickListener {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = android.net.Uri.parse("package:" + requireContext().packageName)
                startActivity(intent)
            }
            return view
        }
        requestLocationUpdate()
        return view
    }

    private fun requestLocationUpdate() {
        if (!isAdded) return // Prevent crash if fragment is not attached
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Location permission not granted"
            return
        }
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isAdded) return // Prevent crash if fragment is not attached
                val location = result.lastLocation
                if (location != null) {
                    updateLocationUI(location)
                } else {
                    statusText.text = "Unable to get location"
                }
            }
        }, Looper.getMainLooper())
    }

    private fun updateLocationUI(location: Location) {
        if (!isAdded || activity == null) return // Prevent crash if fragment/activity is not attached
        val lat = location.latitude
        val lng = location.longitude
        val geofences = loadGeofencesFromPrefs()
        val inside = geofences.firstOrNull { isInsideGeofence(lat, lng, it) }
        try {            if (inside != null) {
                statusText.text = "Current Area: ${inside.name}\nStatus: RESTRICTED ZONE"
                StateManager.updateGeofenceState(requireContext(), true)
            } else {
                statusText.text = "Current Area: $lat, $lng\nStatus: Not Restricted"
                StateManager.updateGeofenceState(requireContext(), false)
            }
        } catch (e: Exception) {
            statusText.text = "Error updating overlay: ${e.localizedMessage}"
        }
    }

    private fun loadGeofencesFromPrefs(): List<GeofenceData> {
        val list = mutableListOf<GeofenceData>()
        val json = prefs.getString("geofence_list", null) ?: return list
        val arr = try { org.json.JSONArray(json) } catch (e: Exception) { null } ?: return list
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            try {
                list.add(
                    GeofenceData(
                        obj.getString("name"),
                        obj.getDouble("latitude"),
                        obj.getDouble("longitude"),
                        obj.getDouble("radius").toFloat(),
                        obj.optString("id", System.currentTimeMillis().toString())
                    )
                )
            } catch (_: Exception) {}
        }
        return list
    }

    private fun isInsideGeofence(lat: Double, lng: Double, geofence: GeofenceData): Boolean {
        val result = FloatArray(1)
        Location.distanceBetween(lat, lng, geofence.latitude, geofence.longitude, result)
        return result[0] <= geofence.radius
    }
}
