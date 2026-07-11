package com.example.myapplication.model

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point

data class AlertaMeteo(
    val punct: Point,
    val tip: String,
    val mesaj: String,
    val linieTranzitie: LineString? = null,
    val minuteDeLaPlecare: Int? = null
)