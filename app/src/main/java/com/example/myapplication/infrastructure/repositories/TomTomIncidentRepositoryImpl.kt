package com.example.myapplication.infrastructure.repositories

import android.util.Log
import com.example.myapplication.application.interfaces.ITomTomIncidentRepository
import com.example.myapplication.domain.entities.TomTomIncident
import com.example.myapplication.infrastructure.api.TomTomApi
import com.mapbox.geojson.Point
import org.json.JSONObject

class TomTomIncidentRepositoryImpl(
    private val tomTomApi: TomTomApi = TomTomApi()
) : ITomTomIncidentRepository {

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
            Log.d("TomTomRepo", "Apelare TomTom Api...")
            val responseText = tomTomApi.fetchIncidents(minLat, minLon, maxLat, maxLon, cleanKey)
            if (responseText.isNullOrBlank()) return emptyList()

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

                val titlu = parseCategory(iconCategory)

                rezultate.add(
                    TomTomIncident(
                        id = id,
                        tip = titlu,
                        titlu = titlu,
                        descriere = descriere,
                        locatie = Point.fromLngLat(lon, lat),
                        intarziereSecunde = delay
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

    private fun parseCategory(category: Int): String {
        return when (category) {
            1 -> "Accident rutier"
            2 -> "Ceață"
            3 -> "Drum periculos / Alunecos"
            4 -> "Ploaie torențială"
            5 -> "Gheață / Polei"
            6 -> "Aglomerație / Dop de trafic"
            7 -> "Bandă blocată"
            8 -> "Drum închis"
            9 -> "Lucrări pe carosabil"
            10 -> "Inundație"
            11 -> "Vânt puternic"
            14 -> "Echipaj / Control viteză"
            else -> "Incident în trafic"
        }
    }
}
