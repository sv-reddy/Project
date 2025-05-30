package com.example.geocamoff

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GeofenceAdapter : RecyclerView.Adapter<GeofenceAdapter.GeofenceViewHolder>() {
    private val geofences = mutableListOf<GeofenceData>()

    class GeofenceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.text_name)
        val coordinatesText: TextView = view.findViewById(R.id.text_coordinates)
        val radiusText: TextView = view.findViewById(R.id.text_radius)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GeofenceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_geofence, parent, false)
        return GeofenceViewHolder(view)
    }

    override fun onBindViewHolder(holder: GeofenceViewHolder, position: Int) {
        val geofence = geofences[position]
        holder.nameText.text = geofence.name
        holder.coordinatesText.text = "Location: ${geofence.latitude}, ${geofence.longitude}"
        holder.radiusText.text = "Radius: ${geofence.radius} meters"
    }

    override fun getItemCount() = geofences.size

    fun addGeofence(geofence: GeofenceData) {
        geofences.add(geofence)
        notifyItemInserted(geofences.size - 1)
    }

    fun getGeofences(): List<GeofenceData> = geofences.toList()
}
