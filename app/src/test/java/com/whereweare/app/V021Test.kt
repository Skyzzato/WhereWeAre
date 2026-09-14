package com.whereweare.app

import com.whereweare.app.domain.*
import com.whereweare.app.data.TrackingSession
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class V021Test {
    @Test fun coordinatesUseDecimalPointEvenInItalianLocale() {
        val previous=Locale.getDefault()
        try {
            Locale.setDefault(Locale.ITALIAN)
            assertEquals("46.06787, 11.12108",coordinateLabel(46.06787,11.12108))
            assertEquals("https://www.openstreetmap.org/?mlat=46.06787&mlon=11.12108#map=17/46.06787/11.12108",openStreetMapUrl(46.06787,11.12108))
            assertNotNull(coordinateLabel(0.0,0.0))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun invalidPositionsNeverProduceBrowserLinks() {
        listOf(Double.NaN to 11.0,46.0 to Double.POSITIVE_INFINITY,90.1 to 0.0,0.0 to -180.1).forEach { (lat,lon) ->
            assertNull(coordinateLabel(lat,lon)); assertNull(openStreetMapUrl(lat,lon))
        }
        assertNotNull(openStreetMapUrl(-90.0,180.0))
    }
    @Test fun rasterStylesHaveValidSourcesAndCredits() {
        MapStyle.entries.filter { it.tileUrl!=null }.forEach { style ->
            val json=Json.parseToJsonElement(style.rasterJson()).jsonObject
            val source=json.getValue("sources").jsonObject.getValue("basemap").jsonObject
            assertEquals(8,json.getValue("version").jsonPrimitive.int)
            assertEquals(style.maxZoom,source.getValue("maxzoom").jsonPrimitive.int)
            assertEquals(256,source.getValue("tileSize").jsonPrimitive.int)
            assertTrue(source.getValue("attribution").jsonPrimitive.content.contains("openstreetmap.org/copyright"))
            source.getValue("tiles").jsonArray.forEach { tile ->
                assertTrue(tile.jsonPrimitive.content.startsWith("https://"))
                assertFalse(tile.jsonPrimitive.content.contains("{s}"))
            }
        }
        assertEquals(MapStyle.STANDARD,MapStyle.fromId("unknown"))
        assertEquals(MapStyle.TOPO,MapStyle.fromId("topo"))
        assertEquals(MapStyle.CYCLE,MapStyle.fromId("cyclosm"))
    }
    @Test fun registrationDateHandlesTimezoneAndMissingData() {
        assertEquals("02/01/2026",registrationDate("2026-01-01T23:30:00Z",ZoneId.of("Europe/Rome")))
        assertEquals("01/01/2026",registrationDate("2026-01-01T23:30:00Z",ZoneId.of("UTC")))
        assertNull(registrationDate(null)); assertNull(registrationDate("invalid"))
    }
    @Test fun restartPersistsSessionAndOriginalRevision() {
        val initial=TrackingSession("user-a","session-a",42)
        assertEquals(initial,Json.decodeFromString<TrackingSession>(Json.encodeToString(TrackingSession.serializer(),initial)))
        assertNull(Json.decodeFromString<TrackingSession>("""{"userId":"user-a","sessionId":"session-a"}""").revision)
    }
}
