package com.example.myapplication.infrastructure.api

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RainbowApi {
    fun fetchRainbowSnapshot(apiKey: String): Long? {
        val url = URL("https://api.rainbow.ai/tiles/v1/snapshot?layer=precip")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val responseJson = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(responseJson)
            return jsonObject.getLong("snapshot")
        } else {
            throw Exception("Rainbow API returned code $responseCode")
        }
    }
}
