package com.example.myapplication.data.interfaces

import com.example.myapplication.model.TomTomIncident

interface ITomTomIncidentRepository {
    suspend fun getIncidents(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        apiKey: String
    ): List<TomTomIncident>
}
