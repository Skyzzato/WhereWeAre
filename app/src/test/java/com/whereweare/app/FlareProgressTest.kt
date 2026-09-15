package com.whereweare.app

import com.whereweare.app.domain.FlareProgress
import com.whereweare.app.domain.MeetingPoint
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class FlareProgressTest {
    @Test fun cachedArrivalBecomesStaleWithoutAnotherResponse() {
        val now=Instant.parse("2026-09-15T12:00:00Z")
        val progress=FlareProgress("person",state="arrived",recorded_at=now.toString())
        assertEquals("arrived",progress.currentState(now.plusSeconds(120)))
        assertEquals("stale",progress.currentState(now.plusSeconds(121)))
        assertEquals("arrived",progress.copy(explicit=true).currentState(now.plusSeconds(121)))
        assertEquals("unavailable",progress.copy(recorded_at="bad").currentState(now))
    }
    @Test fun legacyFlarePreservesAllStylesWithoutInventedProgress() {
        val point=Json.decodeFromString<MeetingPoint>("""{"id":"m","creator_id":"p","creator_name":"Name","latitude":46.0,"longitude":11.0,"active":true,"created_at":"2026-09-15T12:00:00Z","flare_style_id":50}""")
        assertEquals(50,point.flare_style_id)
        assertTrue(point.progress.isEmpty())
        assertNull(point.completed_at)
    }
}
