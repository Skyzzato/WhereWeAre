package com.whereweare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whereweare.app.BuildConfig
import com.whereweare.app.R
import com.whereweare.app.domain.*
import org.maplibre.compose.camera.*
import org.maplibre.compose.map.*
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

@Composable fun PlaceIcon(emoji: String,scale: Float,label: String,onClick: ()->Unit) {
    val size=mapPlaceIconDp(scale)
    Box(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).clickable(onClick=onClick).semantics {contentDescription=label}.padding(6.dp),contentAlignment=Alignment.Center) {
        if(emoji.isBlank()) Icon(Icons.Default.Place,null,Modifier.size(size.dp))
        else Text(emoji,fontSize=size.sp,lineHeight=(size*1.25f).sp)
    }
}

/** A separate map with no sharing, check-in or meeting actions. Only confirmation edits the draft. */
@Composable fun PlaceLocationPicker(latitude: Double?,longitude: Double?,fix: UserLocation?,style: String,close: ()->Unit,confirm: (Double,Double)->Unit) {
    val initial=remember {placePickerInitial(latitude,longitude,fix)}
    val provider=MapStyle.fromId(style)
    val base=remember(provider) {provider.tileUrl?.let {BaseStyle.Json(provider.rasterJson())} ?: BaseStyle.Uri(BuildConfig.MAP_STYLE_URL)}
    val camera=rememberMapState(baseStyle=base,initialCameraPosition=CameraPosition(target=Position(initial.second,initial.first),zoom=if(latitude!=null || fix!=null) 15.0 else 4.0))
    var error by remember {mutableStateOf(false)}
    LaunchedEffect(camera) {camera.events.collect {when(it) {is MapEvent.StyleLoadFailed -> error=true;MapEvent.StyleLoaded -> error=false;else -> Unit}}}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.safeDrawingPadding().padding(16.dp)) {
            Text(Strings.text(R.string.place_choose_map),style=MaterialTheme.typography.titleLarge)
            Text(Strings.text(R.string.place_map_hint))
            if(error) Text(Strings.text(R.string.map_error),color=MaterialTheme.colorScheme.error)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MaplibreMap(modifier=Modifier.fillMaxSize(),state=camera,uiOptions=remember {MapUiOptions {renderMode=AndroidRenderMode.Texture}})
                Icon(Icons.Default.Add,null,Modifier.align(Alignment.Center).size(44.dp),tint=androidx.compose.ui.graphics.Color.White)
                Icon(Icons.Default.Add,Strings.text(R.string.place_selected_point),Modifier.align(Alignment.Center).size(40.dp),tint=androidx.compose.ui.graphics.Color.Black)
            }
            val target=camera.cameraPosition.target
            Text("${target.latitude}, ${target.longitude}")
            Row {TextButton(onClick=close) {Text(Strings.text(R.string.close))}
                Button(enabled=validPlace("point",target.latitude.toString(),target.longitude.toString(),"100"),onClick={confirm(target.latitude,target.longitude)}) {Text(Strings.text(R.string.place_use_position))}}
        }}
    }
}

@Composable fun MapActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector,description: String,onClick: ()->Unit) {
    FloatingActionButton(containerColor=sharingActionColor(),contentColor=androidx.compose.ui.graphics.Color.Black,onClick=onClick) {Icon(icon,description)}
}
