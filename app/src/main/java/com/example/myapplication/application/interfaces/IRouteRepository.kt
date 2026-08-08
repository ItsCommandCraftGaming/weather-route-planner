package com.example.myapplication.application.interfaces

import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.Point
import retrofit2.Callback

interface IRouteRepository {
    fun getDirections(
        start: Point,
        end: Point,
        mapboxToken: String,
        callback: Callback<DirectionsResponse>
    )
}
