package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whereweare.app.R
import com.whereweare.app.domain.*
import java.util.UUID

fun placePreset(slot: Int)=Strings.text(when(slot) {0 -> R.string.place_home;1 -> R.string.place_work;2 -> R.string.place_custom_one;else -> R.string.place_custom_two})
fun placeRuleLabel(kind: String)=Strings.text(when(kind) {"enter" -> R.string.place_enter;"exit" -> R.string.place_exit;else -> R.string.place_arrival})
fun placeEventText(event: PlaceEvent)=Strings.text(if(event.transition=="enter") R.string.place_entered else R.string.place_exited,event.subject_name,event.place_name)

@Composable fun PlacesScreen(vm: SettingsViewModel,close: ()->Unit) {
    val bundle by vm.places.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    var editing by rememberSaveable {mutableStateOf<Int?>(null)}
    var rulePlace by rememberSaveable {mutableStateOf<String?>(null)}
    var deleting by remember {mutableStateOf<SavedPlace?>(null)}
    LaunchedEffect(vm.userId) {vm.loadPlaces()}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row {Text(Strings.text(R.string.places_title),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick=close) {Text(Strings.text(R.string.close))}}
            Text(Strings.text(R.string.places_monitoring))
            Text(Strings.text(R.string.arriving_disabled),style=MaterialTheme.typography.bodySmall)
            Busy(operation,inline=true)
            repeat(4) {slot ->
                val place=bundle.places.firstOrNull {it.slot==slot}
                OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                    Text(place?.name?:placePreset(slot),style=MaterialTheme.typography.titleMedium)
                    place?.let {Text("${it.latitude}, ${it.longitude}");Text(Strings.text(R.string.place_radius_value,it.radius_m.toString()))}
                    Row {
                        TextButton(enabled=!operation.busy,onClick={vm.message(null);editing=slot}) {Text(Strings.text(R.string.ui_026))}
                        if(place!=null) {
                            TextButton(enabled=!operation.busy,onClick={vm.message(null);rulePlace=place.id}) {Text(Strings.text(R.string.place_add_rule))}
                            TextButton(enabled=!operation.busy,onClick={deleting=place}) {Text(Strings.text(R.string.place_delete))}
                        }
                    }
                    bundle.rules.filter {it.place_id==place?.id}.forEach {rule ->
                        Text(placeRuleLabel(rule.kind)+(if(rule.kind=="arrival") " · "+state.names[rule.subject_id].orEmpty() else ""))
                        Row {
                            Switch(checked=rule.enabled,enabled=!operation.busy,onCheckedChange={vm.saveRule(rule.copy(enabled=it))})
                            Text(Strings.text(if(rule.enabled) R.string.place_armed else R.string.place_paused),Modifier.weight(1f).padding(8.dp))
                            TextButton(enabled=!operation.busy,onClick={vm.removeRule(rule.id)}) {Text(Strings.text(R.string.place_delete))}
                        }
                    }
                }}
            }
        }}
    }
    editing?.let {slot ->
        PlaceEditor(slot,bundle.places.firstOrNull {it.slot==slot},state.locations.firstOrNull {it.userId==vm.userId},operation,{editing=null}) {vm.savePlace(it) {editing=null}}
    }
    bundle.places.firstOrNull {it.id==rulePlace}?.let {place ->
        PlaceRuleEditor(place,state,operation,{rulePlace=null}) {vm.saveRule(it) {rulePlace=null}}
    }
    deleting?.let {place -> ConfirmDestructive(Strings.text(R.string.place_delete),Strings.text(R.string.place_delete_confirm),{deleting=null}) {vm.removePlace(place.id);deleting=null}}
}

@Composable private fun PlaceEditor(slot: Int,place: SavedPlace?,fix: UserLocation?,operation: OperationState,close: ()->Unit,save: (SavedPlace)->Unit) {
    val id=rememberSaveable {place?.id?:UUID.randomUUID().toString()}
    var name by rememberSaveable {mutableStateOf(place?.name?:placePreset(slot))}
    var lat by rememberSaveable {mutableStateOf(place?.latitude?.toString().orEmpty())}
    var lon by rememberSaveable {mutableStateOf(place?.longitude?.toString().orEmpty())}
    var radius by rememberSaveable {mutableStateOf((place?.radius_m?:100).toString())}
    AlertDialog(onDismissRequest={if(!operation.busy) close()},title={Text(placePreset(slot))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(name,{name=it},enabled=!operation.busy,label={Text(Strings.text(R.string.name))})
        OutlinedTextField(lat,{lat=it},enabled=!operation.busy,label={Text(Strings.text(R.string.place_latitude))})
        OutlinedTextField(lon,{lon=it},enabled=!operation.busy,label={Text(Strings.text(R.string.place_longitude))})
        OutlinedTextField(radius,{radius=it},enabled=!operation.busy,label={Text(Strings.text(R.string.place_radius))})
        TextButton(enabled=fix!=null&&!operation.busy,onClick={lat=fix?.latitude.toString();lon=fix?.longitude.toString()}) {Text(Strings.text(R.string.place_use_last))}
        fix?.let {Text(eventTime(it.recordedAt.toString()),style=MaterialTheme.typography.bodySmall)}
        Busy(operation,inline=true)
    }},confirmButton={TextButton(enabled=!operation.busy&&validPlace(name,lat,lon,radius),onClick={save(SavedPlace(id,slot,name.trim(),lat.toDouble(),lon.toDouble(),radius.toInt()))}) {Text(Strings.text(R.string.save))}},dismissButton={TextButton(enabled=!operation.busy,onClick=close) {Text(Strings.text(R.string.close))}})
}

@Composable private fun PlaceRuleEditor(place: SavedPlace,snapshot: Snapshot,operation: OperationState,close: ()->Unit,save: (PlaceRule)->Unit) {
    val id=rememberSaveable {UUID.randomUUID().toString()}
    var kind by rememberSaveable {mutableStateOf("enter")}
    var subject by rememberSaveable {mutableStateOf<String?>(null)}
    var recipients by remember {mutableStateOf(emptySet<String>())}
    val owner=snapshot.profile?.id
    AlertDialog(onDismissRequest={if(!operation.busy) close()},title={Text(Strings.text(R.string.place_add_rule))},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
        Text(place.name);Text(Strings.text(R.string.places_monitoring))
        listOf("enter","exit","arrival").forEach {value -> Row {
            RadioButton(kind==value,{kind=value},enabled=!operation.busy);Text(placeRuleLabel(value))
        }}
        snapshot.contacts.values.filter {it.id!=owner && (kind!="arrival" || it.canView)}.forEach {person -> Row {
            if(kind=="arrival") RadioButton(subject==person.id,{subject=person.id},enabled=!operation.busy)
            else Checkbox(person.id in recipients,{recipients=if(it) recipients+person.id else recipients-person.id},enabled=!operation.busy)
            Text(person.name)
        }}
        Busy(operation,inline=true)
    }},confirmButton={TextButton(enabled=!operation.busy&&owner!=null&&(if(kind=="arrival") subject!=null else recipients.isNotEmpty()),onClick={
        save(PlaceRule(id,place.id,if(kind=="arrival") requireNotNull(subject) else requireNotNull(owner),kind,true,if(kind=="arrival") listOf(requireNotNull(owner)) else recipients.toList()))
    }) {Text(Strings.text(R.string.place_arm))}},dismissButton={TextButton(enabled=!operation.busy,onClick=close) {Text(Strings.text(R.string.close))}})
}
