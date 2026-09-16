package com.whereweare.app

import android.app.Application
import android.net.Uri
import com.whereweare.app.data.InviteStore
import com.whereweare.app.domain.*
import com.whereweare.app.ui.inviteLink
import com.whereweare.app.ui.parseTypedInvite
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class V033Test {
    @Before fun clear() {RuntimeEnvironment.getApplication().getSharedPreferences("pending_invite",0).edit().clear().commit()}
    @Test fun noHostingSharesOnlyUsableCodes() {
        assertEquals("ABC-DEF",inviteLink("group","abc-def",""))
        assertEquals("ABCD-EFGH",inviteLink("person","abcdefgh",""))
        assertEquals("ABC-DEF",inviteLink("person","abcdef","http://example.test"))
        assertNull(parseTypedInvite(Uri.parse("https://whereweare.app/group/ABC-DEF"),""))
    }
    @Test fun configuredHttpsAndCustomLinksRemainTyped() {
        val base="https://invites.example.test/invite"
        for(type in listOf("person","group")) {
            val link=inviteLink(type,"abc-def",base)
            assertEquals("$base/$type/ABC-DEF",link)
            assertEquals(type to "ABC-DEF",parseTypedInvite(Uri.parse(link),base))
            assertEquals(type to "ABC-DEF",parseTypedInvite(Uri.parse("whereweare://$type/abcdef"),""))
        }
    }
    @Test fun invalidOrForeignLinksCannotStartInvitation() {
        val base="https://invites.example.test/invite"
        listOf("http://invites.example.test/invite/group/ABC-DEF","https://evil.test/invite/group/ABC-DEF",
            "$base/group/ABC-DEF/extra","$base/unknown/ABC-DEF","$base/person/ABC-DEF?x=1",
            "$base/group/ABC-DEF#fragment","whereweare://person/ABC-000","whereweare://person/A!BC-DEF",
            "whereweare://group/ABC-DEF/extra","whereweare://person@group/ABC-DEF").forEach {
            assertNull(it,parseTypedInvite(Uri.parse(it),base))
        }
        assertEquals("ABC-DEF",inviteLink("group","ABC-DEF","https://example.test?tracking=1"))
    }
    @Test fun pendingInvitationSurvivesProcessRecreationAndIsConsumedOnce() {
        val context=RuntimeEnvironment.getApplication()
        val original=InviteStore(context)
        assertTrue(original.accept(Uri.parse("whereweare://group/ABC-DEF")))
        val pending=original.pending.value!!
        val recreated=InviteStore(context)
        assertEquals(pending,recreated.pending.value)
        recreated.consume(pending.id)
        assertNull(InviteStore(context).pending.value)
        recreated.consume(pending.id)
        assertNull(recreated.pending.value)
    }
    @Test fun oldDialogCannotConsumeNewIntentAndInvalidIntentCannotEraseIt() {
        val store=InviteStore(RuntimeEnvironment.getApplication())
        store.accept(Uri.parse("whereweare://person/ABC-DEF"));val old=store.pending.value!!
        store.accept(Uri.parse("whereweare://group/GHJ-KMN"));val next=store.pending.value!!
        assertNotEquals(old.id,next.id)
        store.consume(old.id)
        assertEquals(next,store.pending.value)
        assertFalse(store.accept(Uri.parse("whereweare://group/INVALID")))
        assertEquals(next,store.pending.value)
    }
    @Test fun serviceAndButtonStateCoversEveryCombination() {
        assertEquals(SharingUiState.OFF,sharingUiState(false,false,false,false))
        assertEquals(SharingUiState.REMOTE_ACTIVE,sharingUiState(false,false,true,false))
        for(remote in listOf(false,true)) {
            assertEquals(SharingUiState.ON,sharingUiState(true,false,remote,false))
            assertEquals(SharingUiState.STARTING,sharingUiState(false,true,remote,false))
            for(active in listOf(false,true)) for(starting in listOf(false,true))
                assertEquals(SharingUiState.STOPPING,sharingUiState(active,starting,remote,true))
        }
    }
    @Test fun allNewRocketsHaveAVisibleApexThenDescendBeforeBursting() {
        assertEquals((1..50).toList(),FlareStyles.all.map {it.id})
        assertEquals((31..50).toList(),RocketStyles.all.map {it.id})
        for(style in RocketStyles.all) {
            assertTrue(style.x(0f)<.1f && style.y(0f)>.9f)
            assertTrue(style.x(style.burstTime) in .5f.. .95f)
            assertTrue(style.y(style.apex) in .1f.. .7f)
            assertTrue(style.y(style.apex-.1f)>style.y(style.apex))
            assertTrue(style.y(style.burstTime)>style.y(style.apex))
            assertTrue(style.burstTime<style.duration/1000f)
            assertEquals(style.burstTime,FlareStyles.get(style.id).ascent*style.duration/1000f,.001f)
            assertTrue(style.count in 72..196)
        }
        assertEquals(46,FlareStyles.normalize(51))
        assertEquals(46,FlareStyles.normalize(null))
    }
    @Test fun particlesAreDeterministicBoundedAndSlowDown() {
        for(style in RocketStyles.all) {
            val particles=RocketStyles.particles(style,42)
            assertEquals(style.count,particles.size)
            assertEquals(particles,RocketStyles.particles(style,42))
            assertNotEquals(particles,RocketStyles.particles(style,43))
            assertTrue(particles.all {it.life in .65f..1f && it.radius>0 && it.vx.isFinite() && it.vy.isFinite()})
            val first=RocketStyles.displacement(1f,style.drag,1f)
            val second=RocketStyles.displacement(1f,style.drag,2f)-first
            assertTrue(second>0 && second<first)
        }
    }
}
