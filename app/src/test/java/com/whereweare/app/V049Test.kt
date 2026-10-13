package com.whereweare.app

import com.whereweare.app.data.*
import com.whereweare.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class V049Test {
    @Test fun fixesOutsideViewCannotResetTheDeadline() {
        val timer=EmptyMapTimer()
        for(t in 0L..19_999L step 250) assertFalse(timer.tick(t,true,false,true))
        assertTrue(timer.tick(20_000,true,false,true))
        assertFalse(timer.tick(40_000,true,false,true))
    }
    @Test fun gestureBackgroundDialogOrVisibleMarkerResetTime() {
        val timer=EmptyMapTimer()
        assertFalse(timer.tick(0,true,false,true))
        assertFalse(timer.tick(19_999,false,false,true))
        assertFalse(timer.tick(100_000,true,false,true))
        assertFalse(timer.tick(119_999,true,false,true))
        assertTrue(timer.tick(120_000,true,false,true))
        assertFalse(timer.tick(121_000,true,true,true))
        assertFalse(timer.tick(122_000,true,false,true))
        assertFalse(timer.tick(141_999,true,false,true))
        assertTrue(timer.tick(142_000,true,false,true))
        assertFalse(timer.tick(200_000,true,false,false))
    }
    @Test fun meetingExpiresExactlyAtHourAndShorterExpiryWins() {
        val start=Instant.parse("2026-10-13T10:00:00Z")
        val point=MeetingPoint("id","owner","Name",45.0,11.0,true,start.toString())
        assertTrue(point.alive(start.plusSeconds(3599)))
        assertFalse(point.alive(start.plusSeconds(3600)))
        assertFalse(point.alive(start.plusSeconds(3601)))
        assertFalse(point.copy(expires_at=start.plusSeconds(7200).toString()).alive(start.plusSeconds(3600)))
        assertFalse(point.copy(expires_at=start.plusSeconds(30).toString()).alive(start.plusSeconds(30)))
        assertFalse(point.copy(created_at="invalid").alive(start))
        assertFalse(point.copy(active=false).alive(start))
    }
    @Test fun displayCacheAndOldProviderFixCannotBeRepublishedAsFresh() {
        val fix=UserLocation("owner",45.0,11.0,10.0,null,null,Instant.now(),elapsedRealtimeNanos=10_000_000_000)
        assertEquals(30_000L,fixAgeMillis(fix,40_000_000_000))
        assertEquals(30_001L,fixAgeMillis(fix,40_001_000_000))
        assertEquals(Long.MAX_VALUE,fixAgeMillis(fix.copy(elapsedRealtimeNanos=null),10_000_000_000))
        assertEquals(Long.MAX_VALUE,fixAgeMillis(fix,9_000_000_000))
    }
}
