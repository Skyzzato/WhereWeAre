package com.whereweare.app

import com.whereweare.app.data.LocationDto
import com.whereweare.app.data.ProfileDto
import com.whereweare.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DomainTest {
    private val now=Instant.parse("2026-09-09T20:00:00Z")
    @Test fun freshnessCases() {
        listOf(30L to LocationFreshness.LIVE,300L to LocationFreshness.RECENT,2700L to LocationFreshness.OLD,
            7140L to LocationFreshness.OLD,7260L to LocationFreshness.EXPIRED).forEach { (seconds,expected) ->
            assertEquals(expected,freshness(now.minusSeconds(seconds),now))
        }
    }
    @Test fun exactBoundaries() {
        assertEquals(LocationFreshness.LIVE,freshness(now.minusSeconds(120),now))
        assertEquals(LocationFreshness.RECENT,freshness(now.minusSeconds(900),now))
        assertEquals(LocationFreshness.OLD,freshness(now.minusSeconds(7200),now))
        assertEquals(LocationFreshness.EXPIRED,freshness(now.minusMillis(7200001),now))
    }
    @Test fun sharingRequiresAllThreeConditions() {
        for(enabled in listOf(false,true)) for(sharing in listOf(false,true)) for(expired in listOf(false,true)) {
            assertEquals(enabled && sharing && !expired,canSee(enabled,sharing,now.minusSeconds(if(expired) 7201 else 30),now))
        }
    }
    @Test fun codesNormalizeButDoNotAcceptWildcards() {
        assertEquals("K7F4-X9P2",normalizeInviteCode(" k7 f4-x9p2 "))
        assertTrue(validInviteCode("k7f4x9p2"))
        assertFalse(validInviteCode("K7F4%"))
        assertFalse(validInviteCode("OOOO-1111"))
        assertFalse(validInviteCode("K7F4-X9P2-extra"))
    }
    @Test fun validatesAuthFields() {
        assertFalse(validName("  ")); assertTrue(validName(" Angelo ")); assertFalse(validName("x".repeat(81)))
        assertTrue(validEmail("test@example.com")); assertFalse(validEmail("test@")); assertFalse(validEmail("x y@example.com"))
        assertFalse(validPassword("1234567")); assertTrue(validPassword("correct-horse-27")); assertFalse(validPassword("x".repeat(129)))
    }
    @Test fun mapsWithoutInventingOptionalLocationData() {
        val dto=LocationDto("a",41.0,12.0,8.5,recorded_at=now.toString())
        assertEquals(UserLocation("a",41.0,12.0,8.5,null,null,now),dto.domain())
        assertEquals(UserProfile("a","Angelo","K7F4-X9P2"),ProfileDto("a","Angelo","K7F4-X9P2").domain())
    }
}
