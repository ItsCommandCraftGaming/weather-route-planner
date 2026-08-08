package com.example.myapplication.application.interfaces

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point

interface ISafeZoneManager {
    var isSafeZoneModeActive: Boolean
    var circleRadiusMeters: Double
    var linieAvertizare: LineString?
    var punctMijlocLinie: Point?
    var textSecundeInUrma: String

    fun startSafeZone(inputOra: String, inputMinut: String): Double
    fun stopSafeZone()
    fun tickSafeZone(secundeRamase: Double)
    fun updateWarningLine(locatieCurenta: Point?, center: Point?)
}
