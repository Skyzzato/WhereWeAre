package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@Composable fun PlacesScreen(vm: SettingsViewModel,onCenter: (SavedPlace)->Unit={},close: ()->Unit) {
    val bundle by vm.places.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val hidden by vm.hiddenPlaces.collectAsStateWithLifecycle()
    val mapStyle by vm.mapStyle.collectAsStateWithLifecycle()
    var editing by rememberSaveable {mutableStateOf<Int?>(null)}
    var editingRule by remember {mutableStateOf<PlaceRule?>(null)}
    var rulePlace by rememberSaveable {mutableStateOf<String?>(null)}
    var deleting by remember {mutableStateOf<SavedPlace?>(null)}
    LaunchedEffect(vm.userId) {vm.loadPlaces()}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row {Text(Strings.text(R.string.places_title),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick=close) {Text(Strings.text(R.string.close))}}
            Text(Strings.text(R.string.places_monitoring))
            Text(Strings.text(R.string.arriving_disabled),style=MaterialTheme.typography.bodySmall)
            Busy(operation,inline=true)
            Button(enabled=!operation.busy,onClick={vm.message(null);editing=(bundle.places.maxOfOrNull {it.slot}?:-1)+1}) {Text(Strings.text(R.string.place_add))}
            Text(Strings.text(R.string.place_rule_limit),style=MaterialTheme.typography.bodySmall)
            bundle.places.sortedBy {it.slot}.forEach {place ->
                val slot=place.slot
                OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {Text(place.emoji);Text(place.name,style=MaterialTheme.typography.titleMedium)}
                        Column(horizontalAlignment=androidx.compose.ui.Alignment.End) {
                            TextButton(enabled=!operation.busy,onClick={vm.hidePlace(place.id,false) {onCenter(place)}}) {Text(Strings.text(R.string.place_center))}
                            IconButton(enabled=!operation.busy,onClick={vm.hidePlace(place.id,place.id !in hidden)}) {
                                Icon(if(place.id in hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,Strings.text(if(place.id in hidden) R.string.place_show else R.string.place_hide))
                            }
                        }
                    }
                    place.let {Text("${it.latitude}, ${it.longitude}");Text(Strings.text(R.string.place_radius_value,it.radius_m.toString()))}
                    Row {
                        TextButton(enabled=!operation.busy,onClick={vm.message(null);editing=slot}) {Text(Strings.text(R.string.place_edit))}
                        run {
                            TextButton(enabled=!operation.busy,onClick={vm.message(null);editingRule=null;rulePlace=place.id}) {Text(Strings.text(R.string.place_add_rule))}
                            TextButton(enabled=!operation.busy,onClick={deleting=place}) {Text(Strings.text(R.string.place_delete))}
                        }
                    }
                    bundle.rules.filter {it.place_id==place.id}.forEach {rule ->
                        Text(placeRuleLabel(rule.kind)+(if(rule.kind=="arrival") " · "+state.names[rule.subject_id].orEmpty() else ""))
                        Row {
                            TextButton(enabled=!operation.busy,onClick={editingRule=rule;rulePlace=place.id}) {Text(Strings.text(R.string.ui_026))}
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
        PlaceEditor(bundle.places,slot,bundle.places.firstOrNull {it.slot==slot},state.locations.firstOrNull {it.userId==vm.userId},operation,mapStyle,{editing=null}) {vm.savePlace(it) {editing=null}}
    }
    bundle.places.firstOrNull {it.id==rulePlace}?.let {place ->
        PlaceRuleEditor(place,editingRule,state,operation,{rulePlace=null}) {vm.saveRule(it) {rulePlace=null}}
    }
    deleting?.let {place -> ConfirmDestructive(Strings.text(R.string.place_delete),place.name+"\n"+Strings.text(R.string.place_delete_confirm),{deleting=null}) {vm.removePlace(place.id);deleting=null}}
}

@Composable internal fun PlaceEditor(places: List<SavedPlace>,slot: Int,place: SavedPlace?,fix: UserLocation?,operation: OperationState,mapStyle: String,close: ()->Unit,save: (SavedPlace)->Unit) {
    var picking by rememberSaveable {mutableStateOf(false)}
    var radiusInfo by rememberSaveable {mutableStateOf(false)}
    val id=rememberSaveable {place?.id?:UUID.randomUUID().toString()}
    var name by rememberSaveable {mutableStateOf(place?.name?:"")}
    var emoji by rememberSaveable {mutableStateOf(place?.emoji?:"📍")}
    val duplicate=duplicatePlaceName(name,id,places)
    var lat by rememberSaveable {mutableStateOf(place?.latitude?.toString().orEmpty())}
    var lon by rememberSaveable {mutableStateOf(place?.longitude?.toString().orEmpty())}
    var radius by rememberSaveable {mutableStateOf((place?.radius_m?:100).toString())}
    AlertDialog(onDismissRequest={if(!operation.busy) close()},title={Text(Strings.text(if(place==null) R.string.place_add else R.string.place_edit))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(name,{name=it},enabled=!operation.busy,label={Text(Strings.text(R.string.name))})
        if(duplicate) Text(Strings.text(R.string.place_duplicate),color=MaterialTheme.colorScheme.error)
        Row {Text(emoji);Text(name)}
        TextButton(enabled=!operation.busy,onClick={emoji=""}) {Text(Strings.text(R.string.group_remove_icon))}
        EmojiPicker(emoji,operation.busy) {emoji=it}
        OutlinedTextField(lat,{lat=it},enabled=!operation.busy,label={Text(Strings.text(R.string.place_latitude))})
        OutlinedTextField(lon,{lon=it},enabled=!operation.busy,label={Text(Strings.text(R.string.place_longitude))})
        TextButton(enabled=!operation.busy,onClick={picking=true}) {Text(Strings.text(R.string.place_choose_map))}
        OutlinedTextField(radius,{radius=it},enabled=!operation.busy,label={Text(Strings.text(R.string.place_radius))},suffix={Text("m")},trailingIcon={IconButton(onClick={radiusInfo=true}) {Icon(Icons.Default.Info,Strings.text(R.string.place_radius_info_title))}})
        TextButton(enabled=fix!=null&&!operation.busy,onClick={lat=fix?.latitude.toString();lon=fix?.longitude.toString()}) {Text(Strings.text(R.string.place_use_last))}
        fix?.let {Text(eventTime(it.recordedAt.toString()),style=MaterialTheme.typography.bodySmall)}
        Busy(operation,inline=true)
    }},confirmButton={TextButton(enabled=!operation.busy&&!duplicate&&validPlace(name,lat,lon,radius),onClick={save(SavedPlace(id,slot,name.trim(),lat.toDouble(),lon.toDouble(),radius.toInt(),emoji))}) {Text(Strings.text(R.string.save))}},dismissButton={TextButton(enabled=!operation.busy,onClick=close) {Text(Strings.text(R.string.close))}})
    if(picking) PlaceLocationPicker(lat.toDoubleOrNull(),lon.toDoubleOrNull(),fix,mapStyle,{picking=false}) {latitude,longitude ->
        lat=latitude.toString();lon=longitude.toString();picking=false
    }
    if(radiusInfo) AlertDialog(onDismissRequest={radiusInfo=false},title={Text(Strings.text(R.string.place_radius_info_title))},text={Text(Strings.text(R.string.place_radius_info))},confirmButton={TextButton(onClick={radiusInfo=false}) {Text(Strings.text(R.string.close))}})
}

@Composable private fun PlaceRuleEditor(place: SavedPlace,rule: PlaceRule?,snapshot: Snapshot,operation: OperationState,close: ()->Unit,save: (PlaceRule)->Unit) {
    val id=rememberSaveable {rule?.id?:UUID.randomUUID().toString()}
    var kind by rememberSaveable {mutableStateOf(rule?.kind?:"enter")}
    var subject by rememberSaveable {mutableStateOf(rule?.subject_id)}
    var recipients by remember {mutableStateOf(rule?.recipients?.toSet()?:emptySet<String>())}
    val owner=snapshot.profile?.id
    AlertDialog(onDismissRequest={if(!operation.busy) close()},title={Text(Strings.text(if(rule==null) R.string.place_add_rule else R.string.ui_026))},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
        Text(Strings.text(R.string.places_monitoring))
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
        save(PlaceRule(id,place.id,if(kind=="arrival") requireNotNull(subject) else requireNotNull(owner),kind,rule?.enabled?:true,if(kind=="arrival") listOf(requireNotNull(owner)) else recipients.toList()))
    }) {Text(Strings.text(if(rule==null) R.string.place_arm else R.string.save))}},dismissButton={TextButton(enabled=!operation.busy,onClick=close) {Text(Strings.text(R.string.close))}})
}
