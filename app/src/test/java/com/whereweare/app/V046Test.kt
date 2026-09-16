package com.whereweare.app

import com.whereweare.app.data.*
import com.whereweare.app.domain.*
import com.whereweare.app.ui.syncFailureMessage
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

class V046Test {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun concurrentRecoveryWaitsForOneRenewal()=runTest {
        val gate=SessionRefreshGate()
        var token="old"
        var renewals=0
        var returned=0
        repeat(3) {launch {gate.refresh({token}) {renewals++;delay(100);token="new"};assertEquals("new",token);returned++}}
        runCurrent()
        assertEquals(0,returned)
        advanceTimeBy(101);runCurrent()
        assertEquals(1,renewals)
        assertEquals(3,returned)
    }
    @Test fun cleanInstallAndUpgradeRespectPriorOff() {
        assertTrue(shouldInitializeSharing(null,0,false))
        assertFalse(shouldInitializeSharing(null,1,false))
        assertFalse(shouldInitializeSharing(false,0,false))
        assertFalse(shouldInitializeSharing(false,42,false))
        assertTrue(shouldInitializeSharing(true,42,false))
        assertFalse(shouldInitializeSharing(true,42,true))
    }
    @Test fun updatesDeduplicateAcrossSourcesAndSortInstants() {
        fun event(id: String,kind: String,at: String)=AppEvent(id,"sender","Name",kind,at,"2026-09-17T00:00:00Z",buildJsonObject {})
        val checkin=event("c","checkin","2026-09-16T10:00:00+02:00")
        val sos=event("s","sos","2026-09-16T09:00:00Z")
        assertEquals(listOf(sos,checkin),unifiedUpdates(listOf(checkin,sos,checkin,sos)))
    }
    @Test fun sharingTransitionsUseOneStateAndPreserveOff() {
        assertEquals(SharingUiState.OFF,sharingUiState(false,false,false,false))
        assertEquals(SharingUiState.VERIFYING,sharingUiState(false,false,false,false,true))
        assertEquals(SharingUiState.STARTING,sharingUiState(false,true,false,false))
        assertEquals(SharingUiState.ON,sharingUiState(true,false,true,false))
        assertEquals(SharingUiState.SUSPENDED,sharingUiState(true,false,true,false,suspended=true))
        assertEquals(SharingUiState.STOPPING,sharingUiState(true,true,true,true,true,true))
        assertEquals(SharingUiState.OFF,sharingUiState(false,false,false,false,suspended=true))
    }
    @Test fun schemaErrorsHaveAnActionableCategory() {
        val e=org.mockito.Mockito.mock(io.github.jan.supabase.exceptions.RestException::class.java)
        org.mockito.Mockito.`when`(e.statusCode).thenReturn(404)
        assertEquals("configuration",connectionFailure(e))
        assertEquals(R.string.error_config,syncFailureMessage(Snapshot(syncError="configuration")))
        assertFalse(retryableRead(e))
    }
}
