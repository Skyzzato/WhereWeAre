package com.whereweare.app

import com.whereweare.app.domain.*
import com.whereweare.app.data.GroupDto
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class GroupExpiryTest {
    private val now=Instant.parse("2026-01-01T00:00:00Z")
    private val group=Group("g","Trip","📍","ABC234","owner",now,now.plusSeconds(60))
    @Test fun expiryRevokesOnlyItsOwnGrant() {
        val snapshot=Snapshot(groups=listOf(group),members=listOf(GroupMember("g","owner"),GroupMember("g","viewer")))
        assertTrue(activeLocationGrant(snapshot,"owner","viewer",now))
        assertFalse(activeLocationGrant(snapshot,"owner","viewer",now.plusSeconds(60)))
        assertTrue(activeLocationGrant(snapshot.copy(shares=listOf(LocationShare("owner","viewer",true))),"owner","viewer",now.plusSeconds(60)))
        assertFalse(activeLocationGrant(snapshot.copy(members=listOf(GroupMember("g","owner",false),GroupMember("g","viewer"))),"owner","viewer",now))
        assertTrue(group.copy(expiresAt=null).active(now.plusSeconds(999999)))
    }
    @Test fun strictLocalTimeRejectsPastInvalidAndAmbiguousDates() {
        val rome=ZoneId.of("Europe/Rome")
        assertNull(groupExpiry("2026-03-29","02:30",rome,now))
        assertNull(groupExpiry("2026-10-25","02:30",rome,now))
        assertNull(groupExpiry("2026-02-30","12:00",rome,now))
        assertNull(groupExpiry("2025-12-31","12:00",rome,now))
        assertEquals(Instant.parse("2026-09-15T16:30:00Z"),groupExpiry("2026-09-15","18:30",rome,now))
    }
}
