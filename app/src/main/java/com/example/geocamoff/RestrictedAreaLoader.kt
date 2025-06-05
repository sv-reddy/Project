package com.example.geocamoff

import android.content.Context
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

object RestrictedAreaLoader {
    
    fun loadRestrictedAreas(context: Context): List<GeofenceData> {
        val geofences = mutableListOf<GeofenceData>()
        
        try {
            val inputStream = context.assets.open("restricted_areas.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonText = reader.use { it.readText() }
            
            val jsonArray = JSONArray(jsonText)
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                try {
                    val geofence = GeofenceData(
                        name = jsonObject.getString("name"),
                        latitude = jsonObject.getDouble("latitude"),
                        longitude = jsonObject.getDouble("longitude"),
                        radius = jsonObject.getDouble("radius").toFloat(),
                        id = jsonObject.getString("id")
                    )
                    geofences.add(geofence)
                } catch (e: Exception) {
                    // Skip malformed geofence entries
                    continue
                }
            }
        } catch (e: Exception) {
            // Return empty list if file cannot be read
            return emptyList()
        }
        
        return geofences
    }
}
