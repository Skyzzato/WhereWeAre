package com.whereweare.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.whereweare.app.BuildConfig
import com.whereweare.app.R
import com.whereweare.app.domain.*
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.*
import org.maplibre.compose.map.*
import org.maplibre.compose.map.AndroidRenderMode
import org.maplibre.compose.overlay.*
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.geojson.BoundingBox
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun MapScreen(vm: MapViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    val tracking by vm.controller.state.collectAsStateWithLifecycle()
    val pending by vm.pendingStop.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val permissionState by vm.permission.collectAsStateWithLifecycle()
    val style by vm.mapStyle.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var startAfterPermission by remember { mutableStateOf(false) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.permissionsChanged()
        if(vm.location.hasPermission()) { if(startAfterPermission) vm.start() }
        else vm.message(R.string.location_permission)
    }
    fun requestPermission(start: Boolean) {
        startAfterPermission=start
        val permissions=mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
        if(Build.VERSION.SDK_INT>=33) permissions+=Manifest.permission.POST_NOTIFICATIONS
        permission.launch(permissions.toTypedArray())
    }
    LifecycleResumeEffect(Unit) { vm.permissionsChanged(); vm.refresh(); onPauseOrDispose { } }
    val provider=MapStyle.fromId(style)
    val baseStyle=remember(provider) { provider.tileUrl?.let { BaseStyle.Json(provider.rasterJson()) } ?: BaseStyle.Uri(BuildConfig.MAP_STYLE_URL) }
    val camera=rememberMapState(baseStyle=baseStyle,
        initialCameraPosition=local?.let { CameraPosition(target=Position(it.longitude,it.latitude),zoom=14.0) } ?: CameraPosition(zoom=1.0))
    var centered by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var mapError by remember { mutableStateOf(false) }
    LaunchedEffect(camera) {
        camera.events.collect { event ->
            when(event) {
                is MapEvent.StyleLoadFailed -> mapError=true
                MapEvent.StyleLoaded -> mapError=false
                else -> Unit
            }
        }
    }
    LaunchedEffect(local) {
        if(!centered && local!=null) { camera.setCameraPosition(CameraPosition(target=Position(local!!.longitude,local!!.latitude),zoom=14.0)); centered=true }
    }
    Column(Modifier.fillMaxSize()) {
        if(state.snapshot.offline) Notice(R.string.offline)
        if(state.snapshot.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Busy(operation)
        if(permissionState==LocationPermission.APPROXIMATE) Surface(color=MaterialTheme.colorScheme.errorContainer) {
            Text("Posizione precisa non autorizzata: Android concede soltanto la posizione approssimativa.",Modifier.fillMaxWidth().padding(12.dp))
        }
        local?.takeIf { lowAccuracy(it.accuracy,threshold) }?.let { fix -> Surface(color=MaterialTheme.colorScheme.errorContainer) {
            Text("Precisione GPS bassa · ±${fix.accuracy.toLong()} m",Modifier.fillMaxWidth().padding(12.dp))
        } }
        if(mapError) Notice(R.string.map_error)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MaplibreMap(
                modifier=Modifier.fillMaxSize(),state=camera,
                uiOptions=MapUiOptions { renderMode=AndroidRenderMode.Texture },
                overlay={
                    include(MapOverlay.Default)
                    local?.let { fix ->
                        Surface(onClick={ selectedId=if(selectedId==fix.userId) null else fix.userId },
                            shape=androidx.compose.foundation.shape.CircleShape,modifier=Modifier.placedAt(Position(fix.longitude,fix.latitude))) {
                            Avatar(fix.userId,state.snapshot.profile?.displayName ?: "Tu",state.snapshot.profile?.avatarPath,false,vm.avatars,if(selectedId==fix.userId) 54.dp else 32.dp)
                        }
                    }
                    state.visible.forEach { person ->
                        Surface(onClick={ selectedId=if(selectedId==person.location.userId) null else person.location.userId },shape=androidx.compose.foundation.shape.CircleShape,
                            color=MaterialTheme.colorScheme.tertiaryContainer,
                            modifier=Modifier.placedAt(Position(person.location.longitude,person.location.latitude))) {
                            val p=state.snapshot.contacts[person.location.userId]
                            Avatar(person.location.userId,person.name,p?.avatarPath,p?.commonGroup==true,vm.avatars,if(selectedId==person.location.userId) 54.dp else 32.dp)
                        }
                    }
                })
            Column(Modifier.align(Alignment.TopStart).padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(enabled=local!=null,onClick={ if(local!=null) { scope.launch { camera.animateCameraPosition(CameraPosition(target=Position(local!!.longitude,local!!.latitude),zoom=15.0)) } } }) {
                    Text(stringResource(R.string.center_me))
                }
                FilledTonalButton(onClick={
                    val points=state.visible.map { it.location }+listOfNotNull(local)
                    if(points.isNotEmpty()) scope.launch {
                        if(points.size==1) camera.animateCameraPosition(CameraPosition(target=Position(points[0].longitude,points[0].latitude),zoom=14.0))
                        else camera.animateCameraToBounds(BoundingBox(west=points.minOf { it.longitude }-0.001,south=points.minOf { it.latitude }-0.001,
                            east=points.maxOf { it.longitude }+0.001,north=points.maxOf { it.latitude }+0.001),padding=PaddingValues(60.dp))
                    }
                }) { Text(stringResource(R.string.fit_all)) }
            }
            if(state.visible.isEmpty()) Surface(modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=48.dp),shape=MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.empty_map),Modifier.padding(8.dp),style=MaterialTheme.typography.labelMedium)
            }
            val selected=(state.visible+listOfNotNull(local?.let { VisiblePerson("Tu",it,freshness(it.recordedAt,state.now)) })).firstOrNull { it.location.userId==selectedId }
            selected?.let { person -> ElevatedCard(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text(person.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium); TextButton(onClick={selectedId=null}) { Text("Chiudi") } }
                Text(freshnessText(person.freshness,person.location.recordedAt,state.now))
                Text("Precisione ±${person.location.accuracy.toLong()} m")
                Text(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(person.location.recordedAt))
                Text(coordinateLabel(person.location.latitude,person.location.longitude)?.let { "Coordinate: $it" } ?: "Posizione non disponibile")
                openStreetMapUrl(person.location.latitude,person.location.longitude)?.let { url ->
                    TextButton(onClick={
                        try { context.startActivity(Intent(Intent.ACTION_VIEW,url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        catch(_: android.content.ActivityNotFoundException) { vm.message(R.string.error_generic) }
                    }) { Text("Apri in OpenStreetMap") }
                }
            } } }
        }
        Surface { Text(provider.attribution,Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall) }
        Surface(tonalElevation=3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                val serverSharing=state.snapshot.statuses.any { it.userId==state.snapshot.profile?.id && it.sharing }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.sharing),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(if(tracking.active) R.string.on else R.string.off),color=if(tracking.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                if(tracking.active) Text(if(tracking.waiting) "Ricerca posizione… / attesa connessione" else "Posizione condivisa · ±${tracking.fix?.accuracy?.toLong()} m",style=MaterialTheme.typography.bodySmall)
                if(pending!=null) Notice(R.string.stop_pending)
                if(serverSharing && !tracking.active && pending==null) Notice(R.string.process_stopped)
                Button(onClick={ if(tracking.active || serverSharing) vm.stop() else requestPermission(true) },enabled=pending==null,modifier=Modifier.fillMaxWidth()) {
                    Text(stringResource(if(tracking.active || serverSharing) R.string.stop else R.string.start))
                }
                if(!vm.location.enabled()) TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text(stringResource(R.string.open_location_settings)) }
                if(permissionState!=LocationPermission.PRECISE) TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri())) }) { Text("Autorizza posizione precisa") }
            }
        }
    }
}
