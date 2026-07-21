package com.example.myapplication.ui.components

import com.example.myapplication.BuildConfig
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.data.classes.SunCalculator
import com.example.myapplication.ui.state.MapState
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.rasterLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.rasterSource
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfTransformation
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.viewannotation.annotationAnchor
import kotlinx.coroutines.*
import java.util.Locale
import com.example.myapplication.model.AlertaMeteo

fun getRepresentativeWeather(puncteVreme: List<AlertaMeteo>): String {
    if (puncteVreme.isEmpty()) return "Cer senin"
    val priorities = listOf("Zăpadă", "Ceață", "Ploaie", "Nori", "Nori parțiali", "Cer senin")
    for (type in priorities) {
        if (puncteVreme.any { it.tip == type }) {
            return type
        }
    }
    return "Cer senin"
}


@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@Composable
fun MapScreen(
    state: MapState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Launcher pentru permisiunea de locatie
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            state.gasesteLocatiaMea()
        } else {
            Toast.makeText(context, "Aplicația are nevoie de permisiune pentru a-ți arăta locația!", Toast.LENGTH_SHORT).show()
        }
    }

    // --- LAUNCHED EFFECTS PENTRU LOGICA IN FUNDAL ---

    // 1. Cronometru în fundal care mișcă umbrele din minut în minut (sau 15s)
    LaunchedEffect(Unit) {
        while (true) {
            state.umbraCivil = SunCalculator.calculeazaUmbra(0.0)      // Apus / Răsărit
            state.umbraNautic = SunCalculator.calculeazaUmbra(-6.0)    // Crepuscul Nautic
            state.umbraAstro = SunCalculator.calculeazaUmbra(-12.0)    // Crepuscul Astronomic
            state.umbraNoapte = SunCalculator.calculeazaUmbra(-18.0)   // Noapte deplină
            delay(15_000L)
        }
    }

    // 1b. Preluare cadre radar trecute (RainViewer)
    LaunchedEffect(Unit) {
        state.scope.launch(Dispatchers.IO) {
            val frames = state.weatherRepository.getRainViewerFrames()
            withContext(Dispatchers.Main) {
                state.radarFrames = frames
                state.activeRadarUrl = frames.lastOrNull()?.url
            }
        }
    }

    // 1c. Actualizare cadru radar activ în funcție de slider-ul din trecut
    LaunchedEffect(state.valoareScrubbingSecunde, state.radarFrames, state.modScrubbingActiv) {
        val frames = state.radarFrames
        if (frames.isNotEmpty()) {
            if (state.modScrubbingActiv && state.valoareScrubbingSecunde < 0) {
                // Găsim cadrul cel mai apropiat în trecut
                val targetTimeMs = System.currentTimeMillis() + (state.valoareScrubbingSecunde * 1000).toLong()
                val closestFrame = frames.minByOrNull { kotlin.math.abs(it.time * 1000 - targetTimeMs) }
                state.activeRadarUrl = closestFrame?.url
            } else if (state.modScrubbingActiv && state.valoareScrubbingSecunde > 0) {
                // În viitor, ascundem radarul RainViewer din trecut/prezent (ca să arătăm norii prognozați)
                state.activeRadarUrl = null
            } else {
                // La momentul curent sau când previzualizarea nu e activă, afișăm radarul curent (prezent)
                state.activeRadarUrl = frames.lastOrNull()?.url
            }
        }
    }

    // 2. LOGICA PENTRU CERCUL DINAMIC (Se micșorează în timp real)
    LaunchedEffect(state.safeZoneManager.isSafeZoneModeActive) {
        if (state.safeZoneManager.isSafeZoneModeActive) {
            while (state.safeZoneManager.isSafeZoneModeActive) {
                val secundeRamase = state.calculeazaTimpDisponibil()

                if (secundeRamase > 0) {
                    state.safeZoneManager.tickSafeZone(secundeRamase)
                } else {
                    state.safeZoneManager.stopSafeZone()
                    Toast.makeText(context, "Timpul a expirat! Cercul s-a închis.", Toast.LENGTH_SHORT).show()
                }
                delay(1000L)
            }
        }
    }

    // 3. LOGICA PENTRU LINIA DE AVERTIZARE
    LaunchedEffect(state.locatieCurenta, state.safeZoneManager.circleRadiusMeters, state.safeZoneManager.isSafeZoneModeActive) {
        state.safeZoneManager.updateWarningLine(state.locatieCurenta, state.pozitiePin)
    }

    // 4. LOGICA PENTRU MASINĂ (Ora de sosire țintă + Ceasul sistemului)
    LaunchedEffect(state.drivingSimulator.isDrivingModeActive, state.traseuGeoJson) {
        val traseuSigur = state.traseuGeoJson
        if (state.drivingSimulator.isDrivingModeActive && traseuSigur != null && state.durataTraseuSecunde > 0) {
            state.drivingSimulator.startDriving(state.durataTraseuSecunde)
            var elapsed = 0.0
            // Run loop while active and hasn't exceeded total expected duration + buffer
            val limit = state.durataTraseuSecunde + maxOf(0.0, state.timpDisponibilSecunde - state.durataTraseuSecunde)
            while (state.drivingSimulator.isDrivingModeActive && elapsed <= limit) {
                state.drivingSimulator.tickDriving(
                    route = traseuSigur,
                    elapsedSeconds = elapsed,
                    durationSeconds = state.durataTraseuSecunde,
                    targetSeconds = state.timpDisponibilSecunde
                )
                delay(1000L)
                elapsed++
            }
        }
    }

    // 5. AFISARE NOTIFICARE VREME LA SCHIMBAREA TRASEULUI
    LaunchedEffect(state.traseuGeoJson) {
        val traseuSigur = state.traseuGeoJson
        if (traseuSigur != null) {
            if (state.alerteMeteo.isNotEmpty()) {
                Toast.makeText(context, "Atenție! S-au detectat condiții meteo pe traseu!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Traseu perfect curat, vreme excelentă!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pozitieScrubbing = remember(state.traseuGeoJson, state.valoareScrubbingSecunde, state.durataTraseuSecunde) {
        val traseu = state.traseuGeoJson
        if (traseu != null && state.durataTraseuSecunde > 0) {
            val distantaTotala = TurfMeasurement.length(traseu, TurfConstants.UNIT_METERS)
            val secundeCurente = if (state.valoareScrubbingSecunde < 0.0) 0.0 else state.valoareScrubbingSecunde
            val fractie = secundeCurente / state.durataTraseuSecunde
            val distantaCurenta = fractie * distantaTotala
            TurfMeasurement.along(traseu, distantaCurenta, TurfConstants.UNIT_METERS)
        } else {
            null
        }
    }

    val weatherInfoScrubbing = remember(state.puncteVremeTraseu, state.valoareScrubbingSecunde) {
        if (state.puncteVremeTraseu.isNotEmpty()) {
            val minuteScrubbing = (state.valoareScrubbingSecunde / 60.0).toInt()
            state.puncteVremeTraseu.minByOrNull { kotlin.math.abs((it.minuteDeLaPlecare ?: 0) - minuteScrubbing) }
        } else {
            null
        }
    }

    val fazaLuminaScrubbing = remember(pozitieScrubbing, state.valoareScrubbingSecunde) {
        val punct = pozitieScrubbing
        if (punct != null) {
            val timpSosireAiciMs = System.currentTimeMillis() + (state.valoareScrubbingSecunde * 1000).toLong()
            val altitudine = SunCalculator.calculeazaAltitudineSoareViitor(punct.latitude(), punct.longitude(), timpSosireAiciMs)
            when {
                altitudine > 0.0 -> "Ziua"
                altitudine in -6.0..0.0 -> "Apus / Crepuscul Civil"
                altitudine in -12.0..-6.0 -> "Crepuscul Nautic"
                altitudine in -18.0..-12.0 -> "Crepuscul Astronomic"
                else -> "Noapte Deplină"
            }
        } else {
            ""
        }
    }

    val activeRainbowCloudsUrl = remember(state.valoareScrubbingSecunde, state.rainbowSnapshotTimestamp, state.modScrubbingActiv) {
        val rainbowKey = BuildConfig.RAINBOW_API_KEY
        if (state.modScrubbingActiv && state.valoareScrubbingSecunde > 0 && rainbowKey.isNotBlank() && rainbowKey != "your_rainbow_api_key_here" && state.rainbowSnapshotTimestamp != null) {
            val offsetSec = state.valoareScrubbingSecunde.toLong()
            val snapshot = state.rainbowSnapshotTimestamp
            val forecastTime = ((offsetSec / 600) * 600).coerceIn(0, 14400)
            "https://api.rainbow.ai/tiles/v1/precip/$snapshot/$forecastTime/{z}/{x}/{y}?token=$rainbowKey"
        } else {
            null
        }
    }

    val currentZoom = state.mapViewportState.cameraState?.zoom ?: 0.0
    val isFlat = currentZoom >= 3.0

    Box(modifier = modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = state.mapViewportState,
        ) {
            // Efect pentru setup si RainViewer
            // Efect pentru setup si locatia mea
            MapEffect(Unit) { mapView ->
                mapView.location.updateSettings {
                    enabled = true
                    pulsingEnabled = true
                }

                mapView.location.addOnIndicatorPositionChangedListener { punctNou ->
                    state.locatieCurenta = punctNou
                }
            }

            // Efect pentru RainViewer (radar dinamic cu istoric si prezent)
            MapEffect(state.activeRadarUrl) { mapView ->
                val style = mapView.getMapboxMap().getStyle()
                val url = state.activeRadarUrl
                if (style != null) {
                    val sursaId = "sursa-ploaie"
                    val stratId = "strat-ploaie"

                    if (style.styleLayerExists(stratId)) {
                        style.removeStyleLayer(stratId)
                    }
                    if (style.styleSourceExists(sursaId)) {
                        style.removeStyleSource(sursaId)
                    }

                    if (url != null) {
                        val sursaVreme = rasterSource(sursaId) {
                            tiles(listOf(url))
                            tileSize(256)
                            maxzoom(6)
                        }
                        style.addSource(sursaVreme)

                        val stratVreme = rasterLayer(stratId, sursaId) {
                            rasterOpacity(0.8)
                        }
                        style.addLayer(stratVreme)
                    }
                }
            }

            // Efect pentru hărți de nori în viitor (Rainbow.ai)
            MapEffect(activeRainbowCloudsUrl) { mapView ->
                val style = mapView.getMapboxMap().getStyle()
                val url = activeRainbowCloudsUrl
                android.util.Log.d("WeatherRepository", "activeRainbowCloudsUrl: $url")
                if (style != null) {
                    val sursaId = "sursa-nori"
                    val stratId = "strat-nori"

                    if (style.styleLayerExists(stratId)) {
                        style.removeStyleLayer(stratId)
                    }
                    if (style.styleSourceExists(sursaId)) {
                        style.removeStyleSource(sursaId)
                    }

                    if (url != null) {
                        val sursaNori = rasterSource(sursaId) {
                            tiles(listOf(url))
                            tileSize(256)
                            maxzoom(6)
                        }
                        style.addSource(sursaNori)

                        val stratNori = rasterLayer(stratId, sursaId) {
                            rasterOpacity(0.6)
                        }
                        style.addLayer(stratNori)
                    }
                }
            }

            // Efect pentru linia de avertizare
            MapEffect(state.safeZoneManager.linieAvertizare) { mapView ->
                val style = mapView.getMapboxMap().getStyle()
                if (style != null) {
                    val sursaId = "sursa-linie-avertizare"
                    val stratId = "strat-linie-avertizare"

                    if (state.safeZoneManager.linieAvertizare != null) {
                        if (style.styleSourceExists(sursaId)) {
                            val sursa = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(sursaId)
                            sursa?.geometry(state.safeZoneManager.linieAvertizare!!)
                        } else {
                            style.addSource(geoJsonSource(sursaId) {
                                geometry(state.safeZoneManager.linieAvertizare!!)
                            })
                            style.addLayer(lineLayer(stratId, sursaId) {
                                lineColor(android.graphics.Color.RED)
                                lineWidth(4.0)
                                lineDasharray(listOf(2.0, 2.0))
                            })
                        }
                    } else {
                        if (style.styleLayerExists(stratId)) style.removeStyleLayer(stratId)
                        if (style.styleSourceExists(sursaId)) style.removeStyleSource(sursaId)
                    }
                }
            }

            // Efect pentru trasee + auto zoom pe traseul selectat
            MapEffect(state.toateTraseele, state.indexTraseuSelectat) { mapView ->
                val style = mapView.getMapboxMap().getStyle()
                val mapboxMap = mapView.getMapboxMap()

                if (style != null) {
                    // Mai întâi eliminăm toate straturile și sursele vechi pentru trasee
                    for (i in 0..5) {
                        val sursaId = "sursa-traseu-$i"
                        val stratId = "strat-traseu-$i"
                        val sursaBorduraId = "sursa-traseu-bordura-$i"
                        val stratBorduraId = "strat-traseu-bordura-$i"
                        if (style.styleLayerExists(stratId)) style.removeStyleLayer(stratId)
                        if (style.styleSourceExists(sursaId)) style.removeStyleSource(sursaId)
                        if (style.styleLayerExists(stratBorduraId)) style.removeStyleLayer(stratBorduraId)
                        if (style.styleSourceExists(sursaBorduraId)) style.removeStyleSource(sursaBorduraId)
                    }

                    if (state.toateTraseele.isNotEmpty()) {
                        // Desenăm mai întâi traseele NESELECTATE, ca să fie dedesubt
                        state.toateTraseele.forEachIndexed { index, traseu ->
                            if (index != state.indexTraseuSelectat) {
                                val sursaId = "sursa-traseu-$index"
                                val stratId = "strat-traseu-$index"
                                style.addSource(geoJsonSource(sursaId) {
                                    geometry(traseu.geoJson)
                                })
                                style.addLayer(lineLayer(stratId, sursaId) {
                                    lineColor(android.graphics.Color.parseColor("#9E9E9E"))
                                    lineWidth(4.5)
                                    lineCap(LineCap.ROUND)
                                    lineJoin(LineJoin.ROUND)
                                })
                            }
                        }

                        // Desenăm apoi traseul SELECTAT cu culori de trafic, ca să fie deasupra
                        val selectat = state.toateTraseele.getOrNull(state.indexTraseuSelectat)
                        if (selectat != null) {
                            val index = state.indexTraseuSelectat
                            val sursaId = "sursa-traseu-$index"
                            val stratId = "strat-traseu-$index"
                            val sursaBorduraId = "sursa-traseu-bordura-$index"
                            val stratBorduraId = "strat-traseu-bordura-$index"

                            // Contur/Bordura albastră sub linia de trafic
                            style.addSource(geoJsonSource(sursaBorduraId) {
                                geometry(selectat.geoJson)
                            })
                            style.addLayer(lineLayer(stratBorduraId, sursaBorduraId) {
                                lineColor(android.graphics.Color.parseColor("#1565C0"))
                                lineWidth(9.0)
                                lineCap(LineCap.ROUND)
                                lineJoin(LineJoin.ROUND)
                            })

                            if (selectat.traficGeoJson != null) {
                                style.addSource(geoJsonSource(sursaId) {
                                    featureCollection(selectat.traficGeoJson)
                                })
                                style.addLayer(lineLayer(stratId, sursaId) {
                                    lineColor(com.mapbox.maps.extension.style.expressions.generated.Expression.get("color"))
                                    lineWidth(6.0)
                                    lineCap(LineCap.ROUND)
                                    lineJoin(LineJoin.ROUND)
                                })
                            } else {
                                style.addSource(geoJsonSource(sursaId) {
                                    geometry(selectat.geoJson)
                                })
                                style.addLayer(lineLayer(stratId, sursaId) {
                                    lineColor(android.graphics.Color.parseColor("#2196F3"))
                                    lineWidth(6.0)
                                    lineCap(LineCap.ROUND)
                                    lineJoin(LineJoin.ROUND)
                                })
                            }

                            val padding = com.mapbox.maps.EdgeInsets(200.0, 100.0, 150.0, 100.0)
                            val cameraOptions = mapboxMap.cameraForGeometry(
                                selectat.geoJson,
                                padding,
                                null,
                                null
                            )
                            state.mapViewportState.setCameraOptions(cameraOptions)
                        }
                    }
                }
            }

            // Efect pentru masina
            MapEffect(state.drivingSimulator.pozitieMasinaAnimata) { mapView ->
                val style = mapView.getMapboxMap().getStyle()
                if (style != null && state.drivingSimulator.pozitieMasinaAnimata != null) {
                    val sursaId = "sursa-masina-animata"
                    val stratId = "strat-masina-animata"

                    if (style.styleSourceExists(sursaId)) {
                        val sursa = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(sursaId)
                        sursa?.geometry(state.drivingSimulator.pozitieMasinaAnimata!!)
                    } else {
                        style.addSource(geoJsonSource(sursaId) {
                            geometry(state.drivingSimulator.pozitieMasinaAnimata!!)
                        })

                        style.addLayer(com.mapbox.maps.extension.style.layers.generated.circleLayer(stratId, sursaId) {
                            circleRadius(8.0)
                            circleColor(android.graphics.Color.YELLOW)
                            circleStrokeWidth(2.0)
                            circleStrokeColor(android.graphics.Color.BLACK)
                        })
                    }
                } else if (style != null && !state.drivingSimulator.isDrivingModeActive) {
                    if (style.styleLayerExists("strat-masina-animata")) style.removeStyleLayer("strat-masina-animata")
                    if (style.styleSourceExists("sursa-masina-animata")) style.removeStyleSource("sursa-masina-animata")
                }
            }

            // Efect pentru punctul de scrubbing/slider
            MapEffect(state.modScrubbingActiv, pozitieScrubbing) { mapView ->
                val style = mapView.getMapboxMap().getStyle()
                if (style != null && state.modScrubbingActiv && pozitieScrubbing != null) {
                    val sursaId = "sursa-punct-scrubbing"
                    val stratId = "strat-punct-scrubbing"

                    if (style.styleSourceExists(sursaId)) {
                        val sursa = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(sursaId)
                        sursa?.geometry(pozitieScrubbing)
                    } else {
                        style.addSource(geoJsonSource(sursaId) {
                            geometry(pozitieScrubbing)
                        })

                        style.addLayer(com.mapbox.maps.extension.style.layers.generated.circleLayer(stratId, sursaId) {
                            circleRadius(8.0)
                            circleColor(android.graphics.Color.parseColor("#00FFCC"))
                            circleStrokeWidth(2.0)
                            circleStrokeColor(android.graphics.Color.BLACK)
                        })
                    }
                } else if (style != null) {
                    if (style.styleLayerExists("strat-punct-scrubbing")) style.removeStyleLayer("strat-punct-scrubbing")
                    if (style.styleSourceExists("sursa-punct-scrubbing")) style.removeStyleSource("sursa-punct-scrubbing")
                }
            }

            // Efect pentru cercul dinamic
            MapEffect(state.safeZoneManager.circleRadiusMeters, state.safeZoneManager.isSafeZoneModeActive, state.pozitiePin) { mapView ->
                val style = mapView.getMapboxMap().getStyle()

                if (style != null) {
                    val sursaId = "sursa-zona-sigura"
                    val stratId = "strat-zona-sigura"

                    if (state.safeZoneManager.isSafeZoneModeActive && state.safeZoneManager.circleRadiusMeters > 0 && state.pozitiePin != null) {
                        val poligonCerc = TurfTransformation.circle(
                            state.pozitiePin!!,
                            state.safeZoneManager.circleRadiusMeters,
                            360,
                            TurfConstants.UNIT_METERS
                        )

                        if (style.styleSourceExists(sursaId)) {
                            val sursa = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(sursaId)
                            sursa?.geometry(poligonCerc)
                        } else {
                            style.addSource(geoJsonSource(sursaId) {
                                geometry(poligonCerc)
                            })

                            style.addLayer(fillLayer(stratId, sursaId) {
                                fillColor(android.graphics.Color.parseColor("#40FF0000"))
                                fillOutlineColor(android.graphics.Color.RED)
                            })
                        }
                    } else {
                        if (style.styleLayerExists(stratId)) style.removeStyleLayer(stratId)
                        if (style.styleSourceExists(sursaId)) style.removeStyleSource(sursaId)
                    }
                }
            }

            // Efect pentru liniile scurte de tranzitie noapte
            MapEffect(state.alerteNoapte) { mapView ->
                mapView.getMapboxMap().getStyle { style ->
                    for (i in 0..10) {
                        if (style.styleLayerExists("strat-tranzitie-$i")) style.removeStyleLayer("strat-tranzitie-$i")
                        if (style.styleSourceExists("sursa-tranzitie-$i")) style.removeStyleSource("sursa-tranzitie-$i")
                    }

                    state.alerteNoapte.forEachIndexed { index, alerta ->
                        if (alerta.linieTranzitie != null) {
                            val sursaId = "sursa-tranzitie-$index"
                            val stratId = "strat-tranzitie-$index"

                            val culoareHex = when (alerta.tip) {
                                "Apus / Crepuscul Civil" -> "#FF9800"
                                "Crepuscul Nautic"       -> "#3F51B5"
                                "Crepuscul Astronomic"   -> "#1A237E"
                                else                     -> "#000000"
                            }

                            style.addSource(geoJsonSource(sursaId) {
                                geometry(alerta.linieTranzitie)
                            })

                            style.addLayer(lineLayer(stratId, sursaId) {
                                lineColor(android.graphics.Color.parseColor(culoareHex))
                                lineWidth(4.0)
                                lineDasharray(listOf(2.0, 2.0))
                            })
                        }
                    }
                }
            }

            // Efect pentru umbre (crepuscule + noapte)
            MapEffect(state.umbraCivil, state.umbraNautic, state.umbraAstro, state.umbraNoapte) { mapView ->
                mapView.getMapboxMap().getStyle { style ->
                    val straturi = listOf(
                        Triple("civil", state.umbraCivil, "#15000022"),
                        Triple("nautic", state.umbraNautic, "#25000022"),
                        Triple("astro", state.umbraAstro, "#35000022"),
                        Triple("noapte", state.umbraNoapte, "#50000022")
                    )

                    for ((id, poligon, culoareHex) in straturi) {
                        if (poligon != null) {
                            val sursaId = "sursa-$id"
                            val stratId = "strat-$id"

                            if (style.styleSourceExists(sursaId)) {
                                val sursa = style.getSourceAs<com.mapbox.maps.extension.style.sources.generated.GeoJsonSource>(sursaId)
                                sursa?.geometry(poligon)
                            } else {
                                style.addSource(geoJsonSource(sursaId) {
                                    geometry(poligon)
                                })
                                style.addLayer(fillLayer(stratId, sursaId) {
                                    fillColor(android.graphics.Color.parseColor(culoareHex))
                                })
                            }
                        }
                    }
                }
            }

            // Pin destinatie
            if (state.pozitiePin != null) {
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(state.pozitiePin!!)
                        allowOverlap(true)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Pin Destinatie",
                        tint = Color.Red,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Pin-uri Meteo
            state.alerteMeteo.forEach { alerta ->
                val (culoareFundal, iconitaAlesa) = when (alerta.tip) {
                    "Ploaie" -> Color(0xFF4287F5) to Icons.Default.WaterDrop
                    "Zăpadă" -> Color.Cyan to Icons.Default.AcUnit
                    "Ceață" -> Color.Gray to Icons.Default.Warning
                    "Nori" -> Color.DarkGray to Icons.Default.Cloud
                    else -> Color.Black to Icons.Default.Warning
                }

                val textAfisat = if (alerta.minuteDeLaPlecare != null) {
                    "${alerta.mesaj} (în ${alerta.minuteDeLaPlecare} min)"
                } else {
                    alerta.mesaj
                }

                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(alerta.punct)
                        allowOverlap(true)
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = textAfisat,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(culoareFundal.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Icon(
                            imageVector = iconitaAlesa,
                            contentDescription = textAfisat,
                            tint = culoareFundal,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Pin-uri Crepuscul / Noapte
            state.alerteNoapte.forEach { alerta ->
                val (culoareFundal, iconitaAlesa) = when (alerta.tip) {
                    "Apus / Crepuscul Civil" -> Color(0xFFFF9800) to Icons.Default.WbTwilight
                    "Crepuscul Nautic"       -> Color(0xFF3F51B5) to Icons.Default.Brightness3
                    "Crepuscul Astronomic"   -> Color(0xFF1A237E) to Icons.Default.Brightness3
                    "Noapte Deplină"         -> Color(0xFF000000) to Icons.Default.Brightness3
                    else                     -> Color.DarkGray to Icons.Default.WbTwilight
                }

                val textAfisat = if (alerta.minuteDeLaPlecare != null) {
                    "${alerta.mesaj} (în ${alerta.minuteDeLaPlecare} min)"
                } else {
                    alerta.mesaj
                }

                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(alerta.punct)
                        allowOverlap(true)
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = textAfisat,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(culoareFundal.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Icon(
                            imageVector = iconitaAlesa,
                            contentDescription = textAfisat,
                            tint = culoareFundal,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Pin-uri Incidente TomTom
            state.incidenteTomTom.forEach { incident ->
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(incident.locatie)
                        allowOverlap(true)
                    }
                ) {
                    val intarziereMin = incident.intarziereSecunde / 60
                    val textAfisat = if (intarziereMin > 0) {
                        "${incident.iconitaEmoji} ${incident.titlu} (+$intarziereMin min)"
                    } else {
                        "${incident.iconitaEmoji} ${incident.titlu}"
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            Toast.makeText(context, "${incident.titlu}: ${incident.descriere}", Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Text(
                            text = textAfisat,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0xFFD32F2F).copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Pin intarziere secunde
            if (state.safeZoneManager.punctMijlocLinie != null) {
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(state.safeZoneManager.punctMijlocLinie!!)
                        allowOverlap(true)
                    }
                ) {
                    Text(
                        text = state.safeZoneManager.textSecundeInUrma,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Red, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Pin pentru pozitia simulata in modul scrubbing/slider
            if (state.modScrubbingActiv && pozitieScrubbing != null) {
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(pozitieScrubbing)
                        allowOverlap(true)
                        annotationAnchor {
                            anchor(ViewAnnotationAnchor.BOTTOM)
                        }
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.9f)),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val tipVreme = weatherInfoScrubbing?.tip ?: "Cer senin"
                                val weatherIcon = when (tipVreme) {
                                    "Ploaie" -> Icons.Default.WaterDrop
                                    "Zăpadă" -> Icons.Default.AcUnit
                                    "Ceață" -> Icons.Default.Warning
                                    "Nori" -> Icons.Default.Cloud
                                    "Nori parțiali" -> Icons.Default.Cloud
                                    else -> Icons.Default.WbSunny
                                }
                                val weatherColor = when (tipVreme) {
                                    "Zăpadă" -> Color(0xFF00E5FF)
                                    "Ceață" -> Color(0xFF9E9E9E)
                                    "Ploaie" -> Color(0xFF2979FF)
                                    "Nori" -> Color(0xFF757575)
                                    "Nori parțiali" -> Color(0xFFFFB300)
                                    else -> Color(0xFFFFD600)
                                }

                                val lightIcon = when {
                                    fazaLuminaScrubbing.contains("Ziua") -> Icons.Default.WbSunny
                                    fazaLuminaScrubbing.contains("Apus") || fazaLuminaScrubbing.contains("Crepuscul Civil") -> Icons.Default.WbTwilight
                                    else -> Icons.Default.Brightness3
                                }
                                val lightColor = when {
                                    fazaLuminaScrubbing.contains("Ziua") -> Color(0xFFFFD600)
                                    fazaLuminaScrubbing.contains("Apus") || fazaLuminaScrubbing.contains("Crepuscul Civil") -> Color(0xFFFF9800)
                                    else -> Color(0xFF3F51B5)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = weatherIcon,
                                        contentDescription = null,
                                        tint = weatherColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = tipVreme,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = lightIcon,
                                        contentDescription = null,
                                        tint = lightColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = fazaLuminaScrubbing,
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        // --- OVERLAY INTERFACE ELEMENTS ---

        if (state.baraEsteFocusata) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                    }
            )
        }

        // Panou Căutare + Rute Alternative
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            SearchBar(
                state = state,
                permissionLauncher = {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            )

            // Afișăm rutele alternative dacă există
            if (state.toateTraseele.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.toateTraseele.forEachIndexed { index, traseu ->
                        val esteSelectat = index == state.indexTraseuSelectat
                        
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { state.selecteazaTraseulDirect(index) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (esteSelectat) Color(0xFF2196F3) else Color.White.copy(alpha = 0.9f)
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (esteSelectat) 8.dp else 2.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val tipTraseu = if (index == 0) "Ruta 1" else "Ruta ${index + 1}"
                                Text(
                                    text = tipTraseu,
                                    fontWeight = FontWeight.Bold,
                                    color = if (esteSelectat) Color.White else Color.Black,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                val distKm = String.format(Locale.US, "%.1f km", traseu.distantaMetri / 1000.0)
                                val minTotale = (traseu.durataSecunde / 60).toInt()
                                val ore = minTotale / 60
                                val min = minTotale % 60
                                val timpStr = if (ore > 0) "${ore}h ${min}m" else "${min} min"
                                
                                Text(
                                    text = "$distKm • $timpStr",
                                    color = if (esteSelectat) Color.White.copy(alpha = 0.8f) else Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                val repVreme = getRepresentativeWeather(traseu.puncteVreme)
                                val (weatherIcon, weatherColor) = when (repVreme) {
                                    "Zăpadă" -> Icons.Default.AcUnit to Color(0xFF00E5FF)
                                    "Ceață" -> Icons.Default.Warning to Color(0xFF9E9E9E)
                                    "Ploaie" -> Icons.Default.WaterDrop to Color(0xFF2979FF)
                                    "Nori" -> Icons.Default.Cloud to Color(0xFF757575)
                                    "Nori parțiali" -> Icons.Default.Cloud to Color(0xFFFFB300)
                                    else -> Icons.Default.WbSunny to Color(0xFFFFD600)
                                }
                                val factorMeteoStr = String.format(Locale.US, "%.2fx", traseu.factorMeteo)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = weatherIcon,
                                        contentDescription = repVreme,
                                        tint = if (esteSelectat) Color.White else weatherColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = factorMeteoStr,
                                        color = if (esteSelectat) Color.White.copy(alpha = 0.9f) else Color.DarkGray,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                val scorStr = String.format(Locale.US, "Cost: %.1f", traseu.scor)
                                Text(
                                    text = scorStr,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (esteSelectat) Color.White else Color(0xFF1976D2),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Formula: Cost = Distanță (km) + (Factor Meteo × Timp (min))",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        // Buton Locatia Mea
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    state.gasesteLocatiaMea()
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Locația mea")
        }

        // Buton Clear Traseu
        if (state.traseuGeoJson != null) {
            Button(
                onClick = { state.curataTraseu() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 70.dp, end = 16.dp)
                    .size(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = ButtonDefaults.buttonElevation(4.dp)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Stergere traseu")
            }
        }

        // Buton Setare Zona / Timer
        Button(
            onClick = { state.showTimerDialog = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFA500),
                contentColor = Color.White
            )
        ) {
            Icon(imageVector = Icons.Default.Timer, contentDescription = "Mod Safe Zone")
        }

        // Buton Preview Traseu / Slider timp viitor
        if (state.traseuGeoJson != null) {
            Button(
                onClick = {
                    state.modScrubbingActiv = !state.modScrubbingActiv
                    state.valoareScrubbingSecunde = 0.0
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 70.dp, start = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.modScrubbingActiv) Color(0xFF0080FF) else Color(0xFF4CAF50),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (state.modScrubbingActiv) Icons.Default.Cancel else Icons.Default.DirectionsCar,
                    contentDescription = "Simulare / Prognoză Traseu"
                )
            }
        }

        // Card cu Slider pentru mod preview
        if (state.modScrubbingActiv && state.traseuGeoJson != null && state.durataTraseuSecunde > 0) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 80.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val textTimpOffset = if (state.valoareScrubbingSecunde < 0.0) {
                        val absMinute = kotlin.math.abs((state.valoareScrubbingSecunde / 60.0).toInt())
                        "Istoric radar: -$absMinute min (Mașina la start)"
                    } else {
                        val minuteScrubbing = (state.valoareScrubbingSecunde / 60.0).toInt()
                        val oreScrubbing = minuteScrubbing / 60
                        val restMinute = minuteScrubbing % 60
                        if (oreScrubbing > 0) {
                            "Prognoză peste: $oreScrubbing h $restMinute min"
                        } else {
                            "Prognoză peste: $restMinute min"
                        }
                    }

                    // Calculăm ora efectivă a sosirii în acel punct (sau din trecut)
                    val calendar = java.util.Calendar.getInstance()
                    calendar.add(java.util.Calendar.SECOND, state.valoareScrubbingSecunde.toInt())
                    val oraFormatata = String.format(java.util.Locale.getDefault(), "%02d:%02d", calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$textTimpOffset (Ora: $oraFormatata)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                state.modScrubbingActiv = false
                                state.valoareScrubbingSecunde = 0.0
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Închide previzualizare",
                                tint = Color.Gray
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = state.valoareScrubbingSecunde.toFloat(),
                        onValueChange = { state.valoareScrubbingSecunde = it.toDouble() },
                        valueRange = -3600f..state.durataTraseuSecunde.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.valoareScrubbingSecunde > 0 && activeRainbowCloudsUrl == null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pentru a vedea hărțile de nori din viitor, adaugă în local.properties:\n" +
                                   "• RAINBOW_API_KEY (de pe developer.rainbow.ai - are nowcast gratuit de 4h, cere card)",
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Text Detalii zoom
        if (!isFlat) {
            Text(
                text = "Mareste pentru a vedea mai multe detalii",
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 16.dp)
                    .alpha(0.7f)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 40.dp, vertical = 10.dp)
            )
        }

        // Timer popup dialog
        if (state.showTimerDialog) {
            TimerDialog(
                state = state,
                onDismiss = { state.showTimerDialog = false }
            )
        }

        // AFISARE TIMP ESTIMAT / ETA SAU STATUS MASINA
        val textAfisat = when {
            state.modScrubbingActiv -> null
            state.drivingSimulator.isDrivingModeActive && state.drivingSimulator.statusMasina.isNotEmpty() -> state.drivingSimulator.statusMasina
            state.traseuGeoJson != null && state.durataTraseuSecunde > 0 -> {
                val minuteTotale = (state.durataTraseuSecunde / 60).toInt()
                val ore = minuteTotale / 60
                val minute = minuteTotale % 60
                if (ore > 0) "Timp estimat: $ore h $minute min" else "Timp estimat: $minute min"
            }
            else -> null
        }

        if (textAfisat != null) {
            Text(
                text = textAfisat,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 50.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
