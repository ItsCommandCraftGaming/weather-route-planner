package com.example.myapplication.application.interfaces

import com.example.myapplication.domain.entities.AlertaMeteo
import com.mapbox.geojson.LineString

interface IRouteWeatherScanner {
    suspend fun scanWeather(route: LineString, durationSeconds: Double): List<AlertaMeteo>
    suspend fun scanNightTransitions(route: LineString, durationSeconds: Double): List<AlertaMeteo>
}
