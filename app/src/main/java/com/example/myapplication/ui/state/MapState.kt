package com.example.myapplication.ui.state

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.interfaces.*
import com.example.myapplication.data.classes.*
import com.example.myapplication.model.AlertaMeteo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Calendar
import java.util.Locale

data class TraseuInfo(
    val index: Int,
    val geoJson: LineString,
    val durataSecunde: Double,
    val distantaMetri: Double,
    val puncteVreme: List<AlertaMeteo>,
    val alerteNoapte: List<AlertaMeteo>,
    val factorMeteo: Double,
    val scor: Double
)

class MapState(
    val mapViewportState: MapViewportState,
    private val searchHistoryRepository: ISearchHistoryRepository,
    val weatherRepository: IWeatherRepository,
    val scope: CoroutineScope,
    private val context: Context,
    val safeZoneManager: ISafeZoneManager = SafeZoneManagerImpl(),
    val drivingSimulator: IDrivingSimulator = DrivingSimulatorImpl(),
    val routeWeatherScanner: IRouteWeatherScanner = RouteWeatherScannerImpl(weatherRepository)
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    val MAPBOX_TOKEN = BuildConfig.MAPBOX_PUBLIC_TOKEN

    // Rute alternative si selectie
    var toateTraseele by mutableStateOf<List<TraseuInfo>>(emptyList())
    var indexTraseuSelectat by mutableStateOf(0)
    var rainbowSnapshotTimestamp by mutableStateOf<Long?>(null)

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
            drivingSimulator.stopDriving()
        }
    }

    fun calculeazaTraseu(start: Point, final: Point) {
        actualizeazaRainbowSnapshot()
        val routeOptions = RouteOptions.builder()
            .coordinatesList(listOf(start, final))
            .profile(DirectionsCriteria.PROFILE_DRIVING)
            .geometries(DirectionsCriteria.GEOMETRY_POLYLINE6)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .alternatives(true)
            .build()

        val client = MapboxDirections.builder()
            .accessToken(MAPBOX_TOKEN)
            .routeOptions(routeOptions)
            .build()

        client.enqueueCall(object : Callback<DirectionsResponse> {
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

                                TraseuInfo(
                                    index = index,
                                    geoJson = lineString,
                                    durataSecunde = durationSec,
                                    distantaMetri = distanceMet,
                                    puncteVreme = puncteVreme,
                                    alerteNoapte = alerteNoapte,
                                    factorMeteo = factorMeteo,
                                    scor = scor
                                )
                            }
                        }.awaitAll().filterNotNull()

                        withContext(Dispatchers.Main) {
                            toateTraseele = activeRoutesList
                            if (activeRoutesList.isNotEmpty()) {
                                selecteazaTraseulDirect(0)
                            }
                        }
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
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val adrese = geocoder.getFromLocationName(query, 1)

                    if (!adrese.isNullOrEmpty()) {
                        val locatieGasita = adrese[0]
                        val punctNou = Point.fromLngLat(locatieGasita.longitude, locatieGasita.latitude)

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
                    }
                } catch (e: Exception) {
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
    }
}

@Composable
fun rememberMapState(
    mapViewportState: MapViewportState,
    searchHistoryRepository: ISearchHistoryRepository,
    weatherRepository: IWeatherRepository,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    context: Context = LocalContext.current
): MapState {
    return remember(mapViewportState, searchHistoryRepository, weatherRepository, coroutineScope, context) {
        MapState(
            mapViewportState = mapViewportState,
            searchHistoryRepository = searchHistoryRepository,
            weatherRepository = weatherRepository,
            scope = coroutineScope,
            context = context
        )
    }
}
