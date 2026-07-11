package com.example.myapplication.data.classes

import com.example.myapplication.data.interfaces.IRouteWeatherScanner
import com.example.myapplication.data.interfaces.IWeatherRepository
import com.example.myapplication.model.AlertaMeteo
import com.mapbox.geojson.LineString
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class RouteWeatherScannerImpl(private val weatherRepository: IWeatherRepository) : IRouteWeatherScanner {

    override suspend fun scanWeather(route: LineString, durationSeconds: Double): List<AlertaMeteo> {
        val distantaTotala = TurfMeasurement.length(route, TurfConstants.UNIT_METERS)
        val pasMetri = getStepDistance(distantaTotala)
        var distantaCurenta = 0.0
        val timpPlecareMs = System.currentTimeMillis()
        val vitezaMetriPeSecunda = if (durationSeconds > 0) distantaTotala / durationSeconds else 1.0
        val listaAlerteGasite = mutableListOf<AlertaMeteo>()

        withContext(Dispatchers.IO) {
            val joburi = mutableListOf<Deferred<AlertaMeteo?>>()
            while (distantaCurenta <= distantaTotala) {
                val punctScanat = TurfMeasurement.along(route, distantaCurenta, TurfConstants.UNIT_METERS)
                val secundePanaAici = distantaCurenta / vitezaMetriPeSecunda
                val timpSosireAiciMs = timpPlecareMs + (secundePanaAici * 1000).toLong()
                val minDeLaPlecare = (secundePanaAici / 60.0).toInt()

                val job = async {
                    weatherRepository.checkWeatherForPoint(punctScanat, timpSosireAiciMs)?.copy(
                        minuteDeLaPlecare = minDeLaPlecare
                    )
                }
                joburi.add(job)
                distantaCurenta += pasMetri
            }
            listaAlerteGasite.addAll(joburi.awaitAll().filterNotNull())
        }
        return listaAlerteGasite
    }

    override suspend fun scanNightTransitions(route: LineString, durationSeconds: Double): List<AlertaMeteo> {
        val distantaTotala = TurfMeasurement.length(route, TurfConstants.UNIT_METERS)
        val pasMetri = getStepDistance(distantaTotala)
        var distantaCurenta = 0.0
        val timpPlecareMs = System.currentTimeMillis()
        val vitezaMetriPeSecunda = if (durationSeconds > 0) distantaTotala / durationSeconds else 1.0
        val listaAlerteNoapte = mutableListOf<AlertaMeteo>()
        var stadiuLuminaCurent = "Ziua"

        withContext(Dispatchers.Default) {
            while (distantaCurenta <= distantaTotala) {
                val punct = TurfMeasurement.along(route, distantaCurenta, TurfConstants.UNIT_METERS)
                val secundePanaAici = distantaCurenta / vitezaMetriPeSecunda
                val timpSosireAiciMs = timpPlecareMs + (secundePanaAici * 1000).toLong()

                val altitudine = SunCalculator.calculeazaAltitudineSoareViitor(punct.latitude(), punct.longitude(), timpSosireAiciMs)

                val stadiuNou = when {
                    altitudine > 0.0 -> "Ziua"
                    altitudine in -6.0..0.0 -> "Apus / Crepuscul Civil"
                    altitudine in -12.0..-6.0 -> "Crepuscul Nautic"
                    altitudine in -18.0..-12.0 -> "Crepuscul Astronomic"
                    else -> "Noapte Deplină"
                }

                if (stadiuNou != stadiuLuminaCurent && stadiuNou != "Ziua") {
                    if (altitudine < 0) {
                        val unghiLinie = SunCalculator.calculeazaUnghiTerminator(punct.latitude(), punct.longitude(), timpSosireAiciMs)
                        val p1 = TurfMeasurement.destination(punct, 30000.0, unghiLinie, TurfConstants.UNIT_METERS)
                        val p2 = TurfMeasurement.destination(punct, 30000.0, unghiLinie + 180.0, TurfConstants.UNIT_METERS)
                        val linieScurta = LineString.fromLngLats(listOf(p1, p2))

                        val minDeLaPlecare = (secundePanaAici / 60.0).toInt()
                        listaAlerteNoapte.add(AlertaMeteo(punct, stadiuNou, "Aici începe: $stadiuNou", linieScurta, minDeLaPlecare))
                    }
                }

                stadiuLuminaCurent = stadiuNou
                distantaCurenta += pasMetri
            }
        }
        return listaAlerteNoapte
    }

    private fun getStepDistance(totalDistance: Double): Double {
        return when {
            totalDistance < 20_000.0 -> 2000.0
            totalDistance < 100_000.0 -> 10_000.0
            totalDistance < 500_000.0 -> 25_000.0
            totalDistance < 1000_000.0 -> 50_000.0
            totalDistance < 2500_000.0 -> 100_000.0
            totalDistance < 5000_000.0 -> 200_000.0
            else -> 500_000.0
        }
    }
}
