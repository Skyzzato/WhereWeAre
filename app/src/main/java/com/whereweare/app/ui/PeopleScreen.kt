package com.whereweare.app.ui

import android.content.ClipboardManager
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
import com.whereweare.app.domain.normalizeInviteCode

@Composable fun PeopleScreen(vm: PeopleViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var remove by remember { mutableStateOf<Set<String>?>(null) }
    val id=vm.userId
    val connections=state.shares.flatMap { listOf(it.owner,it.viewer) }.distinct().filter { it!=id }
    LaunchedEffect(connections) { selected=selected.intersect(connections.toSet()) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Busy(operation); if(state.offline) Text("Dati remoti non aggiornati"); Button(onClick={ adding=true; vm.clearLookup() }) { Text("Aggiungi persona") } }
        item { Row(verticalAlignment=Alignment.CenterVertically) { Text("Persone collegate",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge); TextButton(onClick={ selecting=!selecting; selected=emptySet() }) { Text(if(selecting) "Fine" else "Seleziona") } } }
        if(selecting) item { TextButton(onClick={ selected=if(selected.size==connections.size) emptySet() else connections.toSet() }) { Text(if(selected.size==connections.size) "Deseleziona tutte" else "Seleziona tutte") } }
        if(selected.isNotEmpty()) item { SelectionActions(selected.size,
            show={ vm.hide(selected,false) },hide={ vm.hide(selected,true) },allow={ vm.permissions(selected,true) },deny={ vm.permissions(selected,false) },remove={ remove=selected }) }
        if(connections.isEmpty()) item { Text("Ancora nessuna persona collegata.") }
        items(connections,key={it}) { person ->
            val p=state.contacts[person]; val name=state.names[person].orEmpty()
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    if(selecting) Checkbox(person in selected,{ selected=if(it) selected+person else selected-person })
                    Avatar(person,name,p?.avatarPath,p?.commonGroup==true,vm.avatars)
                    Text(name,Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.titleMedium)
                    IconButton(onClick={ vm.hide(setOf(person),person !in hidden) },enabled=!operation.busy) { Icon(if(person in hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,if(person in hidden) "Mostra sulla mappa" else "Nascondi dalla mappa") }
                    IconButton(onClick={ remove=setOf(person) },enabled=!operation.busy) { Icon(Icons.Default.Delete,"Rimuovi collegamento") }
                }
                val allowed=state.shares.any { it.owner==id && it.viewer==person && it.enabled }
                TextButton(onClick={ vm.permission(person,!allowed) },enabled=!operation.busy) { Text(if(allowed) "Non consentire di vedermi" else "Consenti di vedermi") }
                if(p?.commonGroup==true) Text("La condivisione tramite gruppo resta indipendente.",style=MaterialTheme.typography.bodySmall)
            } }
        }
        listOf(true,false).forEach { received ->
            item { Text(if(received) "Richieste ricevute" else "Richieste inviate",style=MaterialTheme.typography.titleLarge) }
            val requests=state.requests.filter { if(received) it.receiver==id else it.sender==id }
            if(requests.isEmpty()) item { Text("Nessuna richiesta") }
            items(requests,key={"$received-${it.id}"}) { r -> OutlinedCard { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(state.names[if(received) r.sender else r.receiver].orEmpty()); Text(when(r.status) { "accepted"->"Accettata"; "rejected"->"Rifiutata"; "cancelled"->"Annullata"; else->"In attesa" }) }
                if(received && r.status=="pending") TextButton(onClick={ vm.respond(r.id,true) },enabled=!operation.busy) { Text("Accetta") }
                IconButton(onClick={ vm.dismiss(r.id) },enabled=!operation.busy) { Icon(Icons.Default.Delete,if(r.status!="pending") "Nascondi dalla cronologia" else if(received) "Rifiuta richiesta" else "Annulla richiesta") }
            } } }
        }
    }
    if(adding) AlertDialog(onDismissRequest={ adding=false },title={ Text("Aggiungi persona") },text={ Column {
        OutlinedTextField(code,{ code=it; vm.clearLookup() },label={Text("Codice personale")},singleLine=true,trailingIcon={ IconButton(onClick={
            code=normalizeInviteCode(context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()); vm.clearLookup()
        }) { Icon(Icons.Default.ContentPaste,"Incolla codice") } })
        found?.let { Text(it.displayName) }; Busy(operation)
    } },confirmButton={ TextButton(onClick={ if(found==null) vm.lookup(code) else vm.send() },enabled=!operation.busy) { Text(if(found==null) "Cerca" else "Invia richiesta") } },dismissButton={ TextButton(onClick={adding=false}) { Text("Chiudi") } })
    remove?.let { ids -> ConfirmDestructive("Rimuovi collegamento","Rimuovere ${ids.size} collegamenti diretti? I gruppi in comune rimangono indipendenti.",{remove=null}) { vm.remove(ids); remove=null; selected=emptySet() } }
}
@Composable fun ConfirmDestructive(title: String,text: String,dismiss: ()->Unit,confirm: ()->Unit) {
    AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Text(text)},confirmButton={TextButton(onClick=confirm){Text("Conferma",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick=dismiss){Text("Annulla")}})
}
@Composable fun SelectionActions(count: Int,show: ()->Unit,hide: ()->Unit,allow: (()->Unit)?=null,deny: (()->Unit)?=null,remove: (()->Unit)?=null,removeLabel: String="Rimuovi collegamento") {
    var menu by remember { mutableStateOf(false) }
    Surface(color=MaterialTheme.colorScheme.secondaryContainer) { Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("$count selezionati",Modifier.weight(1f)); Box { TextButton(onClick={menu=true}) { Text("Azioni") }
            DropdownMenu(menu,{menu=false}) {
                listOf("Mostra sulla mappa" to show,"Nascondi dalla mappa" to hide,"Consenti di vedermi" to allow,"Non consentire di vedermi" to deny,removeLabel to remove).forEach { (label,action) ->
                    if(action!=null) DropdownMenuItem(text={Text(label)},onClick={menu=false;action()})
                }
            }
        }
    } }
}
