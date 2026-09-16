package com.whereweare.app.domain

import kotlinx.serialization.Serializable

@Serializable data class SavedPlace(val id: String,val slot: Int,val name: String,val latitude: Double,val longitude: Double,val radius_m: Int,val emoji: String="📍")
fun normalizedPlaceName(name: String)=name.trim().replace(Regex("\\s+")," ").lowercase(java.util.Locale.ROOT)
fun duplicatePlaceName(name: String,id: String,places: List<SavedPlace>)=places.any {it.id!=id && normalizedPlaceName(it.name)==normalizedPlaceName(name)}
@Serializable data class PlaceRule(val id: String,val place_id: String,val subject_id: String,val kind: String,val enabled: Boolean,val recipients: List<String>)
@Serializable data class PlacesBundle(val places: List<SavedPlace> = emptyList(),val rules: List<PlaceRule> = emptyList())
@Serializable data class PlaceEvent(val place_name: String,val transition: String,val subject_name: String,val observed_at: String)
fun validPlace(name: String,latitude: String,longitude: String,radius: String)=name.trim().length in 1..60 &&
    latitude.toDoubleOrNull()?.let {it in -90.0..90.0}==true && longitude.toDoubleOrNull()?.let {it in -180.0..180.0}==true &&
    radius.toIntOrNull()?.let {it in 50..5000}==true

fun mapPlaceIconDp(preference: Float): Float=when(preference) {.75f -> 24f;1.25f -> 52f;1.5f -> 72f;else -> 36f}
fun placePickerInitial(latitude: Double?,longitude: Double?,fix: UserLocation?): Pair<Double,Double> =
    if(latitude!=null && longitude!=null && latitude in -90.0..90.0 && longitude in -180.0..180.0) latitude to longitude
    else fix?.let {it.latitude to it.longitude} ?: (41.9028 to 12.4964)
