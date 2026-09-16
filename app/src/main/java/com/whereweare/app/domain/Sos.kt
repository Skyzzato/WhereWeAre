package com.whereweare.app.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.ceil

enum class SosSendState { IDLE, SENDING, CONFIRMED, UNKNOWN }
@Serializable data class SosPayload(val category: String,val latitude: Double?=null,val longitude: Double?=null,val accuracy: Double?=null,val recorded_at: String?=null,val nearby: Boolean=false,val accepted: Boolean=false,val area_latitude: Double?=null,val area_longitude: Double?=null) {
    fun location(id: String): UserLocation?=runCatching {
        UserLocation("sos:$id",requireNotNull(latitude),requireNotNull(longitude),requireNotNull(accuracy),null,null,Instant.parse(recorded_at),precisionMeters=ceil(accuracy).toInt().coerceAtLeast(1))
    }.getOrNull()
}
@Serializable data class SosRecipient(val user_id: String,val name: String,val push_accepted: Boolean,val viewed: Boolean,val response: String?=null,val nearby: Boolean=false)
@Serializable data class SosStatus(val recipients: List<SosRecipient> = emptyList(),val nearby_state: String?=null)
