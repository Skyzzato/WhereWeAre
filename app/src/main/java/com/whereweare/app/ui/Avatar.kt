package com.whereweare.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.whereweare.app.data.AvatarRepository
import kotlinx.coroutines.CancellationException

@Composable fun Avatar(id: String,name: String,path: String?,star: Boolean,repository: AvatarRepository,size: Dp=32.dp) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,id,path) {
        value=null
        if(path!=null) try { value=repository.load(path) } catch(e: CancellationException) { throw e } catch(_: Exception) { value=null }
    }
    val colors=listOf(0xFF147D73,0xFF52618C,0xFF9C4866,0xFF95601B,0xFF596B36)
    Box(Modifier.size(size)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(colors[(id.hashCode().toLong() and 0x7fffffff).rem(colors.size).toInt()])),contentAlignment=Alignment.Center) {
            if(path!=null && bitmap!=null) Image(bitmap!!.asImageBitmap(),name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Text(name.trim().let { if(it.isEmpty()) "?" else String(Character.toChars(it.codePointAt(0))).uppercase() },color=Color.White,style=MaterialTheme.typography.titleMedium)
        }
        if(star) Icon(Icons.Default.Star,"Gruppo in comune",tint=Color(0xFFFFC107),modifier=Modifier.size(14.dp).align(Alignment.TopEnd))
    }
}
