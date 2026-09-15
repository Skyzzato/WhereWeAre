package com.whereweare.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant

@Serializable data class AppEvent(val id: String,val sender_id: String,val sender_name: String,val kind: String,
    val created_at: String,val expires_at: String,val payload: JsonObject,val recipients: List<String> = emptyList()) {
    fun active(now: Instant)=runCatching {Instant.parse(expires_at).isAfter(now)}.getOrDefault(false)
    fun sos(): SosPayload?=if(kind!="sos") null else runCatching {Json {ignoreUnknownKeys=true}.decodeFromJsonElement<SosPayload>(payload)}.getOrNull()
    fun place(): PlaceEvent?=if(kind!="place") null else runCatching {Json {ignoreUnknownKeys=true}.decodeFromJsonElement<PlaceEvent>(payload)}.getOrNull()
    fun checkin(): CheckinPayload?=if(kind!="checkin") null else runCatching {Json {ignoreUnknownKeys=true}.decodeFromJsonElement<CheckinPayload>(payload)}.getOrNull()
}
@Serializable data class CheckinPayload(val checkin_type: String,val message: String,val latitude: Double,val longitude: Double,
    val accuracy: Double,val recorded_at: String,val precision_m: Int) {
    fun location(id: String)=runCatching {UserLocation("event:$id",latitude,longitude,accuracy,null,null,Instant.parse(recorded_at),precisionMeters=precision_m)}.getOrNull()
}
