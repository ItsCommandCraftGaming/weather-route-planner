package com.example.myapplication.application.services

import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import java.util.Calendar
import kotlin.math.*

object SunCalculator {

    fun calculeazaUmbra(altitudineSoare: Double): Polygon {
        val D2R = Math.PI / 180.0
        val R2D = 180.0 / Math.PI

        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        var declinatie = -23.44 * cos((360.0 / 365.0) * (dayOfYear + 10) * D2R)
        if (abs(declinatie) < 0.1) declinatie = 0.1

        val utcHour = (System.currentTimeMillis() / 3600000.0) % 24.0
        val sunLon = 180.0 - (utcHour * 15.0)

        val puncte = mutableListOf<Point>()

        val sinAlt = sin(altitudineSoare * D2R)
        val sinDec = sin(declinatie * D2R)
        val cosDec = cos(declinatie * D2R)

        for (lon in -180..180 step 2) {
            val hra = (lon - sunLon) * D2R

            val a = sinDec
            val b = cosDec * cos(hra)
            val R = sqrt(a * a + b * b)

            var lat = 0.0
            val raport = sinAlt / R

            if (abs(raport) <= 1.0) {
                val alpha = atan2(a, b)
                val phi1 = alpha - acos(raport)
                val phi2 = alpha + acos(raport)

                lat = if (declinatie > 0) minOf(phi1, phi2) else maxOf(phi1, phi2)
                lat *= R2D
            } else {
                lat = if (declinatie > 0) -89.9 else 89.9
            }

            if (lat > 89.9) lat = 89.9
            if (lat < -89.9) lat = -89.9

            puncte.add(Point.fromLngLat(lon.toDouble(), lat))
        }

        if (declinatie > 0) {
            puncte.add(Point.fromLngLat(180.0, -89.9))
            puncte.add(Point.fromLngLat(-180.0, -89.9))
        } else {
            puncte.add(Point.fromLngLat(180.0, 89.9))
            puncte.add(Point.fromLngLat(-180.0, 89.9))
        }

        puncte.add(puncte.first())
        return Polygon.fromLngLats(listOf(puncte))
    }

    fun calculeazaAltitudineSoareViitor(lat: Double, lon: Double, timpViitorMs: Long): Double {
        val D2R = Math.PI / 180.0
        val R2D = 180.0 / Math.PI

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timpViitorMs
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        val declinatie = -23.44 * cos((360.0 / 365.0) * (dayOfYear + 10) * D2R)

        val utcHour = (timpViitorMs / 3600000.0) % 24.0
        val sunLon = 180.0 - (utcHour * 15.0)
        val hra = (lon - sunLon) * D2R

        val latR = lat * D2R
        val decR = declinatie * D2R

        val sinAlt = sin(latR) * sin(decR) + cos(latR) * cos(decR) * cos(hra)
        return asin(sinAlt) * R2D
    }

    fun calculeazaUnghiTerminator(lat: Double, lon: Double, timpMs: Long): Double {
        val D2R = Math.PI / 180.0
        val R2D = 180.0 / Math.PI

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timpMs
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        val declinatie = -23.44 * cos((360.0 / 365.0) * (dayOfYear + 10) * D2R)

        val utcHour = (timpMs / 3600000.0) % 24.0
        val sunLon = 180.0 - (utcHour * 15.0)
        val hra = (lon - sunLon) * D2R

        val latR = lat * D2R
        val decR = declinatie * D2R

        val y = sin(hra)
        val x = cos(hra) * sin(latR) - tan(decR) * cos(latR)
        val azimuthSoare = atan2(y, x) * R2D + 180.0

        return azimuthSoare + 90.0
    }
}
