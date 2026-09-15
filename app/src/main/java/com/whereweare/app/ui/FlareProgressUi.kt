package com.whereweare.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.whereweare.app.R
import com.whereweare.app.domain.MeetingPoint
import java.time.Instant

@Composable fun FlareProgressDialog(point: MeetingPoint,now: Instant,close: ()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text(Strings.text(if(point.completed_at!=null) R.string.flare_reunited else R.string.ui_046))},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(Strings.text(R.string.flare_radius,point.radius_m.toString()))
            if(point.progress.isEmpty()) Text(Strings.text(R.string.flare_unavailable))
            point.progress.forEach {person -> Column {
                Text(person.name,style=MaterialTheme.typography.titleSmall)
                val status=person.currentState(now)
                Text(Strings.text(when(status) {"arrived" -> R.string.flare_arrived;"stale" -> R.string.flare_stale;"approaching" -> R.string.flare_approaching;"uncertain" -> R.string.flare_uncertain;else -> R.string.flare_unavailable}))
                if(status!="arrived" && person.distance_m!=null) {
                    Text(Strings.text(R.string.flare_air_distance,person.distance_m.toLong().toString()))
                    if(person.precision_m>0) Text(precisionLabel(person.precision_m))
                }
            }}
            Text(Strings.text(R.string.flare_eta_pending),style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick=close) {Text(Strings.text(R.string.close))}})
}

/** Three friends around a circular platform lift a shared pin with curved ropes. */
@Composable fun FlareReunion(close: ()->Unit) {
    val lift=remember {Animatable(0f)}
    LaunchedEffect(Unit) {lift.animateTo(1f,tween(2200,easing=FastOutSlowInEasing))}
    val color=MaterialTheme.colorScheme.primary
    val secondary=MaterialTheme.colorScheme.tertiary
    AlertDialog(onDismissRequest=close,title={Text(Strings.text(R.string.flare_reunited))},text={
        Canvas(Modifier.fillMaxWidth().height(210.dp)) {
            val w=size.width;val h=size.height;val top=Offset(w*.5f,h*(.55f-.3f*lift.value))
            drawOval(color.copy(alpha=.12f),Offset(w*.08f,h*.73f),androidx.compose.ui.geometry.Size(w*.84f,h*.2f))
            listOf(.2f,.5f,.8f).forEachIndexed {index,x ->
                val foot=Offset(w*x,h*(if(index==1) .93f else .83f));val head=foot-Offset(0f,h*.24f)
                val hand=head+Offset(if(index==2) -w*.07f else w*.07f,h*.04f-h*.05f*lift.value)
                val rope=Path().apply {moveTo(hand.x,hand.y);quadraticTo((hand.x+top.x)/2,h*.55f,top.x,top.y+h*.11f)}
                drawPath(rope,secondary.copy(alpha=.7f),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawCircle(secondary,7.dp.toPx(),head)
                drawLine(color,head+Offset(0f,9.dp.toPx()),foot-Offset(0f,h*.1f),5.dp.toPx())
                drawLine(color,head+Offset(0f,h*.07f),hand,4.dp.toPx())
                drawLine(color,foot-Offset(0f,h*.1f),foot+Offset(-9.dp.toPx(),0f),4.dp.toPx())
                drawLine(color,foot-Offset(0f,h*.1f),foot+Offset(9.dp.toPx(),0f),4.dp.toPx())
            }
            val pin=Path().apply {moveTo(top.x,top.y+h*.17f);cubicTo(top.x-w*.22f,top.y-h*.07f,top.x+w*.22f,top.y-h*.07f,top.x,top.y+h*.17f)}
            drawPath(pin,color);drawCircle(androidx.compose.ui.graphics.Color.White,7.dp.toPx(),top+Offset(0f,h*.025f))
        }
    },confirmButton={TextButton(onClick=close) {Text(Strings.text(R.string.close))}})
}
