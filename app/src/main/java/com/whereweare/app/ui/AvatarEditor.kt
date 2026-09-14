package com.whereweare.app.ui

import com.whereweare.app.R

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.*

@Composable fun AvatarEditor(onSave: (ByteArray)->Unit,onError: ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    fun load(uri: Uri) { scope.launch {
        loading=true
        try { image=withContext(Dispatchers.IO) {
            val resolver=context.contentResolver
            val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            resolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,options)}
            var sample=1; while(max(options.outWidth,options.outHeight)/sample>2048) sample*=2
            val bitmap=resolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})} ?: error("invalid_image")
            val exif=resolver.openInputStream(uri)?.use {ExifInterface(it)}
            val matrix=Matrix().apply { if(exif?.isFlipped==true) postScale(-1f,1f); postRotate((exif?.rotationDegrees ?: 0).toFloat()) }
            Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        } } catch(e: CancellationException) {throw e} catch(_: Exception) {onError()} finally {loading=false}
    } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let(::load)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {ok -> if(ok) cameraUri?.toUri()?.let(::load)}
    Row {
        TextButton(enabled=!loading,onClick={try {
            val dir=File(context.cacheDir,"camera").apply {mkdirs()}
            val file=File(dir,"avatar.jpg")
            val uri=FileProvider.getUriForFile(context,context.packageName+".files",file)
            cameraUri=uri.toString();camera.launch(uri)
        } catch(_: Exception) {onError()}}){Text(Strings.text(R.string.ui_004))}
        TextButton(enabled=!loading,onClick={picker.launch(arrayOf("image/*"))}){Text(Strings.text(R.string.ui_005))}
    }
    if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    image?.let { bitmap -> CropAvatar(bitmap,onDismiss={image=null; File(context.cacheDir,"camera/avatar.jpg").delete()},onSave={bytes -> image=null; File(context.cacheDir,"camera/avatar.jpg").delete(); onSave(bytes)}) }
}
@Composable private fun CropAvatar(bitmap: Bitmap,onDismiss: ()->Unit,onSave: (ByteArray)->Unit) {
    var zoom by remember {mutableFloatStateOf(1f)}
    var pan by remember {mutableStateOf(Offset.Zero)}
    var side by remember {mutableFloatStateOf(1f)}
    val base=side/min(bitmap.width,bitmap.height)
    fun constrain(value: Offset,scale: Float): Offset {
        val x=((bitmap.width*base*scale-side)/2).coerceAtLeast(0f)
        val y=((bitmap.height*base*scale-side)/2).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-x,x),value.y.coerceIn(-y,y))
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text(Strings.text(com.whereweare.app.R.string.crop_title),style=MaterialTheme.typography.headlineSmall)
            Text(Strings.text(com.whereweare.app.R.string.crop_hint))
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).onSizeChanged {side=it.width.toFloat()}
                .pointerInput(bitmap,side) { detectTransformGestures { _,delta,scale,_ ->
                    zoom=(zoom*scale).coerceIn(1f,5f);pan=constrain(pan+delta,zoom)
                } }) {
                val width=bitmap.width*base*zoom;val height=bitmap.height*base*zoom
                drawImage(bitmap.asImageBitmap(),dstOffset=IntOffset(((side-width)/2+pan.x).roundToInt(),((side-height)/2+pan.y).roundToInt()),dstSize=IntSize(width.roundToInt(),height.roundToInt()))
                val mask=Path().apply {fillType=PathFillType.EvenOdd;addRect(androidx.compose.ui.geometry.Rect(0f,0f,side,side));addOval(androidx.compose.ui.geometry.Rect(0f,0f,side,side))}
                drawPath(mask,Color.Black.copy(alpha=.65f));drawCircle(Color.White,radius=side/2-2,style=androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
            Slider(zoom,{zoom=it;pan=constrain(pan,it)},valueRange=1f..5f)
            Row {TextButton(onClick=onDismiss){Text(Strings.text(R.string.ui_006))};Button(onClick={
                val output=createBitmap(512,512,Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(output)
                val factor=512f/side; val width=bitmap.width*base*zoom;val height=bitmap.height*base*zoom
                canvas.drawBitmap(bitmap,null,android.graphics.RectF(((side-width)/2+pan.x)*factor,((side-height)/2+pan.y)*factor,((side+width)/2+pan.x)*factor,((side+height)/2+pan.y)*factor),android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                @Suppress("DEPRECATION")
                val bytes=ByteArrayOutputStream().use {output.compress(Bitmap.CompressFormat.WEBP,85,it);it.toByteArray()}
                output.recycle();onSave(bytes)
            }){Text(Strings.text(R.string.ui_007))}}
        } }
    }
}
