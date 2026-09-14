package com.whereweare.app.domain

import java.time.Duration
import java.time.Instant
import java.util.Locale

data class UserProfile(val id: String, val displayName: String, val inviteCode: String, val avatarPath: String?=null, val visibilitySeconds: Int=86400)
data class ContactProfile(val id: String,val name: String,val avatarPath: String?,val visibilitySeconds: Int,val commonGroup: Boolean,val canView: Boolean=true,val updateInterval: Int=60)
data class Group(val id: String,val name: String,val emoji: String,val code: String,val creator: String,val createdAt: Instant)
data class GroupMember(val groupId: String,val userId: String,val sharingEnabled: Boolean=true)
enum class LocationPermission { NONE, APPROXIMATE, PRECISE }
fun locationPermission(coarse: Boolean,fine: Boolean) = when { fine -> LocationPermission.PRECISE; coarse -> LocationPermission.APPROXIMATE; else -> LocationPermission.NONE }
val updateIntervals = listOf(5,30,60,300,600,1800,3600)
val accuracyThresholds = listOf(25,50,100,250,500,1000)
val visibilityTimeouts = listOf(600,1800,3600,7200,14400,43200,86400)
fun lowAccuracy(accuracy: Double,threshold: Int) = accuracy>threshold
fun withinVisibility(at: Instant,now: Instant,seconds: Int) = !at.isBefore(now.minusSeconds(seconds.toLong()))
fun validGroupName(name: String) = name.trim().codePointCount(0,name.trim().length) in 1..24
data class ShareRequest(val id: String, val sender: String, val receiver: String, val status: String)
data class LocationShare(val owner: String, val viewer: String, val enabled: Boolean)
data class SharingStatus(val userId: String, val sharing: Boolean)
data class UserLocation(val userId: String, val latitude: Double, val longitude: Double,
    val accuracy: Double, val speed: Double?, val bearing: Double?, val recordedAt: Instant)
data class VisiblePerson(val name: String, val location: UserLocation, val freshness: LocationFreshness)
enum class LocationFreshness { LIVE, RECENT, OLD, EXPIRED }
fun freshness(recordedAt: Instant, now: Instant): LocationFreshness {
    val age = Duration.between(recordedAt, now)
    return when {
        age > Duration.ofHours(2) -> LocationFreshness.EXPIRED
        age > Duration.ofMinutes(15) -> LocationFreshness.OLD
        age > Duration.ofMinutes(2) -> LocationFreshness.RECENT
        else -> LocationFreshness.LIVE
    }
}
fun canSee(enabled: Boolean, sharing: Boolean, at: Instant, now: Instant) =
    enabled && sharing && freshness(at, now) != LocationFreshness.EXPIRED
fun normalizeInviteCode(input: String): String {
    val clean = input.uppercase(Locale.ROOT).replace(Regex("[\\s-]"), "")
    return when(clean.length) { 6 -> clean.take(3)+"-"+clean.drop(3); 8 -> clean.take(4)+"-"+clean.drop(4); else -> clean }
}
object InviteCodes { const val ALPHABET="23456789ABCDEFGHJKMNPQRSTUVWXYZ"; val acceptedLengths=setOf(6,8) }
fun validInviteCode(code: String): Boolean { val raw=normalizeInviteCode(code).replace("-",""); return raw.length in InviteCodes.acceptedLengths && raw.all { it in InviteCodes.ALPHABET } }
fun avatarInitial(name: String)=name.trim().takeIf { it.isNotEmpty() }?.let { String(Character.toChars(it.codePointAt(0))).uppercase(Locale.ROOT) } ?: "?"
fun validName(name: String) = name.trim().length in 1..80
fun validEmail(email: String) = email.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
fun validPassword(password: String) = password.length in 8..128

data class Snapshot(val profile: UserProfile? = null, val names: Map<String,String> = emptyMap(),
    val requests: List<ShareRequest> = emptyList(), val shares: List<LocationShare> = emptyList(),
    val statuses: List<SharingStatus> = emptyList(), val locations: List<UserLocation> = emptyList(),
    val loading: Boolean = true, val offline: Boolean = false,
    val contacts: Map<String,ContactProfile> = emptyMap(),val groups: List<Group> = emptyList(),val members: List<GroupMember> = emptyList(),
    val groupRequests: List<GroupRequest> = emptyList(),val meetings: List<MeetingPoint> = emptyList(),val syncFailed: Boolean=false)

@kotlinx.serialization.Serializable data class GroupRequest(val id: String,val group_id: String,val group_name: String,val user_id: String,val name: String,val kind: String,val status: String,val can_respond: Boolean,val group_emoji: String="📍",val can_cancel: Boolean=false)
@kotlinx.serialization.Serializable data class MeetingPoint(val id: String,val creator_id: String,val creator_name: String,val latitude: Double,val longitude: Double,val active: Boolean,val created_at: String,val removed_at: String?=null,val recipients: List<String> = emptyList(),val flare_style_id: Int?=1)
fun stalePosition(fix: UserLocation,profile: ContactProfile?,now: Instant,graceSeconds: Int)=
    Duration.between(fix.recordedAt,now).seconds>((profile?.updateInterval ?: 60)+graceSeconds).toLong()

// Greedy screen-space clusters avoid overlapping hit targets at every avatar scale.
data class MarkerScreenPoint(val index: Int,val x: Float,val y: Float)
fun clusterMarkers(points: List<MarkerScreenPoint>,distance: Float): List<List<Int>> {
    val remaining=points.toMutableList();val result=mutableListOf<List<Int>>()
    while(remaining.isNotEmpty()) {
        val anchor=remaining.removeAt(0)
        val neighbors=remaining.filter {kotlin.math.abs(it.x-anchor.x)<distance && kotlin.math.abs(it.y-anchor.y)<distance}
        remaining.removeAll(neighbors.toSet());result+=listOf(anchor.index)+neighbors.map {it.index}
    }
    return result
}
