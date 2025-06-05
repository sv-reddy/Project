package com.example.geocamoff

import android.Manifest
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
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import android.provider.Settings

class StatusFragment : Fragment() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var statusText: TextView
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_status, container, false)
        statusText = view.findViewById(R.id.status_text)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        if (!Settings.canDrawOverlays(requireContext())) {
            statusText.text = getString(R.string.overlay_permission_required)
            statusText.setOnClickListener {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = "package:${requireContext().packageName}".toUri()
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
            statusText.text = getString(R.string.location_permission_not_granted)
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
                    statusText.text = getString(R.string.unable_to_get_location)
                }
            }
        }, Looper.getMainLooper())
    }

    private fun updateLocationUI(location: Location) {
        if (!isAdded || activity == null) return // Prevent crash if fragment/activity is not attached
        
        val currentPoint = LatLngPoint(location.latitude, location.longitude)
        val polygonGeofences = RestrictedAreaLoader.loadRestrictedAreas(requireContext())
        val inside = polygonGeofences.firstOrNull { geofence ->
            PolygonGeofenceUtils.isInsidePolygonGeofence(currentPoint, geofence)
        }
        
        try {
            if (inside != null) {
                statusText.text = getString(R.string.status_restricted_zone, inside.name)
                StateManager.updateGeofenceState(requireContext(), true)
            } else {
                statusText.text = getString(R.string.status_not_restricted, currentPoint.latitude, currentPoint.longitude)
                StateManager.updateGeofenceState(requireContext(), false)
            }
        } catch (e: Exception) {
            statusText.text = getString(R.string.status_error, e.localizedMessage ?: "Unknown error")
        }
    }
}
