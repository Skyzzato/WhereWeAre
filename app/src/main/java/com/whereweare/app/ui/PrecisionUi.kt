package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.whereweare.app.R
import com.whereweare.app.domain.AudienceMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun precisionLabel(value: Int?)=when(value) {
    null -> Strings.text(R.string.precision_default)
    0 -> Strings.text(R.string.precision_exact)
    else -> Strings.text(R.string.precision_approximate,value)
}
@Composable fun PrecisionChoice(value: Int?,override: Boolean,enabled: Boolean,onChoose: (Int?)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Column {
        Text(Strings.text(if(override) R.string.precision_shared else R.string.precision_default_title),style=MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {Text(precisionLabel(value))}
            DropdownMenu(expanded,{expanded=false}) {
                (if(override) listOf(null,0,250,500,1000) else listOf(0,250,500,1000)).forEach {option ->
                    DropdownMenuItem(text={Text(precisionLabel(option))},onClick={expanded=false;onChoose(option)})
                }
            }
        }
    }
}
@Composable fun AudienceScreen(vm: SettingsViewModel,close: ()->Unit) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var members by remember {mutableStateOf<List<AudienceMember>?>(null)}
    var checkedAt by remember {mutableStateOf<Instant?>(null)}
    var failed by remember {mutableStateOf(false)}
    LaunchedEffect(lifecycle,vm) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while(true) {
                    try {members=vm.audience();checkedAt=Instant.now();failed=false}
                    catch(e: CancellationException) {throw e}
                    catch(_: Exception) {members=null;checkedAt=null;failed=true}
                    delay(15_000)
                }
            } finally {members=null;checkedAt=null}
        }
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(Strings.text(R.string.precision_audience),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    IconButton(onClick=close) {Icon(Icons.Default.Close,Strings.text(R.string.close))}
                }
                Text(Strings.text(R.string.precision_audience_hint))
                checkedAt?.let {Text(Strings.text(R.string.precision_checked,DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(it)),style=MaterialTheme.typography.bodySmall)}
                if(failed) Text(Strings.text(R.string.sync_waiting),color=MaterialTheme.colorScheme.error)
                else if(members==null) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
                    if(members?.isEmpty()==true) item {Text(Strings.text(R.string.precision_no_audience))}
                    items(members.orEmpty(),key={it.user_id}) {person ->
                        OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                            Text(person.name,style=MaterialTheme.typography.titleMedium)
                            Text(precisionLabel(person.precision_m))
                            person.sources.forEach {source ->
                                Text(Strings.text(if(source.kind=="group") R.string.precision_via_group else R.string.precision_via_person,source.name,precisionLabel(source.precision_m)),style=MaterialTheme.typography.bodySmall)
                            }
                        }}
                    }
                }
            }
        }
    }
}
