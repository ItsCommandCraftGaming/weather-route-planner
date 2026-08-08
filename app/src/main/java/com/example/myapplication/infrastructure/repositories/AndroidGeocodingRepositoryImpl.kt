package com.example.myapplication.infrastructure.repositories

import android.content.Context
import android.location.Geocoder
import com.example.myapplication.application.interfaces.IGeocodingRepository
import com.mapbox.geojson.Point
import java.util.Locale

class AndroidGeocodingRepositoryImpl(private val context: Context) : IGeocodingRepository {
    override suspend fun searchLocation(query: String): Point? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val adrese = geocoder.getFromLocationName(query, 1)
            if (!adrese.isNullOrEmpty()) {
                val locatieGasita = adrese[0]
                Point.fromLngLat(locatieGasita.longitude, locatieGasita.latitude)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
