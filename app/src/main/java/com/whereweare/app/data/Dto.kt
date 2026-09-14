package com.whereweare.app.data

import com.whereweare.app.domain.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable data class MetadataDto(val profile: ProfileDto,val names: List<NameDto>,val requests: List<RequestDto>,val shares: List<ShareDto>,val statuses: List<StatusDto>,val contacts: List<ContactDto>,val groups: List<GroupDto>,val members: List<MemberDto>,val group_requests: List<GroupRequest>,val meetings: List<MeetingPoint>,val server_time: String)

@Serializable data class ProfileDto(val id: String, val display_name: String, val invite_code: String,val avatar_path: String?=null,val visibility_seconds: Int=86400) {
    fun domain() = UserProfile(id, display_name, invite_code,SafeAvatar.reference(avatar_path),visibility_seconds)
}
@Serializable data class ContactDto(val user_id: String,val display_name: String,val avatar_path: String?=null,val visibility_seconds: Int=86400,val common_group: Boolean=false,val can_view: Boolean=true,val update_interval_seconds: Int=60) {
    fun domain()=ContactProfile(user_id,display_name,SafeAvatar.reference(avatar_path),visibility_seconds,common_group,can_view,update_interval_seconds)
}
@Serializable data class GroupDto(val id: String,val name: String,val emoji: String,val invite_code: String,val creator_id: String,val created_at: String) {
    fun domain()=Group(id,name,emoji,invite_code,creator_id,Instant.parse(created_at))
}
@Serializable data class MemberDto(val group_id: String,val user_id: String,val sharing_enabled: Boolean=true) { fun domain()=GroupMember(group_id,user_id,sharing_enabled) }
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
