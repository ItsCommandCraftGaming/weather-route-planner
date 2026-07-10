package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.myapplication.data.classes.OpenMeteoWeatherRepository
import com.example.myapplication.data.classes.SharedPrefsSearchHistoryRepository
import com.example.myapplication.ui.components.MapScreen
import com.example.myapplication.ui.state.rememberMapState
import com.mapbox.geojson.Point
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setează tokenul Mapbox programatic înainte de a inițializa orice element vizual Mapbox
        MapboxOptions.accessToken = BuildConfig.MAPBOX_SECRET_TOKEN

        val searchHistoryRepository = SharedPrefsSearchHistoryRepository(applicationContext)
        val weatherRepository = OpenMeteoWeatherRepository()

        setContent {
            val mapViewportState = rememberMapViewportState {
                setCameraOptions {
                    zoom(2.0)
                    center(Point.fromLngLat(24.5574, 46.5424))
                    pitch(0.0)
                    bearing(0.0)
                }
            }

            val mapState = rememberMapState(
                mapViewportState = mapViewportState,
                searchHistoryRepository = searchHistoryRepository,
                weatherRepository = weatherRepository
            )

            Surface(modifier = Modifier.fillMaxSize()) {
                MapScreen(state = mapState)
            }
        }
    }
}
