package com.whereweare.app.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

fun groupExpiry(date: String,time: String,zone: ZoneId,now: Instant): Instant?=runCatching {
    val local=LocalDateTime.parse("$date $time",DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT))
    val offsets=zone.rules.getValidOffsets(local)
    if(offsets.size!=1) null else local.toInstant(offsets.single()).takeIf {it.isAfter(now)}
}.getOrNull()

fun activeLocationGrant(snapshot: Snapshot,owner: String,viewer: String?,now: Instant): Boolean {
    if(owner==viewer) return true
    if(snapshot.shares.any {it.owner==owner && it.viewer==viewer && it.enabled}) return true
    val activeGroups=snapshot.groups.filter {it.active(now)}.map {it.id}.toSet()
    return snapshot.members.any {m -> m.userId==owner && m.sharingEnabled && m.groupId in activeGroups &&
        snapshot.members.any {it.groupId==m.groupId && it.userId==viewer}}
}
