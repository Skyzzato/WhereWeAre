package com.whereweare.app.ui

import com.whereweare.app.R

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whereweare.app.domain.validGroupName
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun GroupsScreen(vm: GroupsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val hiddenGroups by vm.hiddenGroups.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📍") }
    var code by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var selecting by remember { mutableStateOf(false) }
    var deletion by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Set<String>?>(null) }
    var renaming by remember {mutableStateOf(false)}
    var newName by remember {mutableStateOf("")}
    var inviting by remember {mutableStateOf(false)}
    val group=state.groups.find { it.id==selectedGroup }
    val members=state.members.filter { it.groupId==selectedGroup }.map { it.userId }
    val date=DateTimeFormatter.ofPattern(Strings.text(R.string.ui_013)).withZone(ZoneId.systemDefault())
    LaunchedEffect(members) { selected=selected.intersect(members.toSet()) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Busy(operation); if(state.offline) Text(Strings.text(R.string.ui_008)) }
        items(state.groupRequests.filter {it.status=="pending"},key={"request-${it.id}"}) {request -> OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
            Text(request.group_name,style=MaterialTheme.typography.titleMedium);Text(request.name)
            Text(if(request.kind=="invite") Strings.text(R.string.ui_014) else Strings.text(R.string.ui_015))
            if(request.can_respond) Row {TextButton(onClick={vm.respond(request.id,true)},enabled=!operation.busy) {Text(Strings.text(R.string.accept))};TextButton(onClick={vm.respond(request.id,false)},enabled=!operation.busy) {Text(Strings.text(R.string.reject))}}
            else Text(Strings.text(R.string.ui_016))
        }} }
        if(group==null) {
            item { Row { Button(onClick={creating=true}) {Text(Strings.text(R.string.ui_017))}; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick={joining=true}) {Text(Strings.text(R.string.ui_018))} } }
            if(state.groups.isEmpty()) item { Text(Strings.text(R.string.ui_019)) }
            items(state.groups,key={it.id}) { g -> ElevatedCard(onClick={selectedGroup=g.id; selected=emptySet(); selecting=false},modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {Text(g.emoji,fontSize=31.sp);Text(g.name,Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleLarge)
                    IconButton(onClick={vm.hideGroup(g.id,g.id !in hiddenGroups)}) {Icon(if(g.id in hiddenGroups) Icons.Default.VisibilityOff else Icons.Default.Visibility,Strings.text(R.string.ui_020))}}
                Text(Strings.text(R.string.ui_021, (state.members.count { it.groupId==g.id }).toString()))
                Text(date.format(g.createdAt),style=MaterialTheme.typography.bodySmall)
            } } }
        } else {
            item {
                TextButton(onClick={selectedGroup=null}) { Text(Strings.text(R.string.ui_022)) }
                Text("${group.emoji} ${group.name}",style=MaterialTheme.typography.headlineSmall)
                Text(date.format(group.createdAt))
                Text(Strings.text(R.string.ui_023),style=MaterialTheme.typography.titleSmall)
                Text(com.whereweare.app.domain.normalizeInviteCode(group.code),style=MaterialTheme.typography.titleMedium)
                Row { TextButton(onClick={context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(Strings.text(R.string.ui_023),group.code))}) {Text(Strings.text(R.string.copy_code))}
                    TextButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,Strings.text(R.string.ui_024, (group.emoji).toString(), (group.name).toString(), (group.code).toString())),null))}) {Text(Strings.text(R.string.share_code))} }
                Text(Strings.text(R.string.ui_025),style=MaterialTheme.typography.bodySmall)
                val enabled=state.members.any {it.groupId==group.id && it.userId==vm.userId && it.sharingEnabled}
                TextButton(onClick={vm.groupSharing(group.id,!enabled)},enabled=!operation.busy) {Text(if(enabled) Strings.text(R.string.ui_009) else Strings.text(R.string.can_see_me))}
                if(group.creator==vm.userId) Row {TextButton(onClick={newName=group.name;renaming=true}) {Text(Strings.text(R.string.ui_026))};TextButton(onClick={inviting=true}) {Text(Strings.text(R.string.ui_011))}}
                TextButton(onClick={selecting=!selecting;selected=emptySet()}) {Text(if(selecting) Strings.text(R.string.ui_010) else Strings.text(R.string.ui_027))}
                if(selecting) TextButton(onClick={selected=if(selected.isEmpty()) members.filter { it!=vm.userId }.toSet() else emptySet()}) {Text(if(selected.isEmpty()) Strings.text(R.string.ui_028) else Strings.text(R.string.ui_029))}
            }
            if(selected.isNotEmpty()) item { SelectionActions(selected.size,{vm.hide(selected,false)},{vm.hide(selected,true)},remove=if(group.creator==vm.userId) { {removing=selected} } else null,removeLabel=Strings.text(R.string.ui_030)) }
            items(members,key={it}) { id -> val p=state.contacts[id]
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    if(selecting && id!=vm.userId) Checkbox(id in selected,{selected=if(it) selected+id else selected-id})
                    Avatar(id,if(id==vm.userId) state.profile?.displayName.orEmpty() else p?.name.orEmpty(),if(id==vm.userId) state.profile?.avatarPath else p?.avatarPath,id!=vm.userId,vm.avatars)
                    Text(if(id==vm.userId) Strings.text(R.string.you) else p?.name.orEmpty(),Modifier.weight(1f).padding(start=10.dp))
                    if(id!=vm.userId) IconButton(onClick={vm.hide(setOf(id),id !in hidden)}) {Icon(if(id in hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,Strings.text(R.string.ui_020))}
                    if(group.creator==vm.userId && id!=vm.userId) IconButton(onClick={removing=setOf(id)}) {Icon(Icons.Default.Delete,Strings.text(R.string.ui_031))}
                }
            }
            item { HorizontalDivider(); TextButton(onClick={if(group.creator==vm.userId) deletion=true else removing=setOf(requireNotNull(vm.userId))}) {Text(if(group.creator==vm.userId) Strings.text(R.string.ui_032) else Strings.text(R.string.ui_033),color=MaterialTheme.colorScheme.error)} }
        }
    }
    if(creating) AlertDialog(onDismissRequest={creating=false},title={Text(Strings.text(R.string.ui_017))},text={Column {
        OutlinedTextField(name,{name=it},label={Text(Strings.text(R.string.ui_034))},isError=name.isNotEmpty() && !validGroupName(name))
        val emojis=listOf("📍","👨‍👩‍👧‍👦","🏠","❤️","👋","😊","🏔️","🥾","⛰️","🌲","🏕️","🔥","🚴","🚵","🏃","⚽","🏀","🎾","🏊","⛷️","🏂","🚗","🏍️","🚐","⛵","✈️","🚆","🌍","🏖️","🏝️","🎒","🏫","🎓","💼","🛠️","💻","🎉","🎂","🎵","🍕")
        Column { emojis.chunked(8).forEach { row -> Row { row.forEach { e -> TextButton(onClick={emoji=e},contentPadding=PaddingValues(1.dp),modifier=Modifier.weight(1f)) {Text(e)} } } } }
        Text(Strings.text(R.string.ui_035, (emoji).toString())); Busy(operation)
    }},confirmButton={TextButton(enabled=validGroupName(name)&&!operation.busy,onClick={vm.create(name,emoji);creating=false}) {Text(Strings.text(R.string.ui_036))}},dismissButton={TextButton(onClick={creating=false}) {Text(Strings.text(R.string.ui_006))}})
    if(joining) AlertDialog(onDismissRequest={joining=false},title={Text(Strings.text(R.string.ui_018))},text={Column {
        OutlinedTextField(code,{code=it},label={Text(Strings.text(R.string.ui_023))},trailingIcon={IconButton(onClick={code=context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()}) {Icon(Icons.Default.ContentPaste,Strings.text(R.string.ui_012))}})
        Text(Strings.text(R.string.ui_037)); Busy(operation)
    }},confirmButton={TextButton(onClick={vm.join(code);joining=false},enabled=code.isNotBlank()&&!operation.busy){Text(Strings.text(R.string.ui_038))}},dismissButton={TextButton(onClick={joining=false}){Text(Strings.text(R.string.ui_006))}})
    if(deletion && group!=null) ConfirmDestructive(Strings.text(R.string.ui_032),Strings.text(R.string.ui_039, (group.name).toString()),{deletion=false}) {vm.delete(group.id);deletion=false}
    removing?.let { ids -> if(group!=null) ConfirmDestructive(Strings.text(R.string.ui_040),Strings.text(R.string.ui_041),{removing=null}) {vm.remove(group.id,ids);removing=null} }
    if(renaming && group!=null) AlertDialog(onDismissRequest={renaming=false},title={Text(Strings.text(R.string.ui_042))},text={OutlinedTextField(newName,{newName=it})},confirmButton={TextButton(onClick={vm.rename(group.id,newName);renaming=false},enabled=validGroupName(newName)) {Text(Strings.text(R.string.ui_043))}},dismissButton={TextButton(onClick={renaming=false}) {Text(Strings.text(R.string.ui_006))}})
    if(inviting && group!=null) AlertDialog(onDismissRequest={inviting=false},title={Text(Strings.text(R.string.ui_011))},text={Column {state.contacts.values.filter {it.id !in members}.forEach {p -> TextButton(onClick={vm.invite(group.id,p.id);inviting=false}) {Text(p.name)}}}},confirmButton={TextButton(onClick={inviting=false}) {Text(Strings.text(R.string.ui_006))}})
}
