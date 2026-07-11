package com.example.myapplication.data.interfaces

import com.example.myapplication.model.AlertaMeteo
import com.mapbox.geojson.LineString

interface IRouteWeatherScanner {
    suspend fun scanWeather(route: LineString, durationSeconds: Double): List<AlertaMeteo>
    suspend fun scanNightTransitions(route: LineString, durationSeconds: Double): List<AlertaMeteo>
}
