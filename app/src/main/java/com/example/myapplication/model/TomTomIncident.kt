package com.example.myapplication.model

import com.mapbox.geojson.Point

data class TomTomIncident(
    val id: String,
    val tip: String,
    val titlu: String,
    val descriere: String,
    val locatie: Point,
    val intarziereSecunde: Int = 0,
    val iconitaEmoji: String = "⚠️"
)
