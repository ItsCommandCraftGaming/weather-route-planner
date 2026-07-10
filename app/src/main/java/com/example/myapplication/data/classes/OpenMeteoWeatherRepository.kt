package com.example.myapplication.data.classes

import com.example.myapplication.data.interfaces.IWeatherRepository
import com.example.myapplication.model.AlertaMeteo
import com.mapbox.geojson.Point
import org.json.JSONObject
import java.net.URL

class OpenMeteoWeatherRepository : IWeatherRepository {

    override suspend fun getRainViewerUrl(): String? {
        return try {
            val responseJson = URL("https://api.rainviewer.com/public/weather-maps.json").readText()
            val jsonObject = JSONObject(responseJson)
            val host = jsonObject.getString("host")
            val pastArray = jsonObject.getJSONObject("radar").getJSONArray("past")
            val ultimulRadar = pastArray.getJSONObject(pastArray.length() - 1)
            val pathCorect = ultimulRadar.getString("path")
            "$host$pathCorect/256/{z}/{x}/{y}/2/1_1.png"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun checkWeatherForPoint(point: Point): AlertaMeteo? {
        val lat = point.latitude()
        val lon = point.longitude()
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=precipitation,snowfall,visibility,cloud_cover"
            val response = URL(url).readText()
            val jsonObject = JSONObject(response)
            val current = jsonObject.getJSONObject("current")

            val precipitatii = current.optDouble("precipitation", 0.0)
            val ninsoare = current.optDouble("snowfall", 0.0)
            val vizibilitate = current.optDouble("visibility", 10000.0)
            val nori = current.optInt("cloud_cover", 0)

            when {
                ninsoare > 0.0 -> AlertaMeteo(point, "Zăpadă", "Ninsoare")
                precipitatii > 0.0 -> AlertaMeteo(point, "Ploaie", "Ploaie")
                vizibilitate < 2000.0 -> AlertaMeteo(point, "Ceață", "Ceață")
                nori > 85 -> AlertaMeteo(point, "Nori", "Nori denși")
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
