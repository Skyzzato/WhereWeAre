package com.whereweare.app.domain

import java.time.Duration
import java.time.Instant
import java.util.Locale

data class UserProfile(val id: String, val displayName: String, val inviteCode: String)
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
    return if (clean.length == 8) clean.take(4) + "-" + clean.drop(4) else clean
}
fun validInviteCode(code: String) = normalizeInviteCode(code).matches(Regex("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}"))
fun validName(name: String) = name.trim().length in 1..80
fun validEmail(email: String) = email.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
fun validPassword(password: String) = password.length in 8..128

data class Snapshot(val profile: UserProfile? = null, val names: Map<String,String> = emptyMap(),
    val requests: List<ShareRequest> = emptyList(), val shares: List<LocationShare> = emptyList(),
    val statuses: List<SharingStatus> = emptyList(), val locations: List<UserLocation> = emptyList(),
    val loading: Boolean = true, val offline: Boolean = false)
