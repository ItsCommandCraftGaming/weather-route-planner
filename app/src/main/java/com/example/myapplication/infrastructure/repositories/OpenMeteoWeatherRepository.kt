package com.example.myapplication.infrastructure.repositories

import android.content.Context
import com.example.myapplication.application.interfaces.IWeatherRepository
import com.example.myapplication.domain.entities.AlertaMeteo
import com.example.myapplication.domain.entities.RadarFrame
import com.example.myapplication.infrastructure.api.OpenMeteoApi
import com.example.myapplication.infrastructure.api.RainViewerApi
import com.example.myapplication.infrastructure.api.RainbowApi
import com.mapbox.geojson.Point
import org.json.JSONObject

class OpenMeteoWeatherRepository(
    private val context: Context? = null,
    private val openMeteoApi: OpenMeteoApi = OpenMeteoApi(),
    private val rainViewerApi: RainViewerApi = RainViewerApi(),
    private val rainbowApi: RainbowApi = RainbowApi()
) : IWeatherRepository {

    private var cachedSnapshot: Long? = null
    private var lastFetchTime: Long = 0L

    private fun getCachedSnapshotFallback(): Long? {
        if (cachedSnapshot != null) {
            return cachedSnapshot
        }
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("RainbowCachePrefs", Context.MODE_PRIVATE)
            val savedSnapshot = prefs.getLong("rainbow_snapshot", -1L)
            if (savedSnapshot != -1L) {
                return savedSnapshot
            }
        }
        return null
    }

    override suspend fun getRainViewerUrl(): String? {
        return rainViewerApi.fetchRainViewerUrl()
    }

    override suspend fun getRainViewerFrames(): List<RadarFrame> {
        val rawFrames = rainViewerApi.fetchRainViewerFrames()
        return rawFrames.map { RadarFrame(it.first, it.second) }
    }

    override suspend fun checkWeatherForPoint(point: Point, timeMs: Long): AlertaMeteo? {
        val lat = point.latitude()
        val lon = point.longitude()
        val response = openMeteoApi.fetchForecast(lat, lon) ?: return null
        return try {
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
                ninsoare > 0.0 -> AlertaMeteo(point, "Zăpadă", "Ninsoare", ninsoareValoare = ninsoare)
                precipitatii > 0.0 -> AlertaMeteo(point, "Ploaie", "Ploaie", precipitatiiValoare = precipitatii)
                vizibilitate < 2000.0 -> AlertaMeteo(point, "Ceață", "Ceață")
                nori > 85 -> AlertaMeteo(point, "Nori", "Nori denși")
                nori > 20 -> AlertaMeteo(point, "Nori parțiali", "Nori parțiali")
                else -> AlertaMeteo(point, "Cer senin", "Cer senin")
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getRainbowSnapshot(apiKey: String): Long? {
        val currentTime = System.currentTimeMillis()

        // 1. Check memory cache
        cachedSnapshot?.let { snapshot ->
            if (currentTime - lastFetchTime < 10 * 60 * 1000) { // 10 minutes
                android.util.Log.d("WeatherRepository", "Returning cached Rainbow snapshot (memory): $snapshot")
                return snapshot
            }
        }

        // 2. Check SharedPreferences cache
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("RainbowCachePrefs", Context.MODE_PRIVATE)
            val savedSnapshot = prefs.getLong("rainbow_snapshot", -1L)
            val savedFetchTime = prefs.getLong("rainbow_fetch_time", 0L)
            if (savedSnapshot != -1L && (currentTime - savedFetchTime < 10 * 60 * 1000)) {
                android.util.Log.d("WeatherRepository", "Returning cached Rainbow snapshot (prefs): $savedSnapshot")
                cachedSnapshot = savedSnapshot
                lastFetchTime = savedFetchTime
                return savedSnapshot
            }
        }

        return try {
            val snapshot = rainbowApi.fetchRainbowSnapshot(apiKey)
            if (snapshot != null) {
                android.util.Log.d("WeatherRepository", "Rainbow snapshot success: $snapshot")

                // Update caches
                cachedSnapshot = snapshot
                lastFetchTime = currentTime
                context?.let { ctx ->
                    ctx.getSharedPreferences("RainbowCachePrefs", Context.MODE_PRIVATE)
                        .edit()
                        .putLong("rainbow_snapshot", snapshot)
                        .putLong("rainbow_fetch_time", currentTime)
                        .apply()
                }

                snapshot
            } else {
                val fallback = getCachedSnapshotFallback()
                if (fallback != null) {
                    android.util.Log.w("WeatherRepository", "Falling back to expired cached snapshot: $fallback")
                    fallback
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "Rainbow snapshot exception: ${e.message}", e)
            val fallback = getCachedSnapshotFallback()
            if (fallback != null) {
                android.util.Log.w("WeatherRepository", "Exception occurred. Falling back to cached snapshot: $fallback")
                fallback
            } else {
                null
            }
        }
    }
}
