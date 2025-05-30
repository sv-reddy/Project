package com.example.geocamoff

data class GeofenceData(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val id: String = System.currentTimeMillis().toString()
)
