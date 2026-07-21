package com.example.myapplication.data.classes

import android.util.Log
import com.example.myapplication.data.interfaces.ITomTomIncidentRepository
import com.example.myapplication.model.TomTomIncident
import com.mapbox.geojson.Point
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class TomTomIncidentRepositoryImpl : ITomTomIncidentRepository {

    override suspend fun getIncidents(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        apiKey: String
    ): List<TomTomIncident> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "your_tomtom_api_key_here" || cleanKey == "your_tomtom_api_key") {
            Log.w("TomTomRepo", "Cheia TomTom API este lipsă sau invalidă.")
            return emptyList()
        }

        return try {
            // Formatare coordonate cu punct ca separator zecimal (Locale.US)
            val urlString = String.format(
                Locale.US,
                "https://api.tomtom.com/traffic/services/5/incidentDetails?key=%s&bbox=%.5f,%.5f,%.5f,%.5f&language=ro-RO",
                cleanKey, minLon, minLat, maxLon, maxLat
            )

            Log.d("TomTomRepo", "Apelare TomTom URL: $urlString")

            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val responseCode = conn.responseCode
            if (responseCode == 403) {
                Log.e("TomTomRepo", "Eroare HTTP 403: Cheia API nu are autorizat serviciul Traffic API în consola TomTom.")
                return emptyList()
            } else if (responseCode != 200) {
                Log.e("TomTomRepo", "TomTom API a returnat codul de eroare HTTP $responseCode")
                return emptyList()
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)
            val incidentsArray = root.optJSONArray("incidents") ?: return emptyList()

            val rezultate = mutableListOf<TomTomIncident>()

            for (i in 0 until incidentsArray.length()) {
                val item = incidentsArray.getJSONObject(i)
                val id = item.optString("id", i.toString())

                val geometry = item.optJSONObject("geometry") ?: continue
                val geomType = geometry.optString("type", "")
                val coords = geometry.optJSONArray("coordinates") ?: continue

                var lon = 0.0
                var lat = 0.0

                if (geomType == "Point") {
                    lon = coords.optDouble(0, 0.0)
                    lat = coords.optDouble(1, 0.0)
                } else if (geomType == "LineString") {
                    val firstPt = coords.optJSONArray(0) ?: continue
                    lon = firstPt.optDouble(0, 0.0)
                    lat = firstPt.optDouble(1, 0.0)
                } else if (geomType == "MultiLineString") {
                    val firstLine = coords.optJSONArray(0) ?: continue
                    val firstPt = firstLine.optJSONArray(0) ?: continue
                    lon = firstPt.optDouble(0, 0.0)
                    lat = firstPt.optDouble(1, 0.0)
                } else {
                    continue
                }

                if (lon == 0.0 || lat == 0.0) continue

                val props = item.optJSONObject("properties")
                val iconCategory = props?.optInt("iconCategory", 0) ?: 0
                val delay = props?.optInt("delay", 0) ?: 0

                val eventsArray = props?.optJSONArray("events")
                val descriere = if (eventsArray != null && eventsArray.length() > 0) {
                    eventsArray.getJSONObject(0).optString("description", "Incident semnalat")
                } else {
                    "Incident pe traseu"
                }

                val (titlu, emoji) = parseCategory(iconCategory)

                rezultate.add(
                    TomTomIncident(
                        id = id,
                        tip = titlu,
                        titlu = titlu,
                        descriere = descriere,
                        locatie = Point.fromLngLat(lon, lat),
                        intarziereSecunde = delay,
                        iconitaEmoji = emoji
                    )
                )
            }

            Log.d("TomTomRepo", "Incidentele TomTom găsite: ${rezultate.size}")
            rezultate
        } catch (e: Exception) {
            Log.e("TomTomRepo", "Eroare la preluarea incidentelor TomTom: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseCategory(category: Int): Pair<String, String> {
        return when (category) {
            1 -> "Accident rutier" to "🚗💥"
            2 -> "Ceață" to "🌫️"
            3 -> "Drum periculos / Alunecos" to "⚠️"
            4 -> "Ploaie torențială" to "🌧️"
            5 -> "Gheață / Polei" to "❄️"
            6 -> "Aglomerație / Dop de trafic" to "🚘"
            7 -> "Bandă blocată" to "⛔"
            8 -> "Drum închis" to "🚫"
            9 -> "Lucrări pe carosabil" to "🚧"
            10 -> "Inundație" to "🌊"
            11 -> "Vânt puternic" to "💨"
            14 -> "Echipaj / Control viteză" to "👮"
            else -> "Incident în trafic" to "⚠️"
        }
    }
}
