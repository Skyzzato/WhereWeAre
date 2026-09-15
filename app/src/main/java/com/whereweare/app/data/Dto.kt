package com.whereweare.app.data

import com.whereweare.app.domain.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable data class MetadataDto(val profile: ProfileDto,val names: List<NameDto>,val requests: List<RequestDto>,val shares: List<ShareDto>,val statuses: List<StatusDto>,val contacts: List<ContactDto>,val groups: List<GroupDto>,val members: List<MemberDto>,val group_requests: List<GroupRequest>,val meetings: List<MeetingPoint>,val server_time: String,val saved_people: List<String> = emptyList(),val shared_precision: Boolean=false,val location_requests_available: Boolean=false,val location_requests: List<LocationRequest> = emptyList(),val events_available: Boolean=false,val events: List<AppEvent> = emptyList(),val temporary_groups_available: Boolean=false,val places_available: Boolean=false,val sos_available: Boolean=false)

@Serializable data class ProfileDto(val id: String, val display_name: String, val invite_code: String,val avatar_path: String?=null,val visibility_seconds: Int=86400,val shared_precision: Int=0) {
    fun domain() = UserProfile(id, display_name, invite_code,SafeAvatar.reference(avatar_path),visibility_seconds,shared_precision)
}
@Serializable data class ContactDto(val user_id: String,val display_name: String,val avatar_path: String?=null,val visibility_seconds: Int=86400,val common_group: Boolean=false,val can_view: Boolean=true,val update_interval_seconds: Int=60) {
    fun domain()=ContactProfile(user_id,display_name,SafeAvatar.reference(avatar_path),visibility_seconds,common_group,can_view,update_interval_seconds)
}
@Serializable data class GroupDto(val id: String,val name: String,val emoji: String,val invite_code: String,val creator_id: String,val created_at: String,val expires_at: String?=null) {
    fun domain()=Group(id,name,emoji,invite_code,creator_id,Instant.parse(created_at),expires_at?.let(Instant::parse))
}
@Serializable data class MemberDto(val group_id: String,val user_id: String,val sharing_enabled: Boolean=true,val shared_precision: Int?=null) { fun domain()=GroupMember(group_id,user_id,sharing_enabled,shared_precision) }
@Serializable data class NameDto(val user_id: String, val display_name: String)
@Serializable data class LookupDto(val user_id: String, val display_name: String, val invite_code: String) {
    fun domain() = UserProfile(user_id, display_name, invite_code)
}
@Serializable data class RequestDto(val id: String, val sender_id: String, val receiver_id: String, val status: String) {
    fun domain() = ShareRequest(id, sender_id, receiver_id, status)
}
@Serializable data class ShareDto(val owner_id: String, val viewer_id: String, val enabled: Boolean,val shared_precision: Int?=null) {
    fun domain() = LocationShare(owner_id, viewer_id, enabled,shared_precision)
}
@Serializable data class StatusDto(val user_id: String, val is_sharing: Boolean, val revision: Long=0,val session_id: String?=null) {
    fun domain() = SharingStatus(user_id, is_sharing)
}
@Serializable data class LocationDto(val user_id: String, val latitude: Double, val longitude: Double,
    val accuracy: Double, val speed: Double? = null, val bearing: Double? = null, val recorded_at: String,
    val battery_level: Int?=null,val location_enabled: Boolean?=null,val device_status_at: String?=null,val precision_m: Int=0) {
    fun domain() = UserLocation(user_id,latitude,longitude,accuracy,speed,bearing,Instant.parse(recorded_at),
        battery_level?.let(::validBatteryLevel),location_enabled,device_status_at?.let {runCatching {Instant.parse(it)}.getOrNull()},precision_m)
}
