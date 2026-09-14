package com.whereweare.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

/** Untrusted images are optional: bounded decoding, no URI-to-path conversion. */
object SafeAvatar {
    fun reference(path: String?): String? = path?.takeIf {
        it.matches(Regex("[0-9a-fA-F-]{36}/[a-zA-Z0-9_-][a-zA-Z0-9_.-]{0,200}\\.(webp|jpg|jpeg|png)",RegexOption.IGNORE_CASE)) && ".." !in it
    }
    fun diagnostic(reason: String) {Log.w("WhereWeAreAvatar",reason)}
    fun decode(file: File): Bitmap? = guarded {decode({file.inputStream()},1024,true)}
    fun decode(bytes: ByteArray): Bitmap? = guarded {decode({bytes.inputStream()},512,false)}
    private fun guarded(block: ()->Bitmap?): Bitmap? = try {block()}
        catch(_: Exception) {diagnostic("image_rejected");null}
        catch(_: OutOfMemoryError) {diagnostic("image_memory_limit");null}
    private fun decode(open: ()->InputStream,maxSide: Int,orient: Boolean): Bitmap? {
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        open().use {BitmapFactory.decodeStream(it,null,bounds)}
        if(bounds.outWidth !in 1..32768 || bounds.outHeight !in 1..32768) return null
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxSide) sample*=2
        val bitmap=open().use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})} ?: return null
        if(!orient) return bitmap
        val exif=try {open().use {ExifInterface(it)}} catch(_: Exception) {return bitmap}
        if(!exif.isFlipped && exif.rotationDegrees==0) return bitmap
        return try {
            val matrix=Matrix().apply {if(exif.isFlipped) postScale(-1f,1f);postRotate(exif.rotationDegrees.toFloat())}
            Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also {if(it!==bitmap) bitmap.recycle()}
        } catch(e: Throwable) {bitmap.recycle();throw e}
    }
}
