package com.example.myapplication.application.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.myapplication.application.interfaces.IDrivingSimulator
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement

class DrivingSimulatorImpl : IDrivingSimulator {
    override var isDrivingModeActive by mutableStateOf(false)
    override var pozitieMasinaAnimata by mutableStateOf<Point?>(null)
    override var statusMasina by mutableStateOf("")

    override fun startDriving(durationSeconds: Double) {
        isDrivingModeActive = true
    }

    override fun stopDriving() {
        isDrivingModeActive = false
        pozitieMasinaAnimata = null
        statusMasina = ""
    }

    override fun tickDriving(route: LineString, elapsedSeconds: Double, durationSeconds: Double, targetSeconds: Double) {
        val distantaTotala = TurfMeasurement.length(route, TurfConstants.UNIT_METERS)
        val vitezaRealaMPS = distantaTotala / durationSeconds

        if (targetSeconds >= durationSeconds) {
            val timpAsteptare = targetSeconds - durationSeconds
            if (elapsedSeconds < timpAsteptare) {
                pozitieMasinaAnimata = TurfMeasurement.along(route, 0.0, TurfConstants.UNIT_METERS)
                statusMasina = "Plecare în: ${(timpAsteptare - elapsedSeconds).toInt()} secunde"
            } else {
                val timpCondus = elapsedSeconds - timpAsteptare
                if (timpCondus <= durationSeconds) {
                    statusMasina = "La timp. Sosire în: ${(durationSeconds - timpCondus).toInt()} s"
                    val distantaParcursa = timpCondus * vitezaRealaMPS
                    pozitieMasinaAnimata = TurfMeasurement.along(route, distantaParcursa, TurfConstants.UNIT_METERS)
                } else {
                    pozitieMasinaAnimata = TurfMeasurement.along(route, distantaTotala, TurfConstants.UNIT_METERS)
                    statusMasina = "A ajuns la destinație!"
                    isDrivingModeActive = false
                }
            }
        } else {
            val timpIntarziere = durationSeconds - targetSeconds
            val startSecunde = if (timpIntarziere > durationSeconds) durationSeconds else timpIntarziere

            val timpCondus = startSecunde + elapsedSeconds
            if (timpCondus <= durationSeconds) {
                statusMasina = "Întârziere compensată. Ajunge în: ${(durationSeconds - timpCondus).toInt()} s"
                val distantaParcursa = timpCondus * vitezaRealaMPS
                pozitieMasinaAnimata = TurfMeasurement.along(route, distantaParcursa, TurfConstants.UNIT_METERS)
            } else {
                pozitieMasinaAnimata = TurfMeasurement.along(route, distantaTotala, TurfConstants.UNIT_METERS)
                statusMasina = "A ajuns la destinație!"
                isDrivingModeActive = false
            }
        }
    }
}
