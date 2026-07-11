package com.example.myapplication.data.interfaces

import com.example.myapplication.model.AlertaMeteo
import com.mapbox.geojson.Point

interface IWeatherRepository {
    suspend fun getRainViewerUrl(): String?
    suspend fun checkWeatherForPoint(point: Point, timeMs: Long = System.currentTimeMillis()): AlertaMeteo?
}
