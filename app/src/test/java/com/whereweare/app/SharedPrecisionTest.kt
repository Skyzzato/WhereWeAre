package com.whereweare.app

import com.whereweare.app.data.LocationDto
import com.whereweare.app.data.MemberDto
import com.whereweare.app.data.ProfileDto
import com.whereweare.app.data.ShareDto
import com.whereweare.app.ui.approximateBoundary
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SharedPrecisionTest {
    @Test fun privacyMetadataSurvivesDtoMappingAndLegacyDefaults() {
        val json=Json {ignoreUnknownKeys=true}
        val dto=json.decodeFromString<LocationDto>("""{"user_id":"person","latitude":46.12,"longitude":11.12,"accuracy":500,"recorded_at":"2026-09-15T10:00:00Z","precision_m":500}""")
        assertEquals(500,dto.domain().precisionMeters)
        assertNull(dto.domain().speed)
        assertEquals(0,json.decodeFromString<LocationDto>("""{"user_id":"person","latitude":46.12,"longitude":11.12,"accuracy":12,"recorded_at":"2026-09-15T10:00:00Z"}""").domain().precisionMeters)
        assertEquals(250,ShareDto("a","b",true,250).domain().sharedPrecision)
        assertEquals(1000,MemberDto("g","a",true,1000).domain().sharedPrecision)
        assertEquals(500,ProfileDto("a","Name","ABC-DEF",shared_precision=500).domain().sharedPrecision)
    }
    @Test fun renderedAreaHasGeographicRadiusAtEveryLatitude() {
        for(lat in listOf(-89.9,0.0,46.0,89.9)) for(lon in listOf(-179.999,0.0,179.999)) for(radius in listOf(250,500,1000)) {
            val points=approximateBoundary(lat,lon,radius)
            assertEquals(65,points.size)
            points.forEach {p ->
                val latA=Math.toRadians(lat);val latB=Math.toRadians(p.latitude)
                val distance=6371000*acos((sin(latA)*sin(latB)+cos(latA)*cos(latB)*cos(Math.toRadians(lon-p.longitude))).coerceIn(-1.0,1.0))
                assertEquals(radius.toDouble(),distance,.02)
                assertTrue(p.longitude in -180.0..180.0)
            }
        }
    }
}
