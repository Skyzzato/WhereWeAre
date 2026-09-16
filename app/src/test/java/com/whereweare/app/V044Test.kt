package com.whereweare.app

import com.whereweare.app.data.*
import com.whereweare.app.domain.*
import com.whereweare.app.ui.syncFailureMessage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant

class V044Test {
    private val at=Instant.parse("2026-09-16T00:00:00Z")
    private fun metrics()=ConnectionDiagnostics({0},{at})
    @Test fun decodingFailureIsNotAnUnreachableServerAndDoesNotRetryForever()=runTest {
        val d=metrics()
        val error=SerializationException("private payload must not be logged")
        try {d.measure(false,"metadata") {throw error}} catch(_: SerializationException) {}
        assertEquals("response",d.state.value.failure)
        assertEquals(true,d.state.value.serverReachable)
        assertFalse(retryableRead(error))
        assertFalse(d.state.value.toString().contains("private payload"))
        assertTrue(retryableRead(IOException()))
    }
    @Test fun unrelatedSuccessCannotClearFailedService()=runTest {
        val d=metrics()
        try {d.measure(false,"locations") {throw IOException()}} catch(_: IOException) {}
        d.measure(false,"metadata") {Unit}
        assertEquals("unreachable",d.state.value.failure)
        d.measure(false,"locations") {Unit}
        assertNull(d.state.value.failure)
    }
    @Test fun publicSuccessCannotVerifySession()=runTest {
        val d=metrics()
        d.measure(false,"bootstrap",authenticated=false) {Unit}
        assertNull(d.state.value.sessionVerified)
    }
    @Test fun bannerClaimsReconnectionOnlyDuringAnActualAttempt() {
        val failed=Snapshot(syncFailed=true,loading=false,syncError="response")
        assertEquals(R.string.connection_response,syncFailureMessage(failed))
        assertEquals(R.string.sync_waiting,syncFailureMessage(failed.copy(syncInProgress=true)))
        assertEquals(R.string.error_auth,syncFailureMessage(failed.copy(syncError="session")))
    }
    @Test fun sharingServiceDoesNotClaimToReconnectWhileStoppedOrBackingOff() {
        val waiting=com.whereweare.app.service.TrackingState(waiting=true,failure="forbidden")
        assertEquals(R.string.error_forbidden,com.whereweare.app.ui.trackingFailureMessage(waiting))
        assertEquals(R.string.sync_waiting,com.whereweare.app.ui.trackingFailureMessage(waiting.copy(retrying=true)))
    }
    private fun snapshot()=Snapshot(locations=listOf("self","a","b").map {UserLocation(it,0.0,0.0,10.0,null,null,at)})
    @Test fun repeatedLiveStatusDoesNotEraseUnrelatedMarkers() {
        var s=snapshot()
        repeat(30) {
            s=invalidateRealtime(s,"sharing_status",buildJsonObject {put("user_id","a");put("is_sharing",true)},"self")
            assertEquals(listOf("self","a","b"),s.locations.map {it.userId})
        }
    }
    @Test fun stopAndRevocationImmediatelyRemoveAffectedMarkers() {
        val record=buildJsonObject {put("user_id","a");put("is_sharing",false)}
        assertEquals(listOf("self","b"),invalidateRealtime(snapshot(),"sharing_status",record,"self").locations.map {it.userId})
        assertEquals(listOf("self"),invalidateRealtime(snapshot(),"account_events",buildJsonObject {},"self").locations.map {it.userId})
        val revoke=buildJsonObject {put("owner_id","a");put("enabled",false)}
        assertEquals(listOf("self","b"),invalidateRealtime(snapshot(),"location_shares",revoke,"self").locations.map {it.userId})
        assertEquals(listOf("self"),invalidateRealtime(snapshot(),"location_shares",buildJsonObject {},"self").locations.map {it.userId})
    }
    @Test fun metadataMakesSosAndPlacesAvailableBeforeLocationsComplete() {
        val m=Json.decodeFromString<MetadataDto>("""{"profile":{"id":"self","display_name":"Test","invite_code":"ABC-DEF"},"names":[],"requests":[],"shares":[],"statuses":[],"contacts":[],"groups":[],"members":[],"group_requests":[],"meetings":[],"server_time":"2026-09-16T00:00:00Z","sos_available":true,"places_available":true,"events_available":true}""")
        val s=applyMetadata(snapshot(),m)
        assertTrue(s.sosAvailable)
        assertTrue(s.placesAvailable)
        assertTrue(s.eventsAvailable)
        assertFalse(s.loading)
        assertEquals(snapshot().locations,s.locations)
    }
}
