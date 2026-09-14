package com.whereweare.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.whereweare.app.data.AvatarDraftStore
import com.whereweare.app.ui.AvatarTakePictureContract
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class AvatarDraftTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test fun cameraDraftSurvivesStoreRecreationUntilSuccessfulUploadCleanup() {
        val user=UUID.randomUUID().toString()
        val store=AvatarDraftStore(context)
        val file=store.cameraFile(user).apply {writeBytes(byteArrayOf(1,2,3))}
        val recreated=AvatarDraftStore(context)
        assertEquals(file,recreated.pending(user))
        assertArrayEquals(byteArrayOf(1,2,3),recreated.pending(user)!!.readBytes())
        recreated.clear(user)
        assertFalse(file.exists());assertNull(store.pending(user))
    }
    @Test fun fileImportRetainsPrivateCopyAndSeparatesAccounts() {
        val store=AvatarDraftStore(context)
        val source=File(context.cacheDir,"${UUID.randomUUID()}.jpg").apply {writeBytes(byteArrayOf(4,5,6))}
        val other=store.cameraFile("other")
        val imported=store.importFile("me",Uri.fromFile(source))
        source.delete()
        assertArrayEquals(byteArrayOf(4,5,6),imported.readBytes())
        val replacement=store.cameraFile("me")
        assertFalse(imported.exists());assertTrue(replacement.exists())
        store.clear("me");assertTrue(other.exists());store.clear("other")
    }
    @Test fun failedImportPreservesPreviousDraft() {
        val store=AvatarDraftStore(context)
        val previous=store.cameraFile("me").apply {writeBytes(byteArrayOf(1))}
        assertThrows(Exception::class.java) {store.importFile("me",Uri.fromFile(File(context.cacheDir,"missing-${UUID.randomUUID()}")))}
        assertEquals(previous,store.pending("me"));store.clear("me")
    }
    @Test fun cameraIntentExplicitlyGrantsReadAndWriteToItsContentUri() {
        val uri=Uri.parse("content://com.whereweare.app.files/avatar_drafts/camera.image")
        val intent=AvatarTakePictureContract().createIntent(context,uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
    }
}
