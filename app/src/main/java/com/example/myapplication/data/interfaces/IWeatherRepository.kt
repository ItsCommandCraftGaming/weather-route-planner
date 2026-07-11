package com.example.myapplication.data.interfaces

import com.example.myapplication.model.AlertaMeteo
import com.mapbox.geojson.Point

data class RadarFrame(
    val time: Long,
    val url: String
)

interface IWeatherRepository {
    suspend fun getRainViewerUrl(): String?
    suspend fun checkWeatherForPoint(point: Point, timeMs: Long = System.currentTimeMillis()): AlertaMeteo?
    suspend fun getRainViewerFrames(): List<RadarFrame>
}
