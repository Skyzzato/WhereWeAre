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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whereweare.app.domain.validGroupName
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun GroupsScreen(vm: GroupsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
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
    val group=state.groups.find { it.id==selectedGroup }
    val members=state.members.filter { it.groupId==selectedGroup }.map { it.userId }
    val date=remember { DateTimeFormatter.ofPattern("'Creato il' dd/MM/yyyy 'alle' HH:mm").withZone(ZoneId.systemDefault()) }
    LaunchedEffect(members) { selected=selected.intersect(members.toSet()) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Busy(operation); if(state.offline) Text("Dati remoti non aggiornati") }
        if(group==null) {
            item { Row { Button(onClick={creating=true}) {Text("Crea gruppo")}; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick={joining=true}) {Text("Entra con codice")} } }
            if(state.groups.isEmpty()) item { Text("Nessun gruppo. Crea un gruppo o entra con un codice invito.") }
            items(state.groups,key={it.id}) { g -> ElevatedCard(onClick={selectedGroup=g.id; selected=emptySet(); selecting=false},modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("${g.emoji} ${g.name}",style=MaterialTheme.typography.titleLarge)
                Text("${state.members.count { it.groupId==g.id }} membri")
                Text(date.format(g.createdAt),style=MaterialTheme.typography.bodySmall)
            } } }
        } else {
            item {
                TextButton(onClick={selectedGroup=null}) { Text("Tutti i gruppi") }
                Text("${group.emoji} ${group.name}",style=MaterialTheme.typography.headlineSmall)
                Text(date.format(group.createdAt))
                Text(group.code.chunked(4).joinToString("-"),style=MaterialTheme.typography.titleMedium)
                Row { TextButton(onClick={context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Codice gruppo",group.code))}) {Text("Copia codice")}
                    TextButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"${group.emoji} ${group.name} · WhereWeAre: ${group.code}"),null))}) {Text("Condividi codice")} }
                Text("Entrare nel gruppo consente ai membri di vedere le posizioni condivise. Il master ON/OFF resta sovrano.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={selecting=!selecting;selected=emptySet()}) {Text(if(selecting) "Fine" else "Seleziona membri")}
                if(selecting) TextButton(onClick={selected=if(selected.isEmpty()) members.filter { it!=vm.userId }.toSet() else emptySet()}) {Text(if(selected.isEmpty()) "Seleziona tutti" else "Deseleziona tutti")}
            }
            if(selected.isNotEmpty()) item { SelectionActions(selected.size,{vm.hide(selected,false)},{vm.hide(selected,true)},remove=if(group.creator==vm.userId) { {removing=selected} } else null,removeLabel="Rimuovi dal gruppo") }
            items(members,key={it}) { id -> val p=state.contacts[id]
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    if(selecting && id!=vm.userId) Checkbox(id in selected,{selected=if(it) selected+id else selected-id})
                    Avatar(id,p?.name.orEmpty(),p?.avatarPath,id!=vm.userId,vm.avatars)
                    Text(if(id==vm.userId) "Tu" else p?.name.orEmpty(),Modifier.weight(1f).padding(start=10.dp))
                    if(id!=vm.userId) IconButton(onClick={vm.hide(setOf(id),id !in hidden)}) {Icon(if(id in hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,"Mostra/nascondi sulla mia mappa")}
                    if(group.creator==vm.userId && id!=vm.userId) IconButton(onClick={removing=setOf(id)}) {Icon(Icons.Default.Delete,"Rimuovi membro")}
                }
            }
            item { HorizontalDivider(); TextButton(onClick={if(group.creator==vm.userId) deletion=true else removing=setOf(requireNotNull(vm.userId))}) {Text(if(group.creator==vm.userId) "Elimina gruppo" else "Esci dal gruppo",color=MaterialTheme.colorScheme.error)} }
        }
    }
    if(creating) AlertDialog(onDismissRequest={creating=false},title={Text("Crea gruppo")},text={Column {
        OutlinedTextField(name,{name=it},label={Text("Nome · massimo 24 caratteri")},isError=name.isNotEmpty() && !validGroupName(name))
        val emojis=listOf("📍","👨‍👩‍👧‍👦","🏠","❤️","👋","😊","🏔️","🥾","⛰️","🌲","🏕️","🔥","🚴","🚵","🏃","⚽","🏀","🎾","🏊","⛷️","🏂","🚗","🏍️","🚐","⛵","✈️","🚆","🌍","🏖️","🏝️","🎒","🏫","🎓","💼","🛠️","💻","🎉","🎂","🎵","🍕")
        Column { emojis.chunked(8).forEach { row -> Row { row.forEach { e -> TextButton(onClick={emoji=e},contentPadding=PaddingValues(1.dp),modifier=Modifier.weight(1f)) {Text(e)} } } } }
        Text("Icona selezionata: $emoji"); Busy(operation)
    }},confirmButton={TextButton(enabled=validGroupName(name)&&!operation.busy,onClick={vm.create(name,emoji);creating=false}) {Text("Crea")}},dismissButton={TextButton(onClick={creating=false}) {Text("Annulla")}})
    if(joining) AlertDialog(onDismissRequest={joining=false},title={Text("Entra con codice")},text={Column {
        OutlinedTextField(code,{code=it},label={Text("Codice gruppo")},trailingIcon={IconButton(onClick={code=context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()}) {Icon(Icons.Default.ContentPaste,"Incolla codice")}})
        Text("Conferma per entrare nel gruppo e condividere con i suoi membri quando la condivisione è ON."); Busy(operation)
    }},confirmButton={TextButton(onClick={vm.join(code);joining=false},enabled=code.isNotBlank()&&!operation.busy){Text("Entra")}},dismissButton={TextButton(onClick={joining=false}){Text("Annulla")}})
    if(deletion && group!=null) ConfirmDestructive("Elimina gruppo","Eliminare definitivamente ${group.name} per tutti i membri?",{deletion=false}) {vm.delete(group.id);deletion=false}
    removing?.let { ids -> if(group!=null) ConfirmDestructive("Rimuovi appartenenza","Confermi l'uscita o rimozione dei membri selezionati? I collegamenti diretti restano indipendenti.",{removing=null}) {vm.remove(group.id,ids);removing=null} }
}
