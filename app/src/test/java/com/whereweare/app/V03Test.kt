package com.whereweare.app

import com.whereweare.app.domain.*
import com.whereweare.app.data.*
import com.whereweare.app.ui.languageTag
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class V03Test {
    @Test fun shortAndLegacyCodes() {
        assertEquals("H7D-F8C",normalizeInviteCode(" h7df8c "))
        assertTrue(validInviteCode("h7d-f8c"));assertTrue(validInviteCode("K7F4-X9P2"))
        assertFalse(validInviteCode("111-OOO"));assertFalse(validInviteCode("H7D-F8C%"))
    }
    @Test fun initialsAndLanguage() {
        assertEquals("A",avatarInitial(" Angelo "));assertEquals("?",avatarInitial(""))
        assertEquals("🏔",avatarInitial("🏔 Montagna"))
        assertEquals("it",languageTag("system","it"));assertEquals("en",languageTag("system","de"))
        assertEquals("en",languageTag("en","it"));assertEquals("it",languageTag("it","en"))
    }
    @Test fun tenMinutesDoNotTriggerPrematureStaleWarning() {
        val now=Instant.parse("2026-09-12T12:00:00Z")
        val fix=UserLocation("u",1.0,1.0,10.0,null,null,now.minusSeconds(780))
        val contact=ContactProfile("u","Angelo",null,86400,false,updateInterval=600)
        assertFalse(stalePosition(fix,contact,now,180))
        assertTrue(stalePosition(fix,contact,now.plusSeconds(1),180))
        assertTrue(600 in updateIntervals)
    }
    @Test fun oldServerCannotEnableNewClient() {
        val old=BootstrapConfig(4,"0.3",3,false)
        assertEquals(BootstrapGate.BACKEND_UPDATE,bootstrapGate(old,4))
        assertEquals(BootstrapGate.READY,bootstrapGate(old.copy(api_version=3),4))
        assertTrue(GlobalDefaults().high_accuracy)
    }
    @Test fun clustersRespectTouchTargetAndAvatarScale() {
        val points=listOf(MarkerScreenPoint(0,0f,0f),MarkerScreenPoint(1,55f,0f),MarkerScreenPoint(2,300f,0f))
        assertEquals(listOf(listOf(0),listOf(1),listOf(2)),clusterMarkers(points,48f))
        assertEquals(listOf(listOf(0,1),listOf(2)),clusterMarkers(points,81f))
        assertEquals(listOf(listOf(0,1)),clusterMarkers(listOf(MarkerScreenPoint(0,0f,0f),MarkerScreenPoint(1,0f,0f)),48f))
    }
    @Test fun optimisticSuccessRollbackAndAccountIsolation() {
        val profile=UserProfile("a","Angelo","H7D-F8C",avatarPath="photo.jpg")
        val base=Snapshot(profile=profile,shares=listOf(LocationShare("a","b",true)))
        val edits=OptimisticSnapshots()
        fun begin()=edits.begin("a",{it.copy(shares=it.shares.map {share -> share.copy(enabled=false)})},{it.shares.none {share -> share.enabled}})
        val key=begin()
        assertFalse(edits.render(base).shares.single().enabled)
        assertEquals("photo.jpg",edits.render(base).profile?.avatarPath)
        assertTrue(edits.render(base.copy(profile=profile.copy(id="other"))).shares.single().enabled)
        edits.rollback(key);assertEquals(base,edits.render(base))
        begin();val confirmed=base.copy(shares=listOf(LocationShare("a","b",false)))
        assertEquals(confirmed,edits.render(confirmed));assertTrue(edits.changes.value.isEmpty())
        assertEquals(base,edits.render(base))
    }
}
