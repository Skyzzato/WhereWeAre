package com.whereweare.app

import android.app.Activity
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import com.whereweare.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class QrTest {
    @Test fun generatedImagesDecodeAsTypedInvitations() {
        for(type in listOf("person","group")) for(code in listOf("ABC-DEF","ABCD-EFGH")) {
            val bitmap=qrInviteBitmap(type,code)
            val bytes=ByteArray(bitmap.width*bitmap.height) {i -> (bitmap.getPixel(i%bitmap.width,i/bitmap.width) and 255).toByte()}
            val decoded=decodeQrLuminance(bytes,bitmap.width,bitmap.height)
            assertEquals(qrInvitePayload(type,code),decoded)
            assertEquals(type to code,parseQrInvite(decoded!!))
            val rotated=ByteArray(bytes.size) {i -> bytes[(bitmap.height-1-i%bitmap.width)*bitmap.width+i/bitmap.width]}
            assertEquals(decoded,decodeQrLuminance(rotated,bitmap.width,bitmap.height))
            bitmap.recycle()
        }
    }
    @Test fun rejectsForeignLinksTechnicalIdsAndAdditionalPayload() {
        listOf("https://example.com/person/ABC-DEF","whereweare://person/550e8400-e29b-41d4-a716-446655440000",
            "whereweare://person/ABC-DEF?token=secret","whereweare://group/ABC-DEF#extra","whereweare://person/ABC-DEF/extra",
            "whereweare://meeting/ABC-DEF","ABC-DEF","x".repeat(129)).forEach {assertNull(it,parseQrInvite(it))}
        assertEquals("group" to "ABC-DEF",parseQrInvite("whereweare://group/abcdef"))
    }
    @Test fun paddedCameraPlanesRespectStrideAndPosition() {
        val plane=ByteBuffer.wrap(byteArrayOf(99,1,0,2,0,0,3,0,4))
        plane.position(1)
        assertArrayEquals(byteArrayOf(1,2,3,4),qrLuminancePlane(plane,2,2,5,2))
        assertEquals(1,plane.position())
        assertThrows(IllegalArgumentException::class.java) {qrLuminancePlane(plane,3,3,5,2)}
        assertNull(decodeQrLuminance(ByteArray(16) {255.toByte()},4,4))
    }
    @Test fun brightnessAndScreenFlagRestoreAfterRepeatedLifecycleTransitions() {
        Robolectric.buildActivity(Activity::class.java).setup().use {activity ->
            val window=activity.get().window
            for(brightness in listOf(-1f,.35f)) for(awake in listOf(false,true)) {
                window.attributes=window.attributes.apply {screenBrightness=brightness}
                if(awake) window.addFlags(FLAG_KEEP_SCREEN_ON) else window.clearFlags(FLAG_KEEP_SCREEN_ON)
                val state=QrWindowState(window)
                repeat(2) {
                    state.show();assertEquals(1f,window.attributes.screenBrightness,0f)
                    assertTrue(window.attributes.flags and FLAG_KEEP_SCREEN_ON != 0)
                    state.restore();state.restore()
                    assertEquals(brightness,window.attributes.screenBrightness,0f)
                    assertEquals(awake,window.attributes.flags and FLAG_KEEP_SCREEN_ON != 0)
                }
            }
        }
    }
}
