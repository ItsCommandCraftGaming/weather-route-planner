package com.example.myapplication.infrastructure.api

import java.net.URL

class OpenMeteoApi {
    fun fetchForecast(lat: Double, lon: Double): String? {
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=precipitation,snowfall,visibility,cloud_cover&timezone=GMT"
            URL(url).readText()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
