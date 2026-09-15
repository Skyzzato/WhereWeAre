package com.whereweare.app

import com.whereweare.app.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AppEventTest {
    private val event=AppEvent("id","sender","Name","checkin","2026-09-15T10:00:00Z","2026-09-16T10:00:00Z",
        Json.parseToJsonElement("""{"checkin_type":"here","message":"Hello","latitude":46.12,"longitude":11.12,"accuracy":500,"recorded_at":"2026-09-15T10:00:00Z","precision_m":500}""").jsonObject)
    @Test fun snapshotPreservesPrecisionAndExpiresAtDeadline() {
        assertEquals(500,event.checkin()!!.location(event.id)!!.precisionMeters)
        assertTrue(event.active(Instant.parse("2026-09-16T09:59:59Z")))
        assertFalse(event.active(Instant.parse("2026-09-16T10:00:00Z")))
        assertNull(event.copy(kind="unknown").checkin())
        assertNull(event.copy(payload=buildJsonObject {}).checkin())
        assertFalse(event.copy(expires_at="invalid").active(Instant.now()))
    }
}
