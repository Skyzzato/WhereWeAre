package com.whereweare.app.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.whereweare.app.R
import com.whereweare.app.domain.*
import com.whereweare.app.data.sosCountdown
import kotlinx.coroutines.*
import java.util.UUID
import kotlinx.serialization.json.jsonPrimitive

fun sosCategory(value: String)=Strings.text(when(value) {"injured" -> R.string.sos_injured;"lost" -> R.string.sos_lost;"vehicle" -> R.string.sos_vehicle;"accident" -> R.string.sos_accident;else -> R.string.sos_help})
fun sosClosureText(event: AppEvent)=Strings.text(when(event.payload["reason"]?.jsonPrimitive?.content) {"resolved" -> R.string.sos_resolved;"okay" -> R.string.sos_okay;else -> R.string.sos_accidental})
fun eventReceivedText(event: AppEvent)=if(event.kind=="sos" && event.sender_name.isBlank()) Strings.text(R.string.nearby_anonymous) else Strings.text(when(event.kind) {"place" -> R.string.place_event_received;"sos" -> R.string.sos_received;"sos_closed" -> R.string.sos_closed_received;else -> R.string.checkin_received},event.sender_name)

@Composable fun SosEditor(vm: MapViewModel,snapshot: Snapshot,close: ()->Unit) {
    val context=LocalContext.current
    val status by vm.sosSendState.collectAsStateWithLifecycle()
    val sendError by vm.sosError.collectAsStateWithLifecycle()
    var id by rememberSaveable {mutableStateOf(UUID.randomUUID().toString())}
    LaunchedEffect(status) {if(status==SosSendState.IDLE) id=UUID.randomUUID().toString()}
    var category by rememberSaveable {mutableStateOf("help")}
    var people by rememberSaveable {mutableStateOf(emptyList<String>())}
    var groups by rememberSaveable {mutableStateOf(emptyList<String>())}
    var nearby by rememberSaveable {mutableStateOf(false)}
    var choosing by rememberSaveable {mutableStateOf(false)}
    var counting by remember {mutableStateOf(false)}
    var remaining by remember {mutableIntStateOf(5)}

    LaunchedEffect(counting) {if(counting) sosCountdown({remaining=it}) {counting=false;vm.sendSos(id,category,people.toSet(),groups.toSet(),nearby)}}
    LifecycleResumeEffect(Unit) {onPauseOrDispose {counting=false}}
    LaunchedEffect(snapshot.sosAvailable) {if(!snapshot.sosAvailable) counting=false}
    val editable=!counting && status==SosSendState.IDLE
    AlertDialog(onDismissRequest={if(status!=SosSendState.SENDING) close()},title={Text("SOS",color=Color(0xFFB3261E))},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SosOutcomeCard(status,sendError,vm.sos::verify,vm.sos::dismiss)
        if(!snapshot.sosAvailable) {Text(Strings.text(R.string.sos_setup_unavailable));TextButton(onClick=vm::refresh) {Text(Strings.text(R.string.ui_124))}}
        Text(Strings.text(R.string.sos_emergency))
        Button(onClick={runCatching {context.startActivity(Intent(Intent.ACTION_DIAL,"tel:112".toUri()))}.onFailure {vm.message(R.string.error_generic)}}) {Text(Strings.text(R.string.sos_dial))}
        Text(Strings.text(R.string.sos_position_consent))
        Text(Strings.text(R.string.sos_lifetime))
        if(status==SosSendState.IDLE) {
            listOf("help","injured","lost","vehicle","accident").forEach {value -> Row {
                RadioButton(category==value,{category=value},enabled=editable);Text(sosCategory(value))
            }}
            Text(Strings.text(R.string.recipients_summary,people.size,groups.size))
            OutlinedButton(onClick={choosing=true},enabled=editable) {Text(Strings.text(R.string.recipients_title))}

        }
        if(counting) {
            Text(Strings.text(R.string.sos_countdown,remaining.toString()),style=MaterialTheme.typography.headlineMedium)
            Button(onClick={counting=false},modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)) {Text(Strings.text(R.string.sos_cancel))}
        }
        if(status==SosSendState.SENDING || status==SosSendState.CONFIRMED) Text(Strings.text(when(status) {SosSendState.SENDING -> R.string.sos_sending;SosSendState.CONFIRMED -> R.string.sos_confirmed;else -> R.string.sos_unknown}))
        if(status==SosSendState.SENDING) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(snapshot.nearbySosAvailable) {
            Row {Checkbox(nearby,{nearby=it},enabled=editable);Text(Strings.text(R.string.nearby_send))}
            Text(Strings.text(R.string.nearby_sender_consent),style=MaterialTheme.typography.bodySmall)
        } else Text(Strings.text(R.string.nearby_setup_required),style=MaterialTheme.typography.bodySmall)
    }},confirmButton={if(status!=SosSendState.CONFIRMED) TextButton(enabled=snapshot.sosAvailable && !counting && status!=SosSendState.SENDING && (status==SosSendState.UNKNOWN || status==SosSendState.FAILED || people.isNotEmpty()||groups.isNotEmpty()||nearby),onClick={counting=true}) {
        Text(Strings.text(if(status==SosSendState.UNKNOWN) R.string.sos_retry else R.string.sos_send),color=Color(0xFFB3261E))
    }},dismissButton={TextButton(enabled=status!=SosSendState.SENDING,onClick={if(status==SosSendState.CONFIRMED) vm.sos.dismiss();close()}) {Text(Strings.text(R.string.close))}})
    if(choosing) SosRecipients(snapshot,vm.avatars,people,groups,close={choosing=false},confirm={p,g -> people=p;groups=g;choosing=false})
}

@Composable fun SosDetail(vm: MapViewModel,event: AppEvent,owner: Boolean,close: ()->Unit) {
    val operation by vm.operation.collectAsStateWithLifecycle()
    var status by remember(event.id) {mutableStateOf<SosStatus?>(null)}
    var failed by remember(event.id) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LifecycleResumeEffect(event.id) {
        val job=scope.launch {
            if(owner) while(isActive) {
                try {status=vm.sosStatus(event.id);failed=false} catch(e: CancellationException) {throw e} catch(_: Exception) {status=null;failed=true}
                delay(15000)
            } else vm.respondSos(event.id,null)
        }
        onPauseOrDispose {job.cancel()}
    }
    AlertDialog(onDismissRequest=close,title={Text(Strings.text(R.string.sos_active))},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(event.sender_name.ifBlank {Strings.text(R.string.nearby_anonymous)});Text(eventTime(event.created_at));Text(Strings.text(R.string.sos_emergency))
        event.sos()?.let {payload ->
            Text(sosCategory(payload.category))
            if(payload.nearby && !payload.accepted) {
                Text(Strings.text(R.string.nearby_volunteer_consent))
                Text(Strings.text(R.string.nearby_area,payload.area_latitude.toString(),payload.area_longitude.toString()))
            }
            if(payload.recorded_at==null) Text(Strings.text(R.string.sos_no_position)) else {
                Text(eventTime(payload.recorded_at));Text(accuracyText(payload.accuracy))
            }
        }
        if(owner) {
            Text(Strings.text(R.string.sos_delivery_hint))
            if(failed) Text(Strings.text(R.string.sync_waiting))
            status?.nearby_state?.let {Text(Strings.text(if(it=="fresh_fix_required") R.string.nearby_fresh_required else if(status?.recipients?.any {r -> r.nearby}==true) R.string.nearby_help_response else R.string.nearby_waiting_response))}
            status?.recipients?.forEach {recipient -> Column {
                Text(recipient.name,style=MaterialTheme.typography.titleSmall)
                Text(Strings.text(if(recipient.viewed) R.string.sos_viewed else if(recipient.push_accepted) R.string.sos_push_accepted else R.string.sos_registered_recipient))
                recipient.response?.let {Text(Strings.text(if(it=="can_help") R.string.sos_can_help else R.string.sos_cannot_help))}
            }}
            listOf("resolved","okay","accidental").forEach {reason -> TextButton(enabled=!operation.busy,onClick={vm.closeSos(event.id,reason,close)}) {
                Text(Strings.text(when(reason) {"resolved" -> R.string.sos_resolved;"okay" -> R.string.sos_okay;else -> R.string.sos_accidental}))
            }}
        } else {
            TextButton(enabled=!operation.busy,onClick={vm.respondSos(event.id,"can_help")}) {Text(Strings.text(R.string.sos_can_help))}
            TextButton(enabled=!operation.busy,onClick={vm.respondSos(event.id,"cannot_help")}) {Text(Strings.text(R.string.sos_cannot_help))}
        }
        Busy(operation,inline=true)
    }},confirmButton={TextButton(onClick=close) {Text(Strings.text(R.string.close))}})
}

@Composable fun SosOutcomeCard(status: SosSendState,sendError: Int?,verify: ()->Unit,dismiss: ()->Unit) {
        if(status==SosSendState.UNKNOWN || status==SosSendState.FAILED) {
            val uncertain=status==SosSendState.UNKNOWN
            val foreground=if(uncertain) Color(0xFF653C00) else Color(0xFF8C1710)
            Card(colors=CardDefaults.cardColors(containerColor=if(uncertain) Color(0xFFFFE6B3) else Color(0xFFFFDAD6)),border=BorderStroke(2.dp,foreground)) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning,null,tint=foreground)
                    Text(Strings.text(if(uncertain) R.string.sos_unconfirmed_title else R.string.sos_failed),fontWeight=FontWeight.Bold,color=foreground)
                    Text(Strings.text(if(uncertain) R.string.sos_unknown else R.string.sos_failed_detail),color=foreground)
                    sendError?.let {Text(Strings.text(it),color=foreground)}
                    TextButton(onClick=verify) {Text(Strings.text(R.string.sos_verify),color=foreground)}
                    TextButton(onClick=dismiss) {Text(Strings.text(R.string.sos_dismiss),color=foreground)}
                }
            }
        }
}
