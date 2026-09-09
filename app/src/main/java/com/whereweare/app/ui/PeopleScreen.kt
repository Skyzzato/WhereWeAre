package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whereweare.app.R

@Composable fun PeopleScreen(vm: PeopleViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val id=vm.userId
    val connections=state.shares.flatMap { listOf(it.owner,it.viewer) }.distinct().filter { it!=id }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Busy(operation)
            if(state.offline) Notice(R.string.offline)
            if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick={ adding=true; vm.clearLookup() },modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_person)) }
        }
        item { Text(stringResource(R.string.connected_people),style=MaterialTheme.typography.headlineSmall) }
        if(connections.isEmpty()) item { Text(stringResource(R.string.empty_people)) }
        items(connections,key={ it }) { person ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(state.names[person].orEmpty(),style=MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.can_see_me),Modifier.weight(1f))
                        val permissionLabel=stringResource(R.string.can_see_me)+": "+state.names[person].orEmpty()
                        Switch(checked=state.shares.any { it.owner==id && it.viewer==person && it.enabled },
                            onCheckedChange={ vm.permission(person,it) },enabled=!operation.busy,
                            modifier=Modifier.semantics { contentDescription=permissionLabel })
                    }
                    val canSee=state.shares.any { it.owner==person && it.viewer==id && it.enabled }
                    Text(stringResource(R.string.can_see_them,stringResource(if(canSee) R.string.yes else R.string.no)))
                }
            }
        }
        listOf(true,false).forEach { received ->
            item { Text(stringResource(if(received) R.string.received else R.string.sent),style=MaterialTheme.typography.headlineSmall) }
            val requests=state.requests.filter { if(received) it.receiver==id else it.sender==id }
            if(requests.isEmpty()) item { Text(stringResource(R.string.no_requests)) }
            items(requests,key={ "${received}-${it.id}" }) { request ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(state.names[if(received) request.sender else request.receiver].orEmpty(),style=MaterialTheme.typography.titleMedium)
                        Text(stringResource(when(request.status) { "accepted" -> R.string.accepted; "rejected" -> R.string.rejected; "cancelled" -> R.string.cancelled; else -> R.string.pending }))
                        if(request.status=="pending") Row {
                            if(received) {
                                TextButton(onClick={ vm.respond(request.id,true) },enabled=!operation.busy) { Text(stringResource(R.string.accept)) }
                                TextButton(onClick={ vm.respond(request.id,false) },enabled=!operation.busy) { Text(stringResource(R.string.reject)) }
                            } else TextButton(onClick={ vm.cancel(request.id) },enabled=!operation.busy) { Text(stringResource(R.string.cancel_request)) }
                        }
                    }
                }
            }
        }
    }
    if(adding) AlertDialog(onDismissRequest={ if(!operation.busy) adding=false },title={ Text(stringResource(R.string.add_person)) },text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(code,{ code=it; vm.clearLookup() },label={ Text(stringResource(R.string.invite_code)) },singleLine=true,enabled=!operation.busy)
            found?.let { Text(it.displayName,style=MaterialTheme.typography.titleLarge) }
            Busy(operation)
        }
    },confirmButton={ TextButton(onClick={ if(found==null) vm.lookup(code) else vm.send() },enabled=!operation.busy) {
        Text(stringResource(if(found==null) R.string.search else R.string.send_request))
    } },dismissButton={ TextButton(onClick={ adding=false },enabled=!operation.busy) { Text(stringResource(R.string.close)) } })
}
