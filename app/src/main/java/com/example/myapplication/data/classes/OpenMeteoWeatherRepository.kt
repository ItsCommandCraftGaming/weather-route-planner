package com.example.myapplication.data.classes

import com.example.myapplication.data.interfaces.IWeatherRepository
import com.example.myapplication.data.interfaces.RadarFrame
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

    override suspend fun getRainViewerFrames(): List<RadarFrame> {
        return try {
            val responseJson = URL("https://api.rainviewer.com/public/weather-maps.json").readText()
            val jsonObject = JSONObject(responseJson)
            val host = jsonObject.getString("host")
            val pastArray = jsonObject.getJSONObject("radar").getJSONArray("past")
            
            val list = mutableListOf<RadarFrame>()
            for (i in 0 until pastArray.length()) {
                val frameObj = pastArray.getJSONObject(i)
                val time = frameObj.getLong("time")
                val path = frameObj.getString("path")
                val url = "$host$path/256/{z}/{x}/{y}/2/1_1.png"
                list.add(RadarFrame(time, url))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun checkWeatherForPoint(point: Point, timeMs: Long): AlertaMeteo? {
        val lat = point.latitude()
        val lon = point.longitude()
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=precipitation,snowfall,visibility,cloud_cover&timezone=GMT"
            val response = URL(url).readText()
            val jsonObject = JSONObject(response)
            val hourly = jsonObject.getJSONObject("hourly")

            val times = hourly.getJSONArray("time")
            val precipitatiiArray = hourly.getJSONArray("precipitation")
            val ninsoareArray = hourly.getJSONArray("snowfall")
            val vizibilitateArray = hourly.getJSONArray("visibility")
            val noriArray = hourly.getJSONArray("cloud_cover")

            // Găsim cel mai apropiat index orar pentru momentul sosirii (timeMs)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("GMT")
            }

            var bestIndex = 0
            var minDiff = Long.MAX_VALUE
            for (i in 0 until times.length()) {
                val timeStr = times.getString(i)
                try {
                    val date = sdf.parse(timeStr)
                    if (date != null) {
                        val diff = kotlin.math.abs(date.time - timeMs)
                        if (diff < minDiff) {
                            minDiff = diff
                            bestIndex = i
                        }
                    }
                } catch (e: Exception) {
                    // Ignoră erorile de parsare
                }
            }

            val precipitatii = precipitatiiArray.optDouble(bestIndex, 0.0)
            val ninsoare = ninsoareArray.optDouble(bestIndex, 0.0)
            val vizibilitate = vizibilitateArray.optDouble(bestIndex, 10000.0)
            val nori = noriArray.optInt(bestIndex, 0)

            when {
                ninsoare > 0.0 -> AlertaMeteo(point, "Zăpadă", "Ninsoare")
                precipitatii > 0.0 -> AlertaMeteo(point, "Ploaie", "Ploaie")
                vizibilitate < 2000.0 -> AlertaMeteo(point, "Ceață", "Ceață")
                nori > 85 -> AlertaMeteo(point, "Nori", "Nori denși")
                nori > 20 -> AlertaMeteo(point, "Nori parțiali", "Nori parțiali")
                else -> AlertaMeteo(point, "Cer senin", "Cer senin")
            }
        } catch (e: Exception) {
            null
        }
    }
}
