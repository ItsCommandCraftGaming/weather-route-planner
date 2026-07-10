package com.example.myapplication.data.classes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.myapplication.data.interfaces.ISafeZoneManager
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement

class SafeZoneManagerImpl : ISafeZoneManager {
    override var isSafeZoneModeActive by mutableStateOf(false)
    override var circleRadiusMeters by mutableStateOf(0.0)
    override var linieAvertizare by mutableStateOf<LineString?>(null)
    override var punctMijlocLinie by mutableStateOf<Point?>(null)
    override var textSecundeInUrma by mutableStateOf("")

    override fun startSafeZone(inputOra: String, inputMinut: String): Double {
        val h = inputOra.toIntOrNull()
        val m = inputMinut.toIntOrNull()
        if (h != null && m != null && h in 0..23 && m in 0..59) {
            val calendar = java.util.Calendar.getInstance()
            val acumH = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val acumM = calendar.get(java.util.Calendar.MINUTE)
            val acumS = calendar.get(java.util.Calendar.SECOND)

            val targetTotal = h * 3600 + m * 60
            val curentTotal = acumH * 3600 + acumM * 60 + acumS

            var diff = (targetTotal - curentTotal).toDouble()
            if (diff < -43200) diff += 86400

            if (diff > 0) {
                circleRadiusMeters = (diff / 60.0) * 83.0
                isSafeZoneModeActive = true
            }
            return diff
        }
        return -1.0
    }

    override fun stopSafeZone() {
        isSafeZoneModeActive = false
        circleRadiusMeters = 0.0
        linieAvertizare = null
        punctMijlocLinie = null
        textSecundeInUrma = ""
    }

    override fun tickSafeZone(secundeRamase: Double) {
        if (secundeRamase > 0) {
            circleRadiusMeters = (secundeRamase / 60.0) * 83.0
        } else {
            stopSafeZone()
        }
    }

    override fun updateWarningLine(locatieCurenta: Point?, center: Point?) {
        if (isSafeZoneModeActive && locatieCurenta != null && center != null && circleRadiusMeters > 0) {
            val distantaPanaLaCentru = TurfMeasurement.distance(center, locatieCurenta, TurfConstants.UNIT_METERS)

            if (distantaPanaLaCentru > circleRadiusMeters) {
                val unghiSpreCentru = TurfMeasurement.bearing(center, locatieCurenta)
                val punctMargineCerc = TurfMeasurement.destination(center, circleRadiusMeters, unghiSpreCentru, TurfConstants.UNIT_METERS)
                linieAvertizare = LineString.fromLngLats(listOf(locatieCurenta, punctMargineCerc))
                punctMijlocLinie = TurfMeasurement.midpoint(locatieCurenta, punctMargineCerc)

                val distantaPanaLaMargine = distantaPanaLaCentru - circleRadiusMeters
                val secundeIntarziere = (distantaPanaLaMargine / (83.0 / 60.0)).toInt()
                textSecundeInUrma = "+$secundeIntarziere s"
            } else {
                linieAvertizare = null
                punctMijlocLinie = null
            }
        } else {
            linieAvertizare = null
            punctMijlocLinie = null
        }
    }
}
