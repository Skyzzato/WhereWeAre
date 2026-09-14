package com.whereweare.app

import com.whereweare.app.domain.*
import com.whereweare.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class V02Test {
    @Test fun bootstrapOfflineAndCacheFailures()=kotlinx.coroutines.runBlocking {
        val allowed=BootstrapConfig(2,"0.2",2,false)
        val blocked=allowed.copy(minimum_supported_version_code=3,latest_version_code=3)
        suspend fun offline(): BootstrapConfig = throw java.io.IOException("offline")
        assertNull(resolveBootstrap(null,::offline,{}))
        assertEquals(allowed,resolveBootstrap(allowed,::offline,{}))
        assertEquals(blocked,resolveBootstrap(blocked,::offline,{}))
        assertEquals(blocked,resolveBootstrap(allowed,{blocked},{throw java.io.IOException("disk full")}))
        assertEquals(allowed,resolveBootstrap(blocked,{allowed},{}))
    }
    @Test fun permissionsAreNotEquivalent() {
        assertEquals(LocationPermission.NONE,locationPermission(false,false))
        assertEquals(LocationPermission.APPROXIMATE,locationPermission(true,false))
        assertEquals(LocationPermission.PRECISE,locationPermission(true,true))
        assertEquals(LocationPermission.PRECISE,locationPermission(false,true))
    }
    @Test fun accuracyWarningDoesNotDiscardFix() {
        assertFalse(lowAccuracy(10.0,100));assertTrue(lowAccuracy(2000.0,100))
        assertFalse(lowAccuracy(100.0,100));assertTrue(lowAccuracy(100.1,100))
        assertEquals(listOf(5,30,60,300,600,1800,3600),updateIntervals)
        assertEquals(listOf(25,50,100,250,500,1000),accuracyThresholds)
    }
    @Test fun visibilityUsesOwnerTimeout() {
        val now=Instant.parse("2026-09-10T12:00:00Z")
        visibilityTimeouts.forEach { seconds -> assertTrue(withinVisibility(now.minusSeconds(seconds.toLong()),now,seconds));assertFalse(withinVisibility(now.minusSeconds(seconds+1L),now,seconds)) }
        assertFalse(withinVisibility(now.minusSeconds(1860),now,1800))
        assertTrue(withinVisibility(now.minusSeconds(1860),now,86400))
    }
    @Test fun bootstrapUsesNumericCodesAndFailsClosedWithoutCache() {
        val c=BootstrapConfig(10,"0.10",2,false)
        assertEquals(BootstrapGate.READY,bootstrapGate(c.copy(api_version=3),9))
        assertEquals(BootstrapGate.UPDATE,bootstrapGate(c,1))
        assertEquals(BootstrapGate.MAINTENANCE,bootstrapGate(c.copy(maintenance_mode=true),2))
        assertEquals(BootstrapGate.FIRST_CONNECTION,bootstrapGate(null,2))
        assertEquals(BootstrapGate.FIRST_CONNECTION,bootstrapGate(c.copy(minimum_supported_version_code=0),2))
    }
    @Test fun unicodeGroupNamesCountCodePointsNotUtf16Units() {
        assertTrue(validGroupName("🏔".repeat(24)));assertFalse(validGroupName("🏔".repeat(25)))
        assertFalse(validGroupName("  "));assertTrue(validGroupName("Escursione SAT"))
    }
}
