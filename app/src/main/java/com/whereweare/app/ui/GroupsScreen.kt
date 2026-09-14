package com.whereweare.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
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
import com.whereweare.app.R
import com.whereweare.app.domain.normalizeInviteCode
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun GroupsScreen(vm: GroupsViewModel,initialCode: String?=null,inviteId: String?=null,inviteHandled: ()->Unit={}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val hiddenGroups by vm.hiddenGroups.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var selectedGroup by rememberSaveable {mutableStateOf<String?>(null)}
    var creating by rememberSaveable {mutableStateOf(false)}
    var editing by rememberSaveable {mutableStateOf(false)}
    var joining by rememberSaveable(inviteId) {mutableStateOf(initialCode!=null)}
    var code by rememberSaveable(inviteId) {mutableStateOf(initialCode.orEmpty())}
    var searching by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    LaunchedEffect(inviteId) {if(inviteId!=null) vm.message(null)}
    var selecting by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf(emptySet<String>())}
    var inviting by remember {mutableStateOf(false)}
    var invitees by remember {mutableStateOf(emptySet<String>())}
    var deletion by remember {mutableStateOf(false)}
    var removing by remember {mutableStateOf<Set<String>?>(null)}
    val group=state.groups.find {it.id==selectedGroup}
    val members=state.members.filter {it.groupId==selectedGroup}.map {it.userId}
    val invitations=state.groupRequests.filter {it.group_id==selectedGroup && it.kind=="invite" && it.status=="pending" && it.user_id !in members}
    val date=DateTimeFormatter.ofPattern(Strings.text(R.string.ui_013)).withZone(ZoneId.systemDefault())
    LaunchedEffect(members) {selected=selected.intersect(members.toSet())}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {if(!joining) Busy(operation);if(state.offline) Notice(R.string.sync_waiting) else if(state.realtimeUnavailable) Notice(R.string.realtime_unavailable)}
        if(group==null) {
            item {SearchHeader(Strings.text(R.string.ui_119),query,searching,{searching=it},{query=it})}
            items(state.groupRequests.filter {it.status=="pending" && (it.kind=="join" || it.user_id==vm.userId)},key={"request-${it.id}"}) {request ->
                OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                    GroupIdentity(request.group_emoji,request.group_name);Text(request.name)
                    Text(Strings.text(if(request.kind=="invite") R.string.invitation_received else R.string.ui_015))
                    if(request.can_respond) Row {
                        TextButton(onClick={vm.respond(request.id,true)},enabled=!operation.busy) {Text(Strings.text(R.string.accept))}
                        TextButton(onClick={vm.respond(request.id,false)},enabled=!operation.busy) {Text(Strings.text(R.string.reject))}
                    } else if(request.kind=="join") Text(Strings.text(R.string.ui_016))
                }}
            }
            item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={creating=true}) {Text(Strings.text(R.string.ui_017))}
                OutlinedButton(onClick={vm.message(null);joining=true}) {Text(Strings.text(R.string.ui_018))}
            }}
            if(state.groups.isEmpty()) item {Text(Strings.text(R.string.ui_019))}
            items(state.groups.filter {it.name.contains(query,ignoreCase=true)},key={it.id}) {g -> ElevatedCard(onClick={selectedGroup=g.id;selecting=false;selected=emptySet()},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        GroupIdentity(g.emoji,g.name,Modifier.weight(1f))
                        IconButton(onClick={vm.hideGroup(g.id,g.id !in hiddenGroups)}) {Icon(if(g.id in hiddenGroups) Icons.Default.VisibilityOff else Icons.Default.Visibility,Strings.text(R.string.ui_020))}
                    }
                    Text(Strings.text(R.string.ui_021,state.members.count {it.groupId==g.id}.toString()))
                    Text(date.format(g.createdAt),style=MaterialTheme.typography.bodySmall)
                }
            }}
        } else {
            item {
                TextButton(onClick={selectedGroup=null}) {Text(Strings.text(R.string.ui_022))}
                Row(verticalAlignment=Alignment.Top) {
                    Column(Modifier.weight(1f)) {GroupIdentity(group.emoji,group.name);Text(Strings.text(R.string.created_at,date.format(group.createdAt)),style=MaterialTheme.typography.bodySmall)}
                    if(group.creator==vm.userId) TextButton(onClick={editing=true},enabled=!operation.busy) {Text(Strings.text(R.string.ui_026))}
                }
                val enabled=state.members.any {it.groupId==group.id && it.userId==vm.userId && it.sharingEnabled}
                TextButton(onClick={vm.groupSharing(group.id,!enabled)},enabled=!operation.busy) {Text(Strings.text(if(enabled) R.string.ui_009 else R.string.can_see_me))}
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(Strings.text(R.string.ui_023),style=MaterialTheme.typography.titleSmall)
                        Text(normalizeInviteCode(group.code),style=MaterialTheme.typography.headlineSmall)
                        Row {
                            TextButton(onClick={vm.message(if(copyInviteCode(context,group.code)) R.string.code_copied else R.string.error_generic)}) {Text(Strings.text(R.string.ui_080))}
                            TextButton(onClick={if(!shareInviteText(context,Strings.text(if(inviteLink("group",group.code).startsWith("https://")) R.string.ui_024 else R.string.share_group_code,group.emoji,group.name,inviteLink("group",group.code)))) vm.message(R.string.error_generic)}) {Text(Strings.text(R.string.ui_081))}
                        }
                    }
                }
            }
            item {Row(verticalAlignment=Alignment.CenterVertically) {
                Text(Strings.text(R.string.members),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                if(group.creator==vm.userId) TextButton(onClick={invitees=emptySet();inviting=true},enabled=!operation.busy) {Text(Strings.text(R.string.ui_027))}
                IconButton(onClick={selecting=!selecting;selected=emptySet()}) {Icon(Icons.Default.Checklist,Strings.text(R.string.member_actions))}
            }
                if(selecting) TextButton(onClick={selected=if(selected.isEmpty()) members.filter {it!=vm.userId}.toSet() else emptySet()}) {Text(Strings.text(if(selected.isEmpty()) R.string.ui_028 else R.string.ui_029))}
            }
            if(selected.isNotEmpty()) item {SelectionActions(selected.size,{vm.hide(selected,false)},{vm.hide(selected,true)},
                remove=if(group.creator==vm.userId) {{removing=selected}} else null,removeLabel=Strings.text(R.string.ui_030))}
            items(members,key={"member-$it"}) {id ->
                val p=state.contacts[id];val own=id==vm.userId
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    if(selecting && !own) Checkbox(id in selected,{selected=if(it) selected+id else selected-id})
                    Avatar(id,if(own) state.profile?.displayName.orEmpty() else p?.name.orEmpty(),if(own) state.profile?.avatarPath else p?.avatarPath,!own,vm.avatars)
                    Text(if(own) Strings.text(R.string.you) else p?.name.orEmpty(),Modifier.weight(1f).padding(start=10.dp))
                    if(!own) IconButton(onClick={vm.hide(setOf(id),id !in hidden)}) {Icon(if(id in hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,Strings.text(R.string.ui_020))}
                    if(!own && id !in state.savedPeople && state.shares.none {it.owner==id || it.viewer==id}) IconButton(onClick={vm.savePerson(id)},enabled=!operation.busy) {Icon(Icons.Default.PersonAdd,Strings.text(R.string.save_group_person))}
                    if(group.creator==vm.userId && !own) IconButton(onClick={removing=setOf(id)},enabled=!operation.busy) {Icon(Icons.Default.Delete,Strings.text(R.string.ui_031))}
                }
            }
            items(invitations,key={"invitation-${it.id}"}) {request ->
                var menu by remember {mutableStateOf(false)}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Avatar(request.user_id,request.name,state.contacts[request.user_id]?.avatarPath,false,vm.avatars)
                    Text(request.name,Modifier.weight(1f).padding(horizontal=10.dp))
                    Text(Strings.text(R.string.invitation_sent),style=MaterialTheme.typography.labelMedium,modifier=Modifier.widthIn(max=110.dp))
                    if(request.can_cancel) Box {
                        IconButton(onClick={menu=true},enabled=!operation.busy) {Icon(Icons.Default.MoreVert,Strings.text(R.string.ui_077))}
                        DropdownMenu(menu,{menu=false}) {DropdownMenuItem(text={Text(Strings.text(R.string.cancel_invitation))},onClick={menu=false;vm.cancelInvitation(request.id)})}
                    }
                }
            }
            item {HorizontalDivider();TextButton(onClick={if(group.creator==vm.userId) deletion=true else removing=setOf(requireNotNull(vm.userId))}) {
                Text(Strings.text(if(group.creator==vm.userId) R.string.ui_032 else R.string.ui_033),color=MaterialTheme.colorScheme.error)
            }}
        }
    }
    if(creating) GroupEditor(false,busy=operation.busy,dismiss={creating=false}) {name,emoji -> vm.create(name,emoji);creating=false}
    if(editing && group!=null) GroupEditor(true,group.name,group.emoji,operation.busy,{editing=false}) {name,emoji -> vm.edit(group.id,name,emoji);editing=false}
    if(joining) AlertDialog(onDismissRequest={joining=false;vm.message(null);inviteHandled()},title={Text(Strings.text(R.string.ui_018))},text={Column {
        OutlinedTextField(code,{code=it;vm.message(null)},label={Text(Strings.text(R.string.ui_023))},trailingIcon={IconButton(onClick={code=context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()}) {Icon(Icons.Default.ContentPaste,Strings.text(R.string.ui_012))}})
        Text(Strings.text(R.string.ui_037));Busy(operation,inline=true)
    }},confirmButton={TextButton(onClick={vm.join(code) {joining=false;vm.message(null);inviteHandled()}},enabled=code.isNotBlank()&&!operation.busy) {Text(Strings.text(R.string.ui_038))}},dismissButton={TextButton(onClick={joining=false;vm.message(null);inviteHandled()}) {Text(Strings.text(R.string.ui_006))}})
    if(inviting && group!=null) {
        val candidates=state.contacts.values.filter {it.id !in members && invitations.none {r -> r.user_id==it.id}}.sortedBy {it.name}
        AlertDialog(onDismissRequest={inviting=false},title={Text(Strings.text(R.string.ui_027))},text={
            LazyColumn(Modifier.heightIn(max=360.dp)) {
                if(candidates.isEmpty()) item {Text(Strings.text(R.string.no_members_to_invite))}
                items(candidates,key={it.id}) {person -> Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(person.id in invitees,{invitees=if(it) invitees+person.id else invitees-person.id});Text(person.name)
                }}
            }
        },confirmButton={TextButton(enabled=invitees.isNotEmpty()&&!operation.busy,onClick={vm.inviteMany(group.id,invitees);inviting=false}) {Text(Strings.text(R.string.send_request))}},dismissButton={TextButton(onClick={inviting=false}) {Text(Strings.text(R.string.ui_006))}})
    }
    if(deletion && group!=null) ConfirmDestructive(Strings.text(R.string.ui_032),Strings.text(R.string.ui_039,group.name),{deletion=false}) {vm.delete(group.id);deletion=false}
    removing?.let {ids -> if(group!=null) ConfirmDestructive(Strings.text(R.string.ui_040),Strings.text(R.string.ui_041),{removing=null}) {vm.remove(group.id,ids);removing=null}}
}
