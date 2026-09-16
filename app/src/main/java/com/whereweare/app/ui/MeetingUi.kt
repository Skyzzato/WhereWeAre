package com.whereweare.app.ui

import com.whereweare.app.R

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.whereweare.app.domain.*
import org.maplibre.compose.map.MapState
import org.maplibre.spatialk.geojson.Position
import kotlin.math.*

data class MapFocus(val person: String?=null,val meeting: String?=null,val event: String?=null,val place: SavedPlace?=null)
@Composable fun MeetingEditor(snapshot: Snapshot,confirm: (Boolean,Set<String>,Set<String>)->Unit,cancel: ()->Unit) {
    var all by remember {mutableStateOf(true)}
    var people by remember {mutableStateOf(emptySet<String>())}
    var groups by remember {mutableStateOf(emptySet<String>())}
    AlertDialog(onDismissRequest=cancel,title={Text(Strings.text(R.string.ui_058))},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
        Row {Checkbox(all,{all=it});Text(Strings.text(R.string.ui_059))}
        Text(Strings.text(R.string.ui_060),style=MaterialTheme.typography.titleSmall)
        snapshot.contacts.values.filter {it.id!=snapshot.profile?.id}.forEach { p -> Row {Checkbox(p.id in people,{people=if(it) people+p.id else people-p.id});Text(p.name)} }
        Text(Strings.text(R.string.ui_061),style=MaterialTheme.typography.titleSmall)
        snapshot.groups.forEach { g -> Row {Checkbox(g.id in groups,{groups=if(it) groups+g.id else groups-g.id});GroupIdentity(g.emoji,g.name,Modifier.weight(1f))} }
    }},confirmButton={TextButton(onClick={confirm(all,people,groups)},enabled=all||people.isNotEmpty()||groups.isNotEmpty()){Text(Strings.text(R.string.ui_049))}},dismissButton={TextButton(onClick=cancel){Text(Strings.text(R.string.ui_006))}})
}
@Composable fun MeetingConnections(camera: MapState,meetings: List<MeetingPoint>,people: List<UserLocation>) {
    if(meetings.none {it.active}) return
    val transition=rememberInfiniteTransition(label="meeting")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(2000,easing=LinearEasing)),label="direction")
    val color=MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        camera.cameraPosition // Subscribe drawing to camera changes.
        meetings.filter {it.active}.forEach {meeting ->
            val endpoint=camera.screenLocationFromPosition(Position(meeting.longitude,meeting.latitude)) ?: return@forEach
            val end=Offset(endpoint.x.toPx(),endpoint.y.toPx())
            people.filter {it.userId in meeting.recipients}.forEach point@ {person ->
                val startpoint=camera.screenLocationFromPosition(Position(person.longitude,person.latitude)) ?: return@point
                val start=Offset(startpoint.x.toPx(),startpoint.y.toPx()); val delta=end-start
                drawLine(color.copy(alpha=.45f),start,end,2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),6.dp.toPx())))
                val length=delta.getDistance(); if(length>40.dp.toPx()) {
                    val direction=delta/length; val center=start+delta*phase; val side=Offset(-direction.y,direction.x)*4.dp.toPx()
                    drawLine(color.copy(alpha=.7f),center-direction*7.dp.toPx()+side,center,2.dp.toPx())
                    drawLine(color.copy(alpha=.7f),center-direction*7.dp.toPx()-side,center,2.dp.toPx())
                }
            }
        }
    }
}
@Composable fun MeetingFlare(id: String?,finished: ()->Unit) {
    if(id==null) return
    val progress=remember(id) {Animatable(0f)}
    LaunchedEffect(id) {progress.animateTo(1f,tween(1300,easing=LinearEasing));finished()}
    val color=MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val p=progress.value; val center=Offset(size.width*.7f,size.height*(.95f-.65f*(p/.65f).coerceAtMost(1f)))
        if(p<.65f) {drawLine(color.copy(alpha=.5f),center+Offset(-8f,70f),center,5f);drawCircle(color,6f,center)}
        else {val burst=(p-.65f)/.35f;repeat(10) {i -> val angle=i*PI/5;val radius=burst*size.minDimension*.16f
            val ray=Offset(cos(angle).toFloat(),sin(angle).toFloat());drawLine(color.copy(alpha=1f-burst),center+ray*radius*.6f,center+ray*radius,3f)
        };drawCircle(color.copy(alpha=(1f-burst)*.35f),burst*60f,center,style=Stroke(3f))}
    }
}
