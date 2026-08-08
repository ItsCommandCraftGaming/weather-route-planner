package com.example.myapplication.application.interfaces

import com.example.myapplication.domain.entities.AlertaMeteo
import com.example.myapplication.domain.entities.RadarFrame
import com.mapbox.geojson.Point

interface IWeatherRepository {
    suspend fun getRainViewerUrl(): String?
    suspend fun checkWeatherForPoint(point: Point, timeMs: Long = System.currentTimeMillis()): AlertaMeteo?
    suspend fun getRainViewerFrames(): List<RadarFrame>
    suspend fun getRainbowSnapshot(apiKey: String): Long?
}
