package com.whereweare.app.ui

import com.whereweare.app.R

import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whereweare.app.domain.normalizeInviteCode

@Composable fun PeopleScreen(vm: PeopleViewModel,onShow: (String)->Unit={},initialCode: String?=null,inviteId: String?=null,inviteHandled: ()->Unit={},onScan: ()->Unit={}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var adding by rememberSaveable(inviteId) { mutableStateOf(initialCode!=null) }
    var code by rememberSaveable(inviteId) { mutableStateOf(initialCode.orEmpty()) }
    var searching by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    LaunchedEffect(inviteId) {if(initialCode!=null) {vm.clearLookup();vm.lookup(initialCode)}}
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var remove by remember { mutableStateOf<Set<String>?>(null) }
    var unavailable by remember {mutableStateOf<String?>(null)}
    val unavailableHost=remember {SnackbarHostState()}
    LaunchedEffect(unavailable) {unavailable?.let {unavailableHost.showSnackbar(it);unavailable=null}}
    var invitePerson by remember {mutableStateOf<String?>(null)}
    val id=vm.userId
    val connections=(state.shares.flatMap { listOf(it.owner,it.viewer) }+state.savedPeople).distinct().filter { it!=id }
    LaunchedEffect(connections) { selected=selected.intersect(connections.toSet()) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {if(!adding) Busy(operation); if(!online) Notice(R.string.connection_absent) else if(state.syncFailed) Notice(R.string.sync_waiting) else if(state.realtimeUnavailable) Notice(R.string.realtime_unavailable)
            SearchHeader(Strings.text(R.string.people),query,searching,{searching=it},{query=it})
            Button(onClick={ adding=true; vm.clearLookup() }) { Text(Strings.text(R.string.qr_enter_code)) }
            OutlinedButton(onClick=onScan) {Text(Strings.text(R.string.qr_scan))} }
        item { Row(verticalAlignment=Alignment.CenterVertically) { Text(Strings.text(R.string.connected_people),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge); TextButton(onClick={ selecting=!selecting; selected=emptySet() }) { Text(if(selecting) Strings.text(R.string.ui_010) else Strings.text(R.string.ui_063)) } } }
        if(selecting) item { TextButton(onClick={ selected=if(selected.size==connections.size) emptySet() else connections.toSet() }) { Text(if(selected.size==connections.size) Strings.text(R.string.ui_064) else Strings.text(R.string.ui_065)) } }
        if(selected.isNotEmpty()) item { SelectionActions(selected.size,
            show={ vm.hide(selected,false) },hide={ vm.hide(selected,true) },allow={ vm.permissions(selected,true) },deny={ vm.permissions(selected,false) },remove={ remove=selected }) }
        if(connections.isEmpty()) item { Text(Strings.text(R.string.ui_062)) }
        items(connections.filter {(state.contacts[it]?.name ?: state.names[it].orEmpty()).contains(query,ignoreCase=true)},key={it}) { person ->
            val p=state.contacts[person]; val name=p?.name ?: state.names[person].orEmpty()
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal=12.dp,vertical=4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    if(selecting) Checkbox(person in selected,{ selected=if(it) selected+person else selected-person })
                    Avatar(person,name,p?.avatarPath,p?.commonGroup==true,vm.avatars)
                    Text(name,Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.titleMedium)
                    IconButton(onClick={ vm.hide(setOf(person),person !in hidden) },enabled=!operation.busy) { Icon(if(person in hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,if(person in hidden) Strings.text(R.string.ui_066) else Strings.text(R.string.ui_067)) }
                    if(state.groups.any {it.creator==id && state.members.none {m -> m.groupId==it.id && m.userId==person}}) IconButton(onClick={invitePerson=person}) {Icon(Icons.Default.GroupAdd,Strings.text(R.string.ui_011))}
                    IconButton(onClick={ remove=setOf(person) },enabled=!operation.busy) { Icon(Icons.Default.Delete,Strings.text(R.string.ui_068)) }
                }
                val allowed=state.shares.any { it.owner==id && it.viewer==person && it.enabled }
                if(state.shares.any {it.owner==id && it.viewer==person}) TextButton(onClick={ vm.permission(person,!allowed) },enabled=!operation.busy) { Text(if(allowed) Strings.text(R.string.ui_009) else Strings.text(R.string.can_see_me)) }
                else TextButton(onClick={vm.requestPerson(person)},enabled=!operation.busy) {Text(Strings.text(R.string.send_request))}
                TextButton(onClick={
                    val fix=state.locations.firstOrNull {it.userId==person}
                    if(p?.canView==false) unavailable=Strings.text(R.string.ui_070, (name).toString())
                    else if(fix==null || !com.whereweare.app.domain.withinVisibility(fix.recordedAt,java.time.Instant.now(),p?.visibilitySeconds ?: 600) || state.statuses.none {it.userId==person && it.sharing}) unavailable=Strings.text(R.string.ui_071, (name).toString())
                    else {vm.reveal(person);onShow(person)}
                }) {Text(Strings.text(R.string.center_map))}
                if(p?.commonGroup==true) Text(Strings.text(R.string.ui_069),style=MaterialTheme.typography.bodySmall)
            } }
        }
        listOf(true,false).forEach { received ->
            item { Text(if(received) Strings.text(R.string.received) else Strings.text(R.string.sent),style=MaterialTheme.typography.titleLarge) }
            val requests=state.requests.filter { if(received) it.receiver==id else it.sender==id }
            if(requests.isEmpty()) item { Text(Strings.text(R.string.no_requests)) }
            items(requests,key={"$received-${it.id}"}) { r -> OutlinedCard { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(state.names[if(received) r.sender else r.receiver].orEmpty()); Text(when(r.status) { "accepted"->Strings.text(R.string.accepted); "rejected"->Strings.text(R.string.rejected); "cancelled"->Strings.text(R.string.cancelled); else->Strings.text(R.string.pending) }) }
                if(received && r.status=="pending") TextButton(onClick={ vm.respond(r.id,true) },enabled=!operation.busy) { Text(Strings.text(R.string.accept)) }
                IconButton(onClick={ vm.dismiss(r.id) },enabled=!operation.busy) { Icon(Icons.Default.Delete,if(r.status!="pending") Strings.text(R.string.ui_072) else if(received) Strings.text(R.string.ui_073) else Strings.text(R.string.cancel_request)) }
            } } }
        }
    }
    if(adding) AlertDialog(onDismissRequest={ adding=false;vm.message(null);inviteHandled() },title={ Text(Strings.text(R.string.add_person)) },text={ Column {
        OutlinedTextField(code,{ code=it; vm.clearLookup() },label={Text(Strings.text(R.string.invite_code))},singleLine=true,trailingIcon={ IconButton(onClick={
            code=normalizeInviteCode(context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()); vm.clearLookup()
        }) { Icon(Icons.Default.ContentPaste,Strings.text(R.string.ui_012)) } })
        found?.let { Text(it.displayName) }; Busy(operation,inline=true)
    } },confirmButton={ TextButton(onClick={ if(found==null) vm.lookup(code) else vm.send {adding=false;vm.message(null);inviteHandled()} },enabled=!operation.busy) { Text(if(found==null) Strings.text(R.string.search) else Strings.text(R.string.send_request)) } },dismissButton={ TextButton(onClick={adding=false;vm.message(null);inviteHandled()}) { Text(Strings.text(R.string.close)) } })
    remove?.let { ids -> ConfirmDestructive(Strings.text(R.string.ui_068),Strings.text(R.string.ui_074, (ids.size).toString()),{remove=null}) { vm.remove(ids); remove=null; selected=emptySet() } }
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter) {SnackbarHost(unavailableHost)}
    invitePerson?.let {person -> AlertDialog(onDismissRequest={invitePerson=null},title={Text(Strings.text(R.string.ui_011))},text={Column {state.groups.filter {g -> g.creator==id && state.members.none {it.groupId==g.id && it.userId==person} && state.groupRequests.none {it.group_id==g.id && it.user_id==person && it.status=="pending"}}.forEach {g -> TextButton(onClick={vm.invite(g.id,person);invitePerson=null}) {GroupIdentity(g.emoji,g.name,Modifier.fillMaxWidth())}}}},confirmButton={TextButton(onClick={invitePerson=null}) {Text(Strings.text(R.string.ui_006))}}) }
}
@Composable fun ConfirmDestructive(title: String,text: String,dismiss: ()->Unit,confirm: ()->Unit) {
    AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick=confirm){Text(Strings.text(R.string.ui_075),color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick=dismiss){Text(Strings.text(R.string.ui_006))}})
}
@Composable fun SelectionActions(count: Int,show: ()->Unit,hide: ()->Unit,allow: (()->Unit)?=null,deny: (()->Unit)?=null,remove: (()->Unit)?=null,removeLabel: String=Strings.text(R.string.ui_068)) {
    var menu by remember { mutableStateOf(false) }
    Surface(color=MaterialTheme.colorScheme.secondaryContainer) { Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(Strings.text(R.string.ui_076, (count).toString()),Modifier.weight(1f)); Box { TextButton(onClick={menu=true}) { Text(Strings.text(R.string.ui_077)) }
            DropdownMenu(menu,{menu=false}) {
                listOf(Strings.text(R.string.ui_066) to show,Strings.text(R.string.ui_067) to hide,Strings.text(R.string.can_see_me) to allow,Strings.text(R.string.ui_009) to deny,removeLabel to remove).forEach { (label,action) ->
                    if(action!=null) DropdownMenuItem(text={Text(label)},onClick={menu=false;action()})
                }
            }
        }
    } }
}
