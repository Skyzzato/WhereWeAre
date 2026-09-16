package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whereweare.app.R
import com.whereweare.app.data.AvatarRepository
import com.whereweare.app.domain.Snapshot
import java.time.Instant

@Composable fun SosRecipients(snapshot: Snapshot,avatars: AvatarRepository,initialPeople: List<String>,initialGroups: List<String>,
    close: ()->Unit,confirm: (List<String>,List<String>)->Unit) {
    var people by rememberSaveable {mutableStateOf(initialPeople)}
    var groups by rememberSaveable {mutableStateOf(initialGroups)}
    var tab by rememberSaveable {mutableIntStateOf(0)}
    var query by rememberSaveable {mutableStateOf("")}
    var now by remember {mutableStateOf(Instant.now())}
    LaunchedEffect(Unit) {while(true) {now=Instant.now();kotlinx.coroutines.delay(1000)}}
    val contacts=snapshot.contacts.values.filter {it.id!=snapshot.profile?.id}.sortedBy {it.name.lowercase()}
    val validPeople=people.filter {id -> contacts.any {it.id==id}}
    val validGroups=groups.filter {id -> snapshot.groups.any {it.id==id && it.active(now) && snapshot.members.any {member -> member.groupId==it.id && member.userId!=snapshot.profile?.id}}}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.safeDrawingPadding().imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(Strings.text(R.string.recipients_title),style=MaterialTheme.typography.titleLarge)
            Text(Strings.text(R.string.recipients_summary,validPeople.size,validGroups.size))
            TabRow(tab) {
                Tab(tab==0,{tab=0},text={Text(Strings.text(R.string.people))})
                Tab(tab==1,{tab=1},text={Text(Strings.text(R.string.ui_119))})
            }
            OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text(Strings.text(R.string.search))},leadingIcon={Icon(Icons.Default.Search,null)})
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                if(tab==0) {
                    val matches=contacts.filter {it.name.contains(query,true)}
                    if(matches.isEmpty()) item {Text(Strings.text(R.string.recipients_empty))}
                    items(matches,key={it.id}) {person ->
                        val selected=person.id in people
                        Surface(color=if(selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().toggleable(selected,role=Role.Checkbox,onValueChange={people=if(it) people+person.id else people-person.id}).padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Avatar(person.id,person.name,person.avatarPath,person.commonGroup,avatars,40.dp)
                                Text(person.name,Modifier.weight(1f));Checkbox(selected,null)
                            }
                        }
                    }
                } else {
                    val matches=snapshot.groups.filter {it.name.contains(query,true)}
                    if(matches.isEmpty()) item {Text(Strings.text(R.string.recipients_empty))}
                    items(matches,key={it.id}) {group ->
                        val enabled=group.active(now) && snapshot.members.any {it.groupId==group.id && it.userId!=snapshot.profile?.id}
                        val selected=group.id in validGroups
                        Surface(color=if(selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().toggleable(selected,enabled=enabled,role=Role.Checkbox,onValueChange={groups=if(it) groups+group.id else groups-group.id}).padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {GroupIdentity(group.emoji,group.name)
                                    if(!group.active(now)) Text(Strings.text(R.string.group_expired))
                                    else if(!enabled) Text(Strings.text(R.string.recipients_empty))
                                }
                                Checkbox(selected,null,enabled=enabled)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=close) {Text(Strings.text(R.string.ui_006))}
                Button(onClick={confirm(validPeople,validGroups)},enabled=validPeople.isNotEmpty()||validGroups.isNotEmpty()) {Text(Strings.text(R.string.recipients_confirm))}
            }
        }}
    }
}
