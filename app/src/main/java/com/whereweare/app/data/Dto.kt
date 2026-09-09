package com.whereweare.app.data

import com.whereweare.app.domain.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable data class ProfileDto(val id: String, val display_name: String, val invite_code: String) {
    fun domain() = UserProfile(id, display_name, invite_code)
}
@Serializable data class NameDto(val user_id: String, val display_name: String)
@Serializable data class LookupDto(val user_id: String, val display_name: String, val invite_code: String) {
    fun domain() = UserProfile(user_id, display_name, invite_code)
}
@Serializable data class RequestDto(val id: String, val sender_id: String, val receiver_id: String, val status: String) {
    fun domain() = ShareRequest(id, sender_id, receiver_id, status)
}
@Serializable data class ShareDto(val owner_id: String, val viewer_id: String, val enabled: Boolean) {
    fun domain() = LocationShare(owner_id, viewer_id, enabled)
}
@Serializable data class StatusDto(val user_id: String, val is_sharing: Boolean, val revision: Long=0) {
    fun domain() = SharingStatus(user_id, is_sharing)
}
@Serializable data class LocationDto(val user_id: String, val latitude: Double, val longitude: Double,
    val accuracy: Double, val speed: Double? = null, val bearing: Double? = null, val recorded_at: String) {
    fun domain() = UserLocation(user_id,latitude,longitude,accuracy,speed,bearing,Instant.parse(recorded_at))
}
