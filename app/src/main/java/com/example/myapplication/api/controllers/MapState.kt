package com.example.myapplication.api.controllers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.BuildConfig
import com.example.myapplication.application.interfaces.*
import com.example.myapplication.application.services.*
import com.example.myapplication.domain.entities.AlertaMeteo
import com.example.myapplication.domain.entities.RadarFrame
import com.example.myapplication.domain.entities.TomTomIncident
import com.example.myapplication.infrastructure.repositories.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Calendar

data class TraseuInfo(
    val index: Int,
    val geoJson: LineString,
    val durataSecunde: Double,
    val distantaMetri: Double,
    val puncteVreme: List<AlertaMeteo>,
    val alerteNoapte: List<AlertaMeteo>,
    val factorMeteo: Double,
    val scor: Double,
    val traficGeoJson: FeatureCollection? = null
)

class MapState(
    val mapViewportState: MapViewportState,
    private val context: Context,
    private val searchHistoryRepository: ISearchHistoryRepository,
    val weatherRepository: IWeatherRepository,
    val routeRepository: IRouteRepository = MapboxRouteRepositoryImpl(),
    val geocodingRepository: IGeocodingRepository = AndroidGeocodingRepositoryImpl(context),
    val scope: CoroutineScope,
    val safeZoneManager: ISafeZoneManager = SafeZoneManagerImpl(),
    val drivingSimulator: IDrivingSimulator = DrivingSimulatorImpl(),
    val routeWeatherScanner: IRouteWeatherScanner = RouteWeatherScannerImpl(weatherRepository),
    val tomTomRepository: ITomTomIncidentRepository = TomTomIncidentRepositoryImpl()
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    val MAPBOX_TOKEN = BuildConfig.MAPBOX_PUBLIC_TOKEN

    // Rute alternative si selectie
    var toateTraseele by mutableStateOf<List<TraseuInfo>>(emptyList())
    var indexTraseuSelectat by mutableStateOf(0)
    var rainbowSnapshotTimestamp by mutableStateOf<Long?>(null)
    var toateIncidenteleTomTom by mutableStateOf<List<TomTomIncident>>(emptyList())
    var incidenteTomTom by mutableStateOf<List<TomTomIncident>>(emptyList())

    init {
        actualizeazaRainbowSnapshot()
    }

    fun actualizeazaRainbowSnapshot() {
        val apiKey = BuildConfig.RAINBOW_API_KEY
        if (apiKey.isNotBlank() && apiKey != "your_rainbow_api_key_here") {
            scope.launch(Dispatchers.IO) {
                val snapshot = weatherRepository.getRainbowSnapshot(apiKey)
                withContext(Dispatchers.Main) {
                    rainbowSnapshotTimestamp = snapshot
                }
            }
        }
    }

    // UI States (General)
    var showTimerDialog by mutableStateOf(false)
    var inputOra by mutableStateOf("")
    var inputMinut by mutableStateOf("")
    var timpDisponibilSecunde by mutableStateOf(0.0)

    var locatieCurenta by mutableStateOf<Point?>(null)

    var umbraCivil by mutableStateOf<Polygon?>(null)
    var umbraNautic by mutableStateOf<Polygon?>(null)
    var umbraAstro by mutableStateOf<Polygon?>(null)
    var umbraNoapte by mutableStateOf<Polygon?>(null)

    var traseuGeoJson by mutableStateOf<LineString?>(null)
    var durataTraseuSecunde by mutableStateOf(0.0)

    var alerteMeteo by mutableStateOf<List<AlertaMeteo>>(emptyList())
    var alerteNoapte by mutableStateOf<List<AlertaMeteo>>(emptyList())
    var modScrubbingActiv by mutableStateOf(false)
    var valoareScrubbingSecunde by mutableStateOf(0.0)
    var puncteVremeTraseu by mutableStateOf<List<AlertaMeteo>>(emptyList())
    var radarFrames by mutableStateOf<List<RadarFrame>>(emptyList())
    var activeRadarUrl by mutableStateOf<String?>(null)

    var textCautat by mutableStateOf("")
    var istoricCautari by mutableStateOf(searchHistoryRepository.getHistory())
    var baraEsteFocusata by mutableStateOf(false)

    var pozitiePin by mutableStateOf<Point?>(null)

    fun calculeazaTimpDisponibil(): Double {
        val h = inputOra.toIntOrNull()
        val m = inputMinut.toIntOrNull()
        if (h != null && m != null && h in 0..23 && m in 0..59) {
            val calendar = Calendar.getInstance()
            val acumH = calendar.get(Calendar.HOUR_OF_DAY)
            val acumM = calendar.get(Calendar.MINUTE)
            val acumS = calendar.get(Calendar.SECOND)

            val targetTotal = h * 3600 + m * 60
            val curentTotal = acumH * 3600 + acumM * 60 + acumS

            var diff = (targetTotal - curentTotal).toDouble()
            // Dacă utilizatorul pune o oră pentru a doua zi dimineața
            if (diff < -43200) diff += 86400

            return diff
        } else {
            return -1.0
        }
    }

    fun gasesteLocatiaMea() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val punctulMeu = Point.fromLngLat(location.longitude, location.latitude)
                    mapViewportState.setCameraOptions(
                        CameraOptions.Builder()
                            .center(punctulMeu)
                            .zoom(15.0)
                            .build()
                    )
                }
            }
        } else {
            Toast.makeText(context, "Permisiune locație neacordată", Toast.LENGTH_SHORT).show()
        }
    }

    fun getWeightForWeather(alerta: AlertaMeteo): Double {
        return when (alerta.tip) {
            "Zăpadă" -> 3.5 + (alerta.ninsoareValoare * 2.0)
            "Ceață" -> 2.5
            "Ploaie" -> 1.8 + (alerta.precipitatiiValoare * 1.5)
            "Nori" -> 1.2
            "Nori parțiali" -> 1.1
            "Cer senin" -> 1.0
            else -> 1.0
        }
    }

    fun selecteazaTraseulDirect(index: Int) {
        if (index in toateTraseele.indices) {
            indexTraseuSelectat = index
            val traseu = toateTraseele[index]
            traseuGeoJson = traseu.geoJson
            durataTraseuSecunde = traseu.durataSecunde
            puncteVremeTraseu = traseu.puncteVreme
            alerteNoapte = traseu.alerteNoapte
            alerteMeteo = traseu.puncteVreme.filter { it.tip != "Cer senin" && it.tip != "Nori parțiali" }
            
            // Run incident filtering in background to avoid freezing the UI thread
            scope.launch(Dispatchers.Default) {
                val filtrate = filtreazaIncidentePeTraseu(toateIncidenteleTomTom, traseu.geoJson)
                withContext(Dispatchers.Main) {
                    incidenteTomTom = filtrate
                }
            }
            
            drivingSimulator.stopDriving()
        }
    }

    fun calculeazaTraseu(start: Point, final: Point) {
        actualizeazaRainbowSnapshot()
        routeRepository.getDirections(start, final, MAPBOX_TOKEN, object : Callback<DirectionsResponse> {
            override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                val routes = response.body()?.routes() ?: emptyList()
                if (routes.isNotEmpty()) {
                    scope.launch(Dispatchers.Default) {
                        val activeRoutesList = routes.take(3).mapIndexed { index, route ->
                            async(Dispatchers.IO) {
                                val geom = route.geometry() ?: return@async null
                                val lineString = LineString.fromPolyline(geom, 6)
                                val durationSec = route.duration() ?: 0.0
                                val distanceMet = route.distance() ?: 0.0

                                val puncteVreme = routeWeatherScanner.scanWeather(lineString, durationSec)
                                val alerteNoapte = routeWeatherScanner.scanNightTransitions(lineString, durationSec)

                                val factorMeteo = if (puncteVreme.isNotEmpty()) {
                                    puncteVreme.map { getWeightForWeather(it) }.average()
                                } else {
                                    1.0
                                }

                                val distanceKm = distanceMet / 1000.0
                                val durationMin = durationSec / 60.0
                                val scor = distanceKm + (factorMeteo * durationMin)

                                val congestions = route.legs()?.flatMap { leg ->
                                    leg.annotation()?.congestion() ?: emptyList()
                                } ?: emptyList()

                                val coords = lineString.coordinates()
                                val trafficFeatures = mutableListOf<Feature>()

                                if (congestions.isNotEmpty() && congestions.size == coords.size - 1) {
                                    for (i in 0 until coords.size - 1) {
                                        val segment = LineString.fromLngLats(listOf(coords[i], coords[i + 1]))
                                        val feature = Feature.fromGeometry(segment)
                                        val level = congestions[i]
                                        val colorHex = when (level) {
                                            "low" -> "#4CAF50"       // Verde - Trafic lejer
                                            "moderate" -> "#FFC107"  // Galben - Trafic moderat
                                            "heavy" -> "#FF5722"     // Portocaliu - Trafic intens
                                            "severe" -> "#B71C1C"    // Roșu închis - Trafic foarte aglomerat
                                            else -> "#2196F3"        // Albastru - Trafic necunoscut / normal
                                        }
                                        feature.addStringProperty("color", colorHex)
                                        trafficFeatures.add(feature)
                                    }
                                } else {
                                    val feature = Feature.fromGeometry(lineString)
                                    feature.addStringProperty("color", "#2196F3")
                                    trafficFeatures.add(feature)
                                }

                                val traficGeoJson = FeatureCollection.fromFeatures(trafficFeatures)

                                TraseuInfo(
                                    index = index,
                                    geoJson = lineString,
                                    durataSecunde = durationSec,
                                    distantaMetri = distanceMet,
                                    puncteVreme = puncteVreme,
                                    alerteNoapte = alerteNoapte,
                                    factorMeteo = factorMeteo,
                                    scor = scor,
                                    traficGeoJson = traficGeoJson
                                )
                            }
                        }.awaitAll().filterNotNull()

                        withContext(Dispatchers.Main) {
                            toateTraseele = activeRoutesList
                            if (activeRoutesList.isNotEmpty()) {
                                selecteazaTraseulDirect(0)
                            }
                        }

                        val geometries = activeRoutesList.map { it.geoJson }
                        incarcaIncidenteTomTom(geometries)
                    }
                } else {
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Nu s-a găsit niciun traseu", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                t.printStackTrace()
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Eroare la obținerea traseului", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    fun efectueazaCautarea(query: String, permissionLauncher: () -> Unit) {
        baraEsteFocusata = false

        if (query.isNotBlank()) {
            textCautat = query
            istoricCautari = searchHistoryRepository.saveQuery(query)

            scope.launch(Dispatchers.IO) {
                val punctNou = geocodingRepository.searchLocation(query)
                if (punctNou != null) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                val punctStart = Point.fromLngLat(location.longitude, location.latitude)
                                calculeazaTraseu(punctStart, punctNou)

                                pozitiePin = punctNou
                                mapViewportState.setCameraOptions(
                                    CameraOptions.Builder()
                                        .center(punctNou)
                                        .zoom(12.0)
                                        .build()
                                )
                            } else {
                                Toast.makeText(context, "Pornește GPS-ul din setări!", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        // Run on Main Thread
                        withContext(Dispatchers.Main) {
                            permissionLauncher()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Eroare la căutare", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun stergeIstoric() {
        searchHistoryRepository.clearHistory()
        istoricCautari = emptyList()
        baraEsteFocusata = false
    }

    fun curataTraseu() {
        traseuGeoJson = null
        toateTraseele = emptyList()
        indexTraseuSelectat = 0
        safeZoneManager.stopSafeZone()
        drivingSimulator.stopDriving()
        pozitiePin = null
        alerteMeteo = emptyList()
        alerteNoapte = emptyList()
        textCautat = ""
        modScrubbingActiv = false
        valoareScrubbingSecunde = 0.0
        puncteVremeTraseu = emptyList()
        activeRadarUrl = radarFrames.lastOrNull()?.url
        incidenteTomTom = emptyList()
    }

    class BBox(
        var minLat: Double,
        var minLon: Double,
        var maxLat: Double,
        var maxLon: Double
    ) {
        fun getAreaKm2(): Double {
            val avgLat = (minLat + maxLat) / 2.0
            val latHeight = (maxLat - minLat) * 111.0
            val lonWidth = (maxLon - minLon) * 111.0 * java.lang.Math.cos(java.lang.Math.toRadians(avgLat))
            return latHeight * lonWidth
        }

        fun addPoint(lat: Double, lon: Double) {
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
        }

        fun clone(): BBox {
            return BBox(minLat, minLon, maxLat, maxLon)
        }
    }

    private fun generateBBoxesForRoutes(routes: List<LineString>): List<BBox> {
        val bboxes = mutableListOf<BBox>()
        val padding = 0.05

        for (route in routes) {
            val coords = route.coordinates()
            var i = 0
            val n = coords.size
            while (i < n) {
                val coord = coords[i]

                val covered = bboxes.any { bbox ->
                    coord.latitude() >= bbox.minLat && coord.latitude() <= bbox.maxLat &&
                    coord.longitude() >= bbox.minLon && coord.longitude() <= bbox.maxLon
                }

                if (covered) {
                    i++
                    continue
                }

                val bbox = BBox(
                    minLat = coord.latitude() - padding,
                    minLon = coord.longitude() - padding,
                    maxLat = coord.latitude() + padding,
                    maxLon = coord.longitude() + padding
                )

                var j = i + 1
                while (j < n) {
                    val pt = coords[j]
                    val tempBBox = bbox.clone()
                    tempBBox.addPoint(pt.latitude(), pt.longitude())

                    val paddedBBox = BBox(
                        minLat = tempBBox.minLat - padding,
                        minLon = tempBBox.minLon - padding,
                        maxLat = tempBBox.maxLat + padding,
                        maxLon = tempBBox.maxLon + padding
                    )

                    if (paddedBBox.getAreaKm2() <= 9000.0) {
                        bbox.minLat = tempBBox.minLat
                        bbox.minLon = tempBBox.minLon
                        bbox.maxLat = tempBBox.maxLat
                        bbox.maxLon = tempBBox.maxLon
                        j++
                    } else {
                        break
                    }
                }

                bboxes.add(
                    BBox(
                        minLat = bbox.minLat - padding,
                        minLon = bbox.minLon - padding,
                        maxLat = bbox.maxLat + padding,
                        maxLon = bbox.maxLon + padding
                    )
                )

                i = if (j == i + 1) j else j - 1
            }
        }
        return bboxes
    }

    fun incarcaIncidenteTomTom(rute: List<LineString>) {
        val apiKey = getTomTomApiKey()
        if (apiKey.isBlank() || apiKey == "your_tomtom_api_key_here" || apiKey == "your_tomtom_api_key") {
            android.util.Log.w("MapState", "TomTom API Key este lipsă sau invalidă în BuildConfig.")
            return
        }

        if (rute.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            val bboxes = generateBBoxesForRoutes(rute)
            val deferreds = bboxes.map { bbox ->
                async(Dispatchers.IO) {
                    tomTomRepository.getIncidents(bbox.minLat, bbox.minLon, bbox.maxLat, bbox.maxLon, apiKey)
                }
            }
            val toateIncidentele = deferreds.awaitAll().flatten().distinctBy { it.id }

            val filtrate = withContext(Dispatchers.Default) {
                val traseuCurent = toateTraseele.getOrNull(indexTraseuSelectat)?.geoJson
                filtreazaIncidentePeTraseu(toateIncidentele, traseuCurent)
            }

            withContext(Dispatchers.Main) {
                toateIncidenteleTomTom = toateIncidentele
                incidenteTomTom = filtrate
            }
        }
    }

    fun filtreazaIncidentePeTraseu(incidente: List<TomTomIncident>, route: LineString?): List<TomTomIncident> {
        if (route == null || incidente.isEmpty()) return emptyList()
        val coords = route.coordinates()
        if (coords.isEmpty()) return emptyList()

        return incidente.filter { incident ->
            var minDistanceMeters = Double.MAX_VALUE
            val incidentLat = incident.locatie.latitude()
            val incidentLon = incident.locatie.longitude()

            for (coord in coords) {
                val coordLat = coord.latitude()
                val coordLon = coord.longitude()

                // Fast pre-filter (bounding-box approx. 2km):
                // 0.02 deg lat ≈ 2.2km, 0.03 deg lon ≈ 2.3km
                if (java.lang.Math.abs(incidentLat - coordLat) < 0.02 &&
                    java.lang.Math.abs(incidentLon - coordLon) < 0.03) {
                    
                    val dist = TurfMeasurement.distance(incident.locatie, coord, "meters")
                    if (dist < minDistanceMeters) {
                        minDistanceMeters = dist
                    }
                }
            }
            minDistanceMeters <= 1500.0 // Doar incidentele aflate la maxim 1.5 km de linia traseului
        }.sortedByDescending { it.intarziereSecunde }.take(30) // Se afiseaza doar cele mai importante maxim 30 incidente
    }

    private fun getTomTomApiKey(): String {
        return try {
            BuildConfig.TOMTOM_API_KEY
        } catch (e: Throwable) {
            try {
                val field = BuildConfig::class.java.getField("TOMTOM_API_KEY")
                field.get(null) as? String ?: ""
            } catch (e2: Throwable) {
                ""
            }
        }
    }
}

@Composable
fun rememberMapState(
    mapViewportState: MapViewportState,
    searchHistoryRepository: ISearchHistoryRepository,
    weatherRepository: IWeatherRepository,
    context: Context = LocalContext.current,
    routeRepository: IRouteRepository = remember { MapboxRouteRepositoryImpl() },
    geocodingRepository: IGeocodingRepository = remember(context) { AndroidGeocodingRepositoryImpl(context) },
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): MapState {
    return remember(mapViewportState, searchHistoryRepository, weatherRepository, routeRepository, geocodingRepository, coroutineScope, context) {
        MapState(
            mapViewportState = mapViewportState,
            context = context,
            searchHistoryRepository = searchHistoryRepository,
            weatherRepository = weatherRepository,
            routeRepository = routeRepository,
            geocodingRepository = geocodingRepository,
            scope = coroutineScope
        )
    }
}
