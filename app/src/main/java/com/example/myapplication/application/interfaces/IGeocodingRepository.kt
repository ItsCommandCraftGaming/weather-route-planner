package com.example.myapplication.application.interfaces

import com.mapbox.geojson.Point

interface IGeocodingRepository {
    suspend fun searchLocation(query: String): Point?
}
