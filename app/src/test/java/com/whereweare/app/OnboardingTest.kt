package com.whereweare.app

import android.net.Uri
import com.whereweare.app.data.InviteStore
import com.whereweare.app.data.OnboardingStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class OnboardingTest {
    @Test fun completionAndSkippingNeverConsumePersonOrGroupInvites() {
        val context=RuntimeEnvironment.getApplication()
        for(type in listOf("person","group")) {
            context.getSharedPreferences("onboarding",0).edit().clear().commit()
            val invite=InviteStore(context)
            assertTrue(invite.accept(Uri.parse("whereweare://$type/ABC-DEF")))
            val pending=invite.pending.value
            val onboarding=OnboardingStore(context)
            assertFalse(onboarding.completed.value)
            assertTrue(onboarding.complete())
            assertTrue(OnboardingStore(context).completed.value)
            assertEquals(pending,InviteStore(context).pending.value)
            invite.consume(requireNotNull(pending).id)
            assertNull(InviteStore(context).pending.value)
        }
    }
}
