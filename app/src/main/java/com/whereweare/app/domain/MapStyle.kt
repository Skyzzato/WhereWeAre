package com.whereweare.app.domain

import kotlinx.serialization.json.*
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Stable IDs preserve the v0.2 DataStore preference. */
enum class MapStyle(val id: String,val label: String,val tileUrl: String?,val attribution: String,val maxZoom: Int) {
    STANDARD("standard","OpenStreetMap · Standard",null,
        "© OpenStreetMap contributors · OpenFreeMap · OpenMapTiles",22),
    TOPO("topo","OpenTopoMap","https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png",
        "© OpenStreetMap contributors · SRTM · © OpenTopoMap (CC-BY-SA)",17),
    CYCLE("cyclosm","CyclOSM","https://{s}.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png",
        "© OpenStreetMap contributors · CyclOSM · tiles © OpenStreetMap France",20);

    fun rasterJson(): String {
        val url=requireNotNull(tileUrl)
        val credits="<a href=\"https://www.openstreetmap.org/copyright\">© OpenStreetMap contributors</a> · "+when(this) {
            TOPO -> "SRTM · <a href=\"https://opentopomap.org/about\">© OpenTopoMap</a> (<a href=\"https://creativecommons.org/licenses/by-sa/3.0/\">CC-BY-SA</a>)"
            CYCLE -> "<a href=\"https://www.cyclosm.org/\">CyclOSM</a> · <a href=\"https://www.openstreetmap.fr/\">OpenStreetMap France</a>"
            STANDARD -> error("Standard uses the existing vector style")
        }
        return buildJsonObject {
            put("version",8); put("name",label)
            putJsonObject("sources") { putJsonObject("basemap") {
                put("type","raster"); put("tileSize",256); put("maxzoom",maxZoom)
                put("attribution",credits)
                putJsonArray("tiles") { listOf("a","b","c").forEach { add(url.replace("{s}",it)) } }
            } }
            putJsonArray("layers") { add(buildJsonObject { put("id","basemap"); put("type","raster"); put("source","basemap") }) }
        }.toString()
    }
    companion object { fun fromId(id: String)=entries.firstOrNull { it.id==id } ?: STANDARD }
}

fun validCoordinates(latitude: Double,longitude: Double)=latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
fun coordinateLabel(latitude: Double,longitude: Double): String? =
    if(validCoordinates(latitude,longitude)) String.format(Locale.ROOT,"%.5f, %.5f",latitude,longitude) else null
fun openStreetMapUrl(latitude: Double,longitude: Double): String? =
    if(validCoordinates(latitude,longitude)) "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=17/$latitude/$longitude" else null
fun registrationDate(createdAt: String?,zone: ZoneId=ZoneId.systemDefault()): String? =
    createdAt?.let { runCatching { DateTimeFormatter.ofPattern("dd/MM/yyyy",Locale.ITALIAN).withZone(zone).format(Instant.parse(it)) }.getOrNull() }
