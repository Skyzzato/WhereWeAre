package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whereweare.app.R
import com.whereweare.app.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

fun checkinLabel(type: String)=Strings.text(when(type) {"arrived"->R.string.checkin_arrived;"okay"->R.string.checkin_okay;else->R.string.checkin_here})
fun eventTime(at: String)=runCatching {DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(at))}.getOrDefault("")
@Composable fun CheckinEditor(snapshot: Snapshot,operation: OperationState,hasPermission: Boolean,permission: ()->Unit,
    send: (String,String,String,Set<String>,Set<String>,String?)->Unit,close: ()->Unit) {
    var type by rememberSaveable {mutableStateOf("here")}
    var message by rememberSaveable {mutableStateOf("")}
    var people by rememberSaveable {mutableStateOf(listOf<String>())}
    var groups by rememberSaveable {mutableStateOf(listOf<String>())}
    var meeting by rememberSaveable {mutableStateOf<String?>(null)}
    val id=rememberSaveable(type,message,people,groups,meeting) {UUID.randomUUID().toString()}
    AlertDialog(onDismissRequest={if(!operation.busy) close()},title={Text(Strings.text(R.string.checkin_title))},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(Strings.text(R.string.checkin_hint))
            listOf("here","arrived","okay").forEach {value -> Row(verticalAlignment=Alignment.CenterVertically) {
                RadioButton(type==value,{type=value},enabled=!operation.busy);Text(checkinLabel(value))
            }}
            OutlinedTextField(message,{if(it.length<=160) message=it},enabled=!operation.busy,label={Text(Strings.text(R.string.checkin_message))})
            Text(Strings.text(R.string.people),style=MaterialTheme.typography.titleSmall)
            snapshot.contacts.values.filter {it.id!=snapshot.profile?.id}.forEach {person -> Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(person.id in people,{people=if(it) people+person.id else people-person.id},enabled=!operation.busy);Text(person.name)
            }}
            Text(Strings.text(R.string.ui_119),style=MaterialTheme.typography.titleSmall)
            snapshot.groups.forEach {group -> Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(group.id in groups,{groups=if(it) groups+group.id else groups-group.id},enabled=!operation.busy);Text(group.name)
            }}
            snapshot.meetings.filter {it.active}.forEach {point -> Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(meeting==point.id,{meeting=if(it) point.id else null},enabled=!operation.busy)
                Text(Strings.text(R.string.checkin_meeting,point.creator_name))
            }}
            Busy(operation,inline=true)
        }
    },confirmButton={TextButton(enabled=!operation.busy && (people.isNotEmpty()||groups.isNotEmpty()||meeting!=null),onClick={
        if(hasPermission) send(id,type,message,people.toSet(),groups.toSet(),meeting) else permission()
    }) {Text(Strings.text(if(hasPermission) R.string.checkin_send else R.string.checkin_allow_location))}},dismissButton={TextButton(onClick=close,enabled=!operation.busy) {Text(Strings.text(R.string.close))}})
}
@Composable fun CheckinInbox(events: List<AppEvent>,snapshot: Snapshot,open: (AppEvent)->Unit,close: ()->Unit) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.safeDrawingPadding().padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(Strings.text(R.string.checkin_inbox),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onClick=close) {Icon(Icons.Default.Close,Strings.text(R.string.close))}
            }
            Text(Strings.text(R.string.checkin_retention))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(events.isEmpty()) item {Text(Strings.text(R.string.checkin_empty))}
                items(events,key={it.id}) {event ->
                    if(event.kind in setOf("sos","sos_closed")) OutlinedCard(onClick={open(event)},modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {Text(eventReceivedText(event));Text(eventTime(event.created_at))}
                    }
                    event.place()?.let {notice -> OutlinedCard(onClick={open(event)},modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {Text(placeEventText(notice));Text(eventTime(notice.observed_at))}
                    }}
                    event.checkin()?.let {payload ->
                    OutlinedCard(onClick={open(event)},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                        Text("${event.sender_name} · ${checkinLabel(payload.checkin_type)}",style=MaterialTheme.typography.titleMedium)
                        Text(eventTime(payload.recorded_at));Text(precisionLabel(payload.precision_m))
                        if(payload.message.isNotBlank()) Text(payload.message)
                        if(event.sender_id==snapshot.profile?.id) Text(Strings.text(R.string.checkin_recipients,event.recipients.size))
                    }}
                }}
            }
        }}
    }
}
