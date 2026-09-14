package com.whereweare.app.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Private original retained through activity/process recreation and upload failures. */
@Singleton class AvatarDraftStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val directory=File(context.filesDir,"avatar-drafts")
    private val preferences=context.getSharedPreferences("avatar_drafts",Context.MODE_PRIVATE)
    @Synchronized fun pending(user: String): File? {
        val name=preferences.getString("file_$user",null) ?: return null
        if(!name.matches(Regex("[0-9a-f-]{36}\\.(image|jpg)"))) return null
        return File(directory,name).takeIf {it.isFile}
    }
    @Synchronized fun clear(user: String) {
        pending(user)?.delete()
        preferences.edit().remove("file_$user").commit()
    }
    @Synchronized fun cameraFile(user: String): File {
        directory.mkdirs()
        val file=File(directory,"${UUID.randomUUID()}.jpg")
        check(file.createNewFile())
        replace(user,file)
        return file
    }
    fun importFile(user: String,uri: Uri): File {
        directory.mkdirs()
        val file=File(directory,"${UUID.randomUUID()}.image")
        try {
            context.contentResolver.openInputStream(uri)?.use {input -> file.outputStream().use {output ->
                val buffer=ByteArray(8192);var total=0L
                while(true) {
                    val size=input.read(buffer);if(size<0) break
                    total+=size;if(total>40L*1024*1024) throw IOException("image_too_large")
                    output.write(buffer,0,size)
                }
            }} ?: throw IOException("invalid_image")
            check(file.length()>0)
            synchronized(this) {replace(user,file)}
            return file
        } catch(e: Exception) {file.delete();throw e}
    }
    private fun replace(user: String,file: File) {
        val previous=pending(user)
        check(preferences.edit().putString("file_$user",file.name).commit())
        if(previous!=file) previous?.delete()
    }
}
