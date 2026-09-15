package com.whereweare.app.ui

import com.whereweare.app.R

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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.whereweare.app.BuildConfig
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

@Composable fun MapScreen(vm: MapViewModel,focus: MapFocus?=null,focused: ()->Unit={}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    val tracking by vm.controller.state.collectAsStateWithLifecycle()
    val pending by vm.pendingStop.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val permissionState by vm.permission.collectAsStateWithLifecycle()
    val style by vm.mapStyle.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val updateInterval by vm.updateInterval.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val avatarScale by vm.avatarScale.collectAsStateWithLifecycle()
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
    var follow by rememberSaveable {mutableStateOf(false)}
    var creatingMeeting by rememberSaveable {mutableStateOf(false)}
    var choosingRecipients by remember {mutableStateOf(false)}
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedEvent by remember {mutableStateOf<String?>(null)}
    var actions by remember {mutableStateOf(false)}
    var checkinEditor by rememberSaveable {mutableStateOf(false)}
    var checkinInbox by rememberSaveable {mutableStateOf(false)}
    val events=state.snapshot.events.filter {it.active(state.now) && it.checkin()!=null}
    LaunchedEffect(events) {if(events.none {it.id==selectedEvent}) selectedEvent=null}
    var mapError by remember { mutableStateOf(false) }
    LaunchedEffect(camera) {
        camera.events.collect { event ->
            when(event) {
                is MapEvent.StyleLoadFailed -> mapError=true
                MapEvent.StyleLoaded -> mapError=false
                is MapEvent.CameraMoveStarted -> if(camera.cameraMoveReason==CameraMoveReason.GESTURE) follow=false
                else -> Unit
            }
        }
    }
    LaunchedEffect(local) {
        if(local!=null && (follow || !centered)) { camera.setCameraPosition(camera.cameraPosition.copy(target=Position(local!!.longitude,local!!.latitude),zoom=if(!centered) 14.0 else camera.cameraPosition.zoom)); centered=true }
    }
    LaunchedEffect(focus,state.snapshot.meetings,state.visible,events) {
        val event=events.firstOrNull {it.id==focus?.event}
        event?.checkin()?.let {payload ->
            selectedEvent=event.id;selectedId=null;follow=false
            camera.setCameraPosition(CameraPosition(target=Position(payload.longitude,payload.latitude),zoom=if(payload.precision_m>0) 12.0 else 16.0));focused()
        }
        val point=state.snapshot.meetings.firstOrNull {it.id==focus?.meeting && it.active}
        val person=state.visible.firstOrNull {it.location.userId==focus?.person}
        if(point!=null || person!=null) {follow=false;selectedEvent=null;selectedId=person?.location?.userId
            camera.setCameraPosition(CameraPosition(target=point?.let {Position(it.longitude,it.latitude)} ?: Position(person!!.location.longitude,person.location.latitude),zoom=if((person?.location?.precisionMeters ?: 0)>0) 12.0 else 16.0));focused()}
    }
    Column(Modifier.fillMaxSize()) {
        if(!online) Notice(R.string.connection_absent)
        else if(state.snapshot.syncFailed && !state.snapshot.loading) Notice(R.string.sync_waiting)
        else if(state.snapshot.realtimeUnavailable) Notice(R.string.realtime_unavailable)
        if(state.visible.any {stalePosition(it.location,state.snapshot.contacts[it.location.userId],state.now,config.config?.defaults?.stale_grace_seconds?.coerceIn(30,3600) ?: 180)}) Notice(R.string.positions_stale)
        if(local?.recordedAt?.isBefore(state.now.minusSeconds(updateInterval.toLong()+180))==true) Notice(R.string.local_position_stale)
        Busy(operation)
        if(!vm.location.enabled()) Notice(R.string.location_disabled)
        if(permissionState==LocationPermission.APPROXIMATE) Surface(color=MaterialTheme.colorScheme.errorContainer) {
            Text(Strings.text(R.string.ui_044),Modifier.fillMaxWidth().padding(12.dp))
        }
        local?.takeIf { lowAccuracy(it.accuracy,threshold) }?.let { fix -> Surface(color=MaterialTheme.colorScheme.errorContainer) {
            Text(Strings.text(R.string.ui_045, (fix.accuracy.toLong()).toString()),Modifier.fillMaxWidth().padding(12.dp))
        } }
        if(mapError) Notice(R.string.map_error)
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            MaplibreMap(
                modifier=Modifier.fillMaxSize(),state=camera,
                uiOptions=MapUiOptions { renderMode=AndroidRenderMode.Texture },
                overlay={
                    include(MapOverlay.Default)
                    events.forEach {event -> event.checkin()?.let {payload ->
                        Surface(onClick={selectedId=null;selectedEvent=event.id},modifier=Modifier.placedAt(Position(payload.longitude,payload.latitude)),shape=MaterialTheme.shapes.small) {
                            if(payload.precision_m>0) Text("${Strings.text(R.string.checkin_title)} · ${event.sender_name}\n${precisionLabel(payload.precision_m)}",Modifier.padding(6.dp),style=MaterialTheme.typography.labelSmall)
                            else Icon(Icons.Default.CheckCircle,"${event.sender_name}: ${checkinLabel(payload.checkin_type)}",Modifier.size(40.dp).padding(6.dp),tint=MaterialTheme.colorScheme.tertiary)
                        }
                    }}
                    state.visible.filter {it.location.precisionMeters>0}.forEach {person ->
                        OutlinedButton(onClick={selectedEvent=null;selectedId=person.location.userId},modifier=Modifier.placedAt(Position(person.location.longitude,person.location.latitude))) {
                            Text("${person.name}\n${precisionLabel(person.location.precisionMeters)}")
                        }
                    }
                    val markers=state.visible.filter {it.location.precisionMeters==0}+listOfNotNull(local?.let {VisiblePerson(state.snapshot.profile?.displayName.orEmpty(),it,freshness(it.recordedAt,state.now))})
                    val projected=markers.mapIndexed {index,person ->
                        val at=camera.screenLocationFromPosition(Position(person.location.longitude,person.location.latitude))
                        MarkerScreenPoint(index,at?.x?.value ?: (index*10000).toFloat(),at?.y?.value ?: 0f)
                    }
                    clusterMarkers(projected,maxOf(48f,54f*avatarScale)).forEach {cluster ->
                        val person=markers[cluster.first()]
                        Box(modifier=Modifier.placedAt(Position(person.location.longitude,person.location.latitude))) {
                            if(cluster.size>1) {
                                var expanded by remember {mutableStateOf(false)}
                                FilledTonalButton(onClick={expanded=true},modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {Text(cluster.size.toString())}
                                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                                    cluster.forEach {index -> val member=markers[index]
                                        DropdownMenuItem(text={Text(member.name)},onClick={selectedEvent=null;selectedId=member.location.userId;expanded=false})
                                    }
                                }
                            } else {
                                Box(Modifier.clickable {selectedEvent=null;selectedId=if(selectedId==person.location.userId) null else person.location.userId}) {
                                    Box(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).padding(5.dp),contentAlignment=Alignment.Center) {
                                        val own=person.location.userId==state.snapshot.profile?.id
                                        val contact=state.snapshot.contacts[person.location.userId]
                                        Avatar(person.location.userId,person.name,if(own) state.snapshot.profile?.avatarPath else contact?.avatarPath,contact?.commonGroup==true,vm.avatars,(if(selectedId==person.location.userId) 54.dp else 32.dp)*avatarScale)
                                    }
                                }
                            }
                        }
                    }
                    state.snapshot.meetings.filter {it.active}.forEach {point ->
                        Surface(onClick={scope.launch {follow=false;camera.setCameraPosition(CameraPosition(target=Position(point.longitude,point.latitude),zoom=16.0))}},color=androidx.compose.ui.graphics.Color.Transparent,
                            modifier=Modifier.placedAt(Position(point.longitude,point.latitude))) {Icon(Icons.Default.Flag,point.creator_name,Modifier.size(48.dp).padding(4.dp),tint=MaterialTheme.colorScheme.primary)}
                    }
                })
            ApproximateAreas(camera,state.visible.map {it.location}+events.mapNotNull {it.checkin()?.location(it.id)})
            MeetingConnections(camera,state.snapshot.meetings,state.visible.map {it.location}.filter {it.precisionMeters==0}+listOfNotNull(local))
            Column(Modifier.align(Alignment.TopStart).padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(enabled=local!=null,colors=ButtonDefaults.filledTonalButtonColors(containerColor=if(follow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer),onClick={ if(local!=null) { follow=true; scope.launch { camera.animateCameraPosition(CameraPosition(target=Position(local!!.longitude,local!!.latitude),zoom=15.0)) } } }) {
                    Text(stringResource(R.string.center_me))
                }
                FilledTonalButton(onClick={
                    follow=false
                    val points=state.visible.map { it.location }+listOfNotNull(local)
                    if(points.isNotEmpty()) scope.launch {
                        if(points.size==1) camera.animateCameraPosition(CameraPosition(target=Position(points[0].longitude,points[0].latitude),zoom=14.0))
                        else camera.animateCameraToBounds(BoundingBox(west=points.minOf { it.longitude }-0.001,south=points.minOf { it.latitude }-0.001,
                            east=points.maxOf { it.longitude }+0.001,north=points.maxOf { it.latitude }+0.001),padding=PaddingValues(60.dp))
                    }
                }) { Text(stringResource(R.string.fit_all)) }
            }
            if(!creatingMeeting) Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                FloatingActionButton(onClick={actions=true}) {Icon(Icons.Default.MoreVert,Strings.text(R.string.map_actions))}
                DropdownMenu(actions,{actions=false}) {
                    if(config.config?.features?.meeting_points!=false) DropdownMenuItem(text={Text(Strings.text(R.string.ui_046))},onClick={actions=false;follow=false;creatingMeeting=true})
                    if(state.snapshot.eventsAvailable) {
                        DropdownMenuItem(text={Text(Strings.text(R.string.checkin_title))},onClick={actions=false;vm.message(null);checkinEditor=true})
                        DropdownMenuItem(text={Text(Strings.text(R.string.checkin_inbox))},onClick={actions=false;checkinInbox=true})
                    }
                }
            }
            if(creatingMeeting) {
                Icon(Icons.Default.Flag,Strings.text(R.string.ui_047),Modifier.align(Alignment.Center).size(48.dp),tint=MaterialTheme.colorScheme.primary)
                Surface(Modifier.align(Alignment.BottomCenter)) {Column {Text(Strings.text(R.string.ui_048));Row {
                    TextButton(onClick={creatingMeeting=false}) {Text(Strings.text(R.string.ui_006))}
                    Button(onClick={choosingRecipients=true}) {Text(Strings.text(R.string.ui_049))}
                }}}
            }
            if(state.visible.isEmpty() && events.isEmpty() && !creatingMeeting) Surface(modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=48.dp),shape=MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.empty_map),Modifier.padding(8.dp),style=MaterialTheme.typography.labelMedium)
            }
            val selected=(state.visible+listOfNotNull(local?.let { VisiblePerson(Strings.text(R.string.you),it,freshness(it.recordedAt,state.now)) })).firstOrNull { it.location.userId==selectedId }
            selected?.takeUnless {creatingMeeting || selectedEvent!=null}?.let { person -> ElevatedCard(Modifier.align(Alignment.BottomCenter).padding(16.dp).heightIn(max=360.dp)) { Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text(person.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium); TextButton(onClick={selectedId=null}) { Text(Strings.text(R.string.close)) } }
                val own=person.location.userId==state.snapshot.profile?.id
                val stale=if(own) person.location.recordedAt.isBefore(state.now.minusSeconds(updateInterval.toLong()+180))
                    else stalePosition(person.location,state.snapshot.contacts[person.location.userId],state.now,config.config?.defaults?.stale_grace_seconds ?: 180)
                if(stale) Text(stringResource(R.string.person_position_stale),color=MaterialTheme.colorScheme.error)
                Text(freshnessText(person.freshness,person.location.recordedAt,state.now))
                if(person.location.precisionMeters>0) Text(precisionLabel(person.location.precisionMeters),color=MaterialTheme.colorScheme.tertiary)
                else Text(Strings.text(R.string.ui_050, (person.location.accuracy.toLong()).toString()))
                val deviceAt=if(own) tracking.deviceStatus?.observedAt else person.location.deviceStatusAt
                val deviceRecent=deviceStatusRecent(deviceAt,if(own) java.time.Instant.now() else state.now)
                val battery=(if(own) tracking.deviceStatus?.status?.batteryLevel else person.location.batteryLevel).takeIf {deviceRecent}
                val services=(if(own) tracking.deviceStatus?.status?.locationEnabled else person.location.locationEnabled).takeIf {deviceRecent}
                Text(battery?.let {stringResource(R.string.person_battery,it)} ?: stringResource(R.string.person_battery_unknown))
                Text(stringResource(when(services) {true->R.string.person_location_on;false->R.string.person_location_off;null->R.string.person_location_unknown}))
                deviceAt?.let {Text(stringResource(R.string.person_device_updated,DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(it)),style=MaterialTheme.typography.bodySmall)}
                Text(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(person.location.recordedAt))
                if(person.location.precisionMeters==0) {
                Text(coordinateLabel(person.location.latitude,person.location.longitude)?.let { Strings.text(R.string.ui_051, (it).toString()) } ?: Strings.text(R.string.ui_052))
                openStreetMapUrl(person.location.latitude,person.location.longitude)?.let { url ->
                    TextButton(onClick={
                        try { context.startActivity(Intent(Intent.ACTION_VIEW,url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        catch(_: android.content.ActivityNotFoundException) { vm.message(R.string.error_generic) }
                    }) { Text(Strings.text(R.string.ui_053)) }
                }
                googleMapsUrl(person.location.latitude,person.location.longitude)?.let {url ->
                    TextButton(onClick={
                        try {context.startActivity(Intent(Intent.ACTION_VIEW,url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))}
                        catch(_: android.content.ActivityNotFoundException) {vm.message(R.string.error_generic)}
                    }) {Text(stringResource(R.string.person_open_google))}
                }
                }
            } } }
            events.firstOrNull {it.id==selectedEvent}?.let {event -> event.checkin()?.let {payload ->
                ElevatedCard(Modifier.align(Alignment.BottomCenter).padding(16.dp).heightIn(max=300.dp)) {Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {Text(event.sender_name,Modifier.weight(1f));TextButton(onClick={selectedEvent=null}) {Text(Strings.text(R.string.close))}}
                    Text(checkinLabel(payload.checkin_type),style=MaterialTheme.typography.titleMedium)
                    Text(eventTime(payload.recorded_at));Text(precisionLabel(payload.precision_m))
                    if(payload.message.isNotBlank()) Text(payload.message)
                    if(event.sender_id==state.snapshot.profile?.id) TextButton(onClick={vm.removeEvent(event.id);selectedEvent=null},enabled=!operation.busy) {Text(Strings.text(R.string.checkin_remove))}
                }}
            }}
        }
        Surface { Text(provider.attribution,Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall) }
        state.snapshot.meetings.firstOrNull {it.active && it.creator_id==state.snapshot.profile?.id}?.let {point -> TextButton(onClick={vm.removeMeeting(point.id)},enabled=!operation.busy) {Text(Strings.text(R.string.ui_054))} }
        Surface(tonalElevation=3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                val serverSharing=state.snapshot.statuses.any { it.userId==state.snapshot.profile?.id && it.sharing }
                val sharingState=com.whereweare.app.domain.sharingUiState(tracking.active,tracking.starting,serverSharing,pending!=null)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.sharing),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(when(sharingState) {
                        SharingUiState.OFF -> R.string.off
                        SharingUiState.ON -> R.string.on
                        SharingUiState.STARTING -> R.string.sharing_starting
                        SharingUiState.STOPPING -> R.string.sharing_stopping
                        SharingUiState.REMOTE_ACTIVE -> R.string.remote_session_active
                    }),Modifier.padding(start=8.dp),color=if(tracking.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                if(tracking.active) Text(if(tracking.waiting) Strings.text(R.string.ui_055) else Strings.text(R.string.ui_056, (tracking.fix?.accuracy?.toLong()).toString()),style=MaterialTheme.typography.bodySmall)
                if(pending!=null) Notice(R.string.stop_pending)
                if(tracking.pendingUpload) Notice(R.string.location_upload_pending)
                if(tracking.waiting) Notice(R.string.sync_waiting)
                if(sharingState==SharingUiState.REMOTE_ACTIVE) Notice(R.string.process_stopped)
                Button(onClick={ if(sharingState==SharingUiState.OFF) requestPermission(true) else vm.stop() },enabled=sharingState!=SharingUiState.STOPPING,modifier=Modifier.fillMaxWidth()) {
                    Text(stringResource(when(sharingState) {SharingUiState.ON -> R.string.stop; SharingUiState.REMOTE_ACTIVE -> R.string.reconcile_stop; SharingUiState.STARTING -> R.string.ui_006; SharingUiState.STOPPING -> R.string.sharing_stopping; SharingUiState.OFF -> R.string.start}))
                }
                if(!vm.location.enabled()) TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text(stringResource(R.string.open_location_settings)) }
                if(permissionState!=LocationPermission.PRECISE) TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri())) }) { Text(Strings.text(R.string.ui_057)) }
            }
        }
    }
    if(choosingRecipients) MeetingEditor(state.snapshot,confirm={all,people,groups ->
        val point=camera.cameraPosition.target
        vm.meeting(point.latitude,((point.longitude+180)%360+360)%360-180,all,people,groups);choosingRecipients=false;creatingMeeting=false
    },cancel={choosingRecipients=false})
    if(checkinEditor) CheckinEditor(state.snapshot,operation,permissionState!=LocationPermission.NONE,{requestPermission(false)},
        send={id,type,message,people,groups,meeting -> vm.checkin(id,type,message,people,groups,meeting) {checkinEditor=false}},close={checkinEditor=false})
    if(checkinInbox) CheckinInbox(events,state.snapshot,open={event -> checkinInbox=false;selectedId=null;selectedEvent=event.id;event.checkin()?.let {payload -> scope.launch {
        follow=false;camera.setCameraPosition(CameraPosition(target=Position(payload.longitude,payload.latitude),zoom=if(payload.precision_m>0) 12.0 else 16.0))
    }}},close={checkinInbox=false})
}
