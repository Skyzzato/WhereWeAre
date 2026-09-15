package com.whereweare.app

import com.whereweare.app.data.LocationDto
import com.whereweare.app.data.FeatureFlags
import com.whereweare.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DeviceStatusTest {
    private val now=Instant.parse("2026-09-15T00:00:00Z")
    @Test fun unavailableAndExpiredDeviceStatusAreNeverPresentedAsCurrent() {
        assertNull(validBatteryLevel(-1))
        assertNull(validBatteryLevel(Int.MIN_VALUE))
        assertNull(validBatteryLevel(101))
        assertEquals(0,validBatteryLevel(0))
        assertEquals(100,validBatteryLevel(100))
        assertFalse(deviceStatusRecent(null,now))
        assertFalse(deviceStatusRecent(now.plusSeconds(1),now))
        assertTrue(deviceStatusRecent(now.minusSeconds(600),now))
        assertFalse(deviceStatusRecent(now.minusSeconds(601),now))
    }
    @Test fun oldServerPayloadIsCompatibleAndDeviceReportingDefaultsOff() {
        val fix=LocationDto("owner",46.0,11.0,18.0,recorded_at=now.toString()).domain()
        assertNull(fix.batteryLevel)
        assertNull(fix.locationEnabled)
        assertNull(fix.deviceStatusAt)
        assertFalse(FeatureFlags().device_status)
        val malformed=LocationDto("owner",46.0,11.0,18.0,recorded_at=now.toString(),battery_level=200,device_status_at="invalid").domain()
        assertNull(malformed.batteryLevel)
        assertNull(malformed.deviceStatusAt)
    }
    @Test fun unchangedDeviceDoesNotGenerateARequestEveryFix() {
        val before=DeviceStatus(64,true)
        assertTrue(shouldPublishDeviceStatus(null,before,0))
        assertFalse(shouldPublishDeviceStatus(before,DeviceStatus(63,false),59_999))
        assertTrue(shouldPublishDeviceStatus(before,DeviceStatus(63,false),60_000))
        assertFalse(shouldPublishDeviceStatus(before,before,299_999))
        assertTrue(shouldPublishDeviceStatus(before,before,300_000))
    }
    @Test fun externalMapLinksRejectInvalidCoordinates() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=46.0%2C11.0",googleMapsUrl(46.0,11.0))
        assertNull(googleMapsUrl(Double.NaN,11.0))
        assertNull(googleMapsUrl(91.0,11.0))
        assertNull(googleMapsUrl(46.0,181.0))
        assertNull(openStreetMapUrl(46.0,Double.POSITIVE_INFINITY))
    }
}
