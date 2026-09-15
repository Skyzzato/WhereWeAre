package com.whereweare.app.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.ceil

enum class SosSendState { IDLE, SENDING, CONFIRMED, UNKNOWN }
@Serializable data class SosPayload(val category: String,val latitude: Double?=null,val longitude: Double?=null,val accuracy: Double?=null,val recorded_at: String?=null) {
    fun location(id: String): UserLocation?=runCatching {
        UserLocation("sos:$id",requireNotNull(latitude),requireNotNull(longitude),requireNotNull(accuracy),null,null,Instant.parse(recorded_at),precisionMeters=ceil(accuracy).toInt().coerceAtLeast(1))
    }.getOrNull()
}
@Serializable data class SosRecipient(val user_id: String,val name: String,val push_accepted: Boolean,val viewed: Boolean,val response: String?=null)
@Serializable data class SosStatus(val recipients: List<SosRecipient> = emptyList())
const val NEARBY_SOS_ENABLED=false
