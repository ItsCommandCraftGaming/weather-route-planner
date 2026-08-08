package com.example.myapplication.infrastructure.api

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class TomTomApi {
    fun fetchIncidents(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        apiKey: String
    ): String? {
        val cleanKey = apiKey.trim()
        val urlString = String.format(
            Locale.US,
            "https://api.tomtom.com/traffic/services/5/incidentDetails?key=%s&bbox=%.5f,%.5f,%.5f,%.5f&language=ro-RO",
            cleanKey, minLon, minLat, maxLon, maxLat
        )
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.connect()
        val responseCode = conn.responseCode
        if (responseCode == 403) {
            throw Exception("403 Forbidden")
        } else if (responseCode != 200) {
            throw Exception("HTTP code $responseCode")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
