package com.whereweare.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.whereweare.app.data.*
import com.whereweare.app.domain.*
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class V043Test {
    class MemoryStore: DataStore<Preferences> {
        override val data=MutableStateFlow<Preferences>(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences)->Preferences): Preferences=transform(data.value).also {data.value=it}
    }
    private suspend fun TestScope.fixture(store: MemoryStore=MemoryStore()): Triple<SosOperationRepository,SharingRepository,MemoryStore> {
        val sharing=mock(SharingRepository::class.java)
        val auth=mock(AuthRepository::class.java)
        val location=mock(LocationRepository::class.java)
        `when`(auth.userId).thenReturn("owner")
        `when`(auth.session).thenReturn(MutableStateFlow<SessionStatus>(SessionStatus.Initializing))
        `when`(sharing.state).thenReturn(MutableStateFlow(Snapshot()))
        `when`(location.snapshot(true)).thenAnswer {throw IOException()}
        val repository=SosOperationRepository(sharing,auth,location,store,backgroundScope)
        runCurrent()
        return Triple(repository,sharing,store)
    }
    @Test fun namesAreScopedNormalizedAndExcludeSelf() {
        val places=listOf(SavedPlace("a",0,"Casa   Mia",0.0,0.0,100))
        assertTrue(duplicatePlaceName(" casa mia ","b",places))
        assertFalse(duplicatePlaceName("CASA MIA","a",places))
        assertFalse(duplicatePlaceName("Casa","b",places))
    }
    @Test fun doubleTapRegistersOnceAndConfirmsOnlyServerAck()=runTest {
        val (repo,sharing,_)=fixture()
        repo.send("id","help",emptySet(),emptySet(),true)
        repo.send("different","help",emptySet(),emptySet(),true)
        runCurrent()
        assertEquals(SosSendState.CONFIRMED,repo.state.value)
        verify(sharing,times(1)).sendSos("id","help",emptySet(),emptySet(),null,true)
        verify(sharing,never()).sendSos("different","help",emptySet(),emptySet(),null,true)
    }
    @Test fun lostResponseIsRecoveredWithoutAnotherWrite()=runTest {
        val (repo,sharing,store)=fixture()
        doAnswer {throw IOException()}.`when`(sharing).sendSos("id","help",emptySet(),emptySet(),null,true)
        `when`(sharing.sosRegistered("id")).thenReturn(true)
        repo.send("id","help",emptySet(),emptySet(),true);runCurrent()
        assertEquals(SosSendState.CONFIRMED,repo.state.value)
        assertNotNull(store.data.value[stringPreferencesKey("pending_sos_owner")])
    }
    @Test fun uncertainSurvivesRecreationAndRetryReusesId()=runTest {
        val (repo,sharing,store)=fixture()
        doAnswer {throw IOException()}.`when`(sharing).sendSos("id","lost",emptySet(),emptySet(),null,true)
        repo.send("id","lost",emptySet(),emptySet(),true);runCurrent()
        assertEquals(SosSendState.UNKNOWN,repo.state.value)
        val (restored,next,_)=fixture(store)
        assertEquals(SosSendState.UNKNOWN,restored.state.value)
        verify(next,never()).sendSos(anyString(),anyString(),anySet(),anySet(),isNull(),anyBoolean())
        restored.send("new-id","help",setOf("other"),emptySet(),false);runCurrent()
        verify(next).sendSos("id","lost",emptySet(),emptySet(),null,true)
        assertEquals(SosSendState.CONFIRMED,restored.state.value)
    }
    @Test fun oldPendingRequestIsNeverResent()=runTest {
        val store=MemoryStore()
        store.updateData {mutablePreferencesOf(stringPreferencesKey("pending_sos_owner") to """{"id":"id","category":"help","people":[],"groups":[],"nearby":true,"created":1}""")}
        val (repo,sharing,_)=fixture(store)
        repo.send("id","help",emptySet(),emptySet(),true);runCurrent()
        verify(sharing,never()).sendSos(anyString(),anyString(),anySet(),anySet(),isNull(),anyBoolean())
        assertEquals(SosSendState.UNKNOWN,repo.state.value)
        assertEquals(R.string.sos_old_request,repo.error.value)
    }
    @Test fun authorizationFailureIsCertainAndSurvivesReopening()=runTest {
        val (repo,sharing,store)=fixture()
        val rejected=mock(io.github.jan.supabase.exceptions.RestException::class.java)
        `when`(rejected.statusCode).thenReturn(403)
        doAnswer {throw rejected}.`when`(sharing).sendSos("id","help",emptySet(),emptySet(),null,true)
        repo.send("id","help",emptySet(),emptySet(),true);runCurrent()
        assertEquals(SosSendState.FAILED,repo.state.value)
        val (restored,_,_)=fixture(store)
        assertEquals(SosSendState.FAILED,restored.state.value)
    }
    @Test fun timeoutBeforeRegistrationStaysUnknownUntilExplicitRetry()=runTest {
        val timeout=try {withTimeout(1) {delay(10)};error("expected timeout")} catch(e: TimeoutCancellationException) {e}
        val (repo,sharing,_)=fixture()
        doAnswer {throw timeout}.`when`(sharing).sendSos("id","help",emptySet(),emptySet(),null,true)
        repo.send("id","help",emptySet(),emptySet(),true);runCurrent()
        assertEquals(SosSendState.UNKNOWN,repo.state.value)
        advanceTimeBy(60_000);runCurrent()
        verify(sharing,times(1)).sendSos("id","help",emptySet(),emptySet(),null,true)
    }
}
