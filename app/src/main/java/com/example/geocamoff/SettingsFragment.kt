package com.example.geocamoff

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment() {
    private lateinit var adapter: GeofenceAdapter
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        geofencingClient = LocationServices.getGeofencingClient(requireActivity())
        prefs = requireContext().getSharedPreferences("geofences", Context.MODE_PRIVATE)

        // Setup RecyclerView and Adapter FIRST
        val recyclerView = view.findViewById<RecyclerView>(R.id.geofence_list)
        adapter = GeofenceAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Load saved geofences AFTER adapter is initialized
        loadGeofences()

        // Setup Add Geofence button
        view.findViewById<MaterialButton>(R.id.add_geofence_btn)?.setOnClickListener {
            showAddGeofenceDialog()
        }

        return view
    }

    private fun loadGeofences() {
        val json = prefs.getString("geofence_list", null) ?: return
        val list = try {
            org.json.JSONArray(json)
        } catch (e: Exception) { null } ?: return
        for (i in 0 until list.length()) {
            val obj = list.getJSONObject(i)
            try {
                val geofence = GeofenceData(
                    obj.getString("name"),
                    obj.getDouble("latitude"),
                    obj.getDouble("longitude"),
                    obj.getDouble("radius").toFloat(),
                    obj.optString("id", System.currentTimeMillis().toString())
                )
                adapter.addGeofence(geofence)
            } catch (e: Exception) {
                // Skip malformed geofence
                continue
            }
        }
    }

    private fun addGeofenceToSystem(geofence: GeofenceData) {
        val geofenceObj = Geofence.Builder()
            .setRequestId(geofence.id)
            .setCircularRegion(geofence.latitude, geofence.longitude, geofence.radius)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofenceObj)
            .build()
        val intent = Intent(requireContext(), GeofenceBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        geofencingClient.addGeofences(request, pendingIntent)
    }

    private fun saveGeofences() {
        val arr = org.json.JSONArray()
        for (g in adapter.getGeofences()) {
            val obj = org.json.JSONObject()
            obj.put("name", g.name)
            obj.put("latitude", g.latitude)
            obj.put("longitude", g.longitude)
            obj.put("radius", g.radius)
            obj.put("id", g.id)
            arr.put(obj)
        }
        prefs.edit().putString("geofence_list", arr.toString()).apply()
    }

    private fun showAddGeofenceDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_geofence, null)

        val nameEdit = dialogView.findViewById<TextInputEditText>(R.id.edit_name)
        val latitudeEdit = dialogView.findViewById<TextInputEditText>(R.id.edit_latitude)
        val longitudeEdit = dialogView.findViewById<TextInputEditText>(R.id.edit_longitude)
        val radiusEdit = dialogView.findViewById<TextInputEditText>(R.id.edit_radius)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Restricted Zone")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                try {
                    val name = nameEdit.text.toString()
                    val latitude = latitudeEdit.text.toString().toDouble()
                    val longitude = longitudeEdit.text.toString().toDouble()
                    val radius = radiusEdit.text.toString().toFloat()

                    if (name.isBlank()) throw IllegalArgumentException("Name cannot be empty")
                    if (radius <= 0) throw IllegalArgumentException("Radius must be positive")

                    val geofence = GeofenceData(name, latitude, longitude, radius)
                    adapter.addGeofence(geofence)
                    addGeofenceToSystem(geofence)
                    saveGeofences()
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, "Please enter valid numbers", Toast.LENGTH_SHORT).show()
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
