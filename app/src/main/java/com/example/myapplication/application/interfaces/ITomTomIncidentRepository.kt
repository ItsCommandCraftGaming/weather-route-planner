package com.example.myapplication.application.interfaces

import com.example.myapplication.domain.entities.TomTomIncident

interface ITomTomIncidentRepository {
    suspend fun getIncidents(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        apiKey: String
    ): List<TomTomIncident>
}
