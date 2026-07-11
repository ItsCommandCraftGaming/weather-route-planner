package com.example.myapplication.ui.components

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
import kotlinx.coroutines.*

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
            } else {
                // În viitor sau la momentul curent, afișăm cel mai recent radar (prezent)
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

    // 5. LOGICA PENTRU SCANARE METEO COMPLETĂ
    LaunchedEffect(state.traseuGeoJson, state.durataTraseuSecunde) {
        val traseuSigur = state.traseuGeoJson
        if (traseuSigur != null && state.durataTraseuSecunde > 0) {
            val totTraseul = state.routeWeatherScanner.scanWeather(traseuSigur, state.durataTraseuSecunde)
            state.puncteVremeTraseu = totTraseul
            state.alerteMeteo = totTraseul.filter { it.tip != "Cer senin" && it.tip != "Nori parțiali" }
            if (state.alerteMeteo.isNotEmpty()) {
                Toast.makeText(context, "Atenție! S-au detectat condiții meteo pe traseu!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Traseu perfect curat, vreme excelentă!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 6. LOGICA PENTRU SCANARE NOAPTE PE TRASEU
    LaunchedEffect(state.traseuGeoJson) {
        val traseuSigur = state.traseuGeoJson
        if (traseuSigur != null && state.durataTraseuSecunde > 0) {
            state.alerteNoapte = state.routeWeatherScanner.scanNightTransitions(traseuSigur, state.durataTraseuSecunde)
        } else {
            state.alerteNoapte = emptyList()
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
                if (style != null && url != null) {
                    val sursaId = "sursa-ploaie"
                    val stratId = "strat-ploaie"

                    if (style.styleLayerExists(stratId)) {
                        style.removeStyleLayer(stratId)
                    }
                    if (style.styleSourceExists(sursaId)) {
                        style.removeStyleSource(sursaId)
                    }

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

            // Efect pentru traseu + auto zoom
            MapEffect(state.traseuGeoJson) { mapView ->
                val style = mapView.getMapboxMap().getStyle()
                val mapboxMap = mapView.getMapboxMap()

                if (style != null) {
                    val sursaId = "sursa-traseu"
                    val stratId = "strat-traseu"

                    if (style.styleLayerExists(stratId)) style.removeStyleLayer(stratId)
                    if (style.styleSourceExists(sursaId)) style.removeStyleSource(sursaId)

                    if (state.traseuGeoJson != null) {
                        style.addSource(geoJsonSource(sursaId) {
                            geometry(state.traseuGeoJson!!)
                        })

                        style.addLayer(lineLayer(stratId, sursaId) {
                            lineColor(android.graphics.Color.BLUE)
                            lineWidth(6.0)
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                        })

                        val padding = com.mapbox.maps.EdgeInsets(200.0, 100.0, 150.0, 100.0)
                        val cameraOptions = mapboxMap.cameraForGeometry(
                            state.traseuGeoJson!!,
                            padding,
                            null,
                            null
                        )
                        state.mapViewportState.setCameraOptions(cameraOptions)
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
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.9f)),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(4.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val tipVreme = weatherInfoScrubbing?.tip ?: "Cer senin"
                                val emojiVreme = when (tipVreme) {
                                    "Ploaie" -> "🌧️"
                                    "Zăpadă" -> "❄️"
                                    "Ceață" -> "🌫️"
                                    "Nori" -> "☁️"
                                    "Nori parțiali" -> "⛅"
                                    else -> "☀️"
                                }
                                val emojiLumina = when {
                                    fazaLuminaScrubbing.contains("Ziua") -> "☀️"
                                    fazaLuminaScrubbing.contains("Apus") -> "🌆"
                                    fazaLuminaScrubbing.contains("Noapte") -> "🌃"
                                    else -> "🌙"
                                }

                                Text(
                                    text = "$emojiVreme $tipVreme",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "$emojiLumina $fazaLuminaScrubbing",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Pozitie simulată",
                            tint = Color(0xFF00FFCC),
                            modifier = Modifier.size(36.dp)
                        )
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

        // Bara de cautare
        SearchBar(
            state = state,
            permissionLauncher = {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

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

                    Text(
                        text = "$textTimpOffset (Ora: $oraFormatata)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = state.valoareScrubbingSecunde.toFloat(),
                        onValueChange = { state.valoareScrubbingSecunde = it.toDouble() },
                        valueRange = -3600f..state.durataTraseuSecunde.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
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
