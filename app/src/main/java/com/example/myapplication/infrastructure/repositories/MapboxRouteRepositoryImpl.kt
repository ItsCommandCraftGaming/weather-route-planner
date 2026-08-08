package com.example.myapplication.infrastructure.repositories

import com.example.myapplication.application.interfaces.IRouteRepository
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import retrofit2.Callback

class MapboxRouteRepositoryImpl : IRouteRepository {
    override fun getDirections(
        start: Point,
        end: Point,
        mapboxToken: String,
        callback: Callback<DirectionsResponse>
    ) {
        val routeOptions = RouteOptions.builder()
            .coordinatesList(listOf(start, end))
            .profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC)
            .geometries(DirectionsCriteria.GEOMETRY_POLYLINE6)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .annotationsList(listOf(DirectionsCriteria.ANNOTATION_CONGESTION, DirectionsCriteria.ANNOTATION_DURATION))
            .alternatives(true)
            .build()

        val client = MapboxDirections.builder()
            .accessToken(mapboxToken)
            .routeOptions(routeOptions)
            .build()

        client.enqueueCall(callback)
    }
}
