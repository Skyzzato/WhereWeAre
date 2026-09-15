package com.whereweare.app

import com.whereweare.app.domain.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlacesTest {
    @Test fun validatesCoordinatesAndRejectsNonFiniteInput() {
        assertTrue(validPlace("Home","46.0","11.0","100"))
        assertFalse(validPlace("Home","NaN","11.0","100"))
        assertFalse(validPlace("Home","91","11.0","100"))
        assertFalse(validPlace("Home","46","11","10"))
        assertFalse(validPlace(" ","46","11","100"))
    }
    @Test fun placeNoticeHasNoLocationAndDoesNotBecomeAMapPin() {
        val event=Json.decodeFromString<AppEvent>("""{"id":"event","sender_id":"owner","sender_name":"Owner","kind":"place","created_at":"2026-09-15T12:00:00Z","expires_at":"2026-09-16T12:00:00Z","payload":{"place_name":"Home","subject_name":"Person","transition":"enter","observed_at":"2026-09-15T12:00:00Z"}}""")
        assertEquals("Home",event.place()?.place_name)
        assertNull(event.checkin())
        assertFalse(event.payload.containsKey("latitude"))
    }
}
