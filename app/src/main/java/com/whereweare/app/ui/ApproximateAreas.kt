package com.whereweare.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.whereweare.app.domain.UserLocation
import org.maplibre.compose.map.MapState
import org.maplibre.spatialk.geojson.Position
import kotlin.math.*

/** Geographic circle, projected at every camera change; no precise location on the client. */
fun approximateBoundary(lat: Double,lon: Double,radius: Int): List<Position> {
    val latitude=Math.toRadians(lat);val longitude=Math.toRadians(lon);val angle=radius/6371000.0
    return (0..64).map {i ->
        val bearing=2*PI*i/64
        val targetLat=asin(sin(latitude)*cos(angle)+cos(latitude)*sin(angle)*cos(bearing))
        val targetLon=longitude+atan2(sin(bearing)*sin(angle)*cos(latitude),cos(angle)-sin(latitude)*sin(targetLat))
        Position((Math.toDegrees(targetLon)+540)%360-180,Math.toDegrees(targetLat))
    }
}
@Composable fun ApproximateAreas(camera: MapState,locations: List<UserLocation>) {
    val color=MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxSize()) {
        camera.cameraPosition
        locations.filter {it.precisionMeters>0}.forEach {location ->
            val points=approximateBoundary(location.latitude,location.longitude,location.precisionMeters)
                .mapNotNull {camera.screenLocationFromPosition(it)}
            if(points.size==65) {
                val path=Path()
                points.forEachIndexed {i,p -> if(i==0) path.moveTo(p.x.toPx(),p.y.toPx()) else path.lineTo(p.x.toPx(),p.y.toPx())}
                path.close()
                drawPath(path,color.copy(alpha=.12f))
                drawPath(path,color,style=Stroke(2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),5.dp.toPx()))))
            }
        }
    }
}
