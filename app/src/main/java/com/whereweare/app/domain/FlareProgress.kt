package com.whereweare.app.domain

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable data class FlareProgress(val user_id: String,val name: String="",val state: String,
    val distance_m: Double?=null,val precision_m: Int=0,val recorded_at: String?=null,val explicit: Boolean=false) {
    fun currentState(now: Instant): String {
        if(explicit || recorded_at==null) return state
        val at=runCatching {Instant.parse(recorded_at)}.getOrNull() ?: return "unavailable"
        return if(at.isBefore(now.minusSeconds(120)) || at.isAfter(now.plusSeconds(30))) "stale" else state
    }
}
