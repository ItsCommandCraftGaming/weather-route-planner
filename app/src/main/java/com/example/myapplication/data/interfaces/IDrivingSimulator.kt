package com.example.myapplication.data.interfaces

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point

interface IDrivingSimulator {
    var isDrivingModeActive: Boolean
    var pozitieMasinaAnimata: Point?
    var statusMasina: String

    fun startDriving(durationSeconds: Double)
    fun stopDriving()
    fun tickDriving(route: LineString, elapsedSeconds: Double, durationSeconds: Double, targetSeconds: Double)
}
