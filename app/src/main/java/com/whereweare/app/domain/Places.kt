package com.whereweare.app.domain

import kotlinx.serialization.Serializable

@Serializable data class SavedPlace(val id: String,val slot: Int,val name: String,val latitude: Double,val longitude: Double,val radius_m: Int)
@Serializable data class PlaceRule(val id: String,val place_id: String,val subject_id: String,val kind: String,val enabled: Boolean,val recipients: List<String>)
@Serializable data class PlacesBundle(val places: List<SavedPlace> = emptyList(),val rules: List<PlaceRule> = emptyList())
@Serializable data class PlaceEvent(val place_name: String,val transition: String,val subject_name: String,val observed_at: String)
fun validPlace(name: String,latitude: String,longitude: String,radius: String)=name.trim().length in 1..60 &&
    latitude.toDoubleOrNull()?.let {it in -90.0..90.0}==true && longitude.toDoubleOrNull()?.let {it in -180.0..180.0}==true &&
    radius.toIntOrNull()?.let {it in 50..5000}==true
