package com.whereweare.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.whereweare.app.BuildConfig
import com.whereweare.app.domain.*

fun durationLabel(seconds: Int)=when { seconds<60 -> "$seconds secondi"; seconds==60 -> "1 minuto"; seconds<3600 -> "${seconds/60} minuti"; seconds==3600 -> "1 ora"; else -> "${seconds/3600} ore" }
@Composable fun <T> Choice(label: String,value: T,options: List<T>,text: (T)->String,onChoose: (T)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column { Text(label,style=MaterialTheme.typography.titleSmall); Box {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {Text(text(value))}
        DropdownMenu(expanded,{expanded=false}) { options.forEach { option -> DropdownMenuItem(text={Text(text(option))},onClick={expanded=false;onChoose(option)}) } }
    } }
}
@Composable fun SettingsScreen(vm: SettingsViewModel,onPrivacy: ()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val high by vm.highAccuracy.collectAsStateWithLifecycle()
    val interval by vm.interval.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val style by vm.mapStyle.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var name by remember(state.profile?.displayName) {mutableStateOf(state.profile?.displayName.orEmpty())}
    var precise by remember {mutableStateOf(vm.location.permission()==LocationPermission.PRECISE)}
    var delete by remember {mutableStateOf(false)}
    LifecycleResumeEffect(Unit) {precise=vm.location.permission()==LocationPermission.PRECISE;onPauseOrDispose {}}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Busy(operation)
        Text("Profilo",style=MaterialTheme.typography.titleLarge)
        state.profile?.let { Avatar(it.id,it.displayName,it.avatarPath,false,vm.avatars,64.dp) }
        AvatarEditor(onSave=vm::avatar,onError={vm.message(com.whereweare.app.R.string.error_generic)})
        if(state.profile?.avatarPath!=null) TextButton(onClick=vm::removeAvatar,enabled=!operation.busy) { Text("Elimina foto",color=MaterialTheme.colorScheme.error) }
        OutlinedTextField(name,{name=it},label={Text("Nome visualizzato")},singleLine=true,modifier=Modifier.fillMaxWidth())
        TextButton(onClick={vm.rename(name)},enabled=!operation.busy){Text("Salva nome")}
        val code=state.profile?.inviteCode.orEmpty()
        Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(0xFFEAEAEA))) {
            Column(Modifier.padding(16.dp)) {
                Text("Codice personale",style=MaterialTheme.typography.titleSmall)
                Text(code,style=MaterialTheme.typography.headlineSmall)
                Row {
                    TextButton(enabled=code.isNotBlank(),onClick={
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Codice personale",code))
                        android.widget.Toast.makeText(context,com.whereweare.app.R.string.code_copied,android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("Copia") }
                    TextButton(enabled=code.isNotBlank(),onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT,"Aggiungimi su WhereWeAre con il mio codice personale: $code"),"Condividi codice personale"))}) { Text("Condividi") }
                }
            }
        }
        HorizontalDivider(); Text("Localizzazione",style=MaterialTheme.typography.titleLarge)
        Row { FilterChip(!high || !precise,{vm.accuracy(false)},label={Text("Bilanciata")}); Spacer(Modifier.width(8.dp)); FilterChip(high && precise,{vm.accuracy(true)},enabled=precise,label={Text("Alta precisione")}) }
        if(!precise) { Text("Posizione precisa non autorizzata. Alta precisione non disponibile.",color=MaterialTheme.colorScheme.error)
            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri()))}){Text("Apri permessi Android")} }
        Choice("Frequenza aggiornamento",interval,updateIntervals,::durationLabel,vm::interval)
        Choice("Soglia avviso precisione GPS",threshold,accuracyThresholds,{"$it m"},vm::threshold)
        Choice("Mostra la mia ultima posizione per",state.profile?.visibilitySeconds ?: 86400,visibilityTimeouts,::durationLabel,vm::visibility)
        HorizontalDivider(); Text("Mappa",style=MaterialTheme.typography.titleLarge)
        Choice("Tipo di mappa",MapStyle.fromId(style).id,MapStyle.entries.map { it.id },{MapStyle.fromId(it).label},vm::mapStyle)
        HorizontalDivider(); Text("Account",style=MaterialTheme.typography.titleLarge)
        Text(vm.registeredSince?.let { "Registrato dal: $it" } ?: "Data registrazione non disponibile")
        OutlinedButton(onClick=vm::logout,enabled=!operation.busy,modifier=Modifier.fillMaxWidth()){Text("Esci")}
        TextButton(onClick={delete=true},enabled=!operation.busy){Text("Elimina account",color=MaterialTheme.colorScheme.error)}
        HorizontalDivider()
        TextButton(onClick=onPrivacy){Text("Privacy, informazioni e copyright >")}
    }
    if(delete) ConfirmDestructive("Elimina account","L'operazione è definitiva: saranno cancellati account, foto, posizione, relazioni e gruppi creati da te. Confermi?",{delete=false}) {delete=false;vm.deleteAccount()}
}
@Composable fun PrivacyScreen(back: ()->Unit) {
    val uri=LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick=back){Text("Impostazioni")}
        Text("Privacy, informazioni e copyright",style=MaterialTheme.typography.headlineSmall)
        Text("La condivisione parte soltanto quando la attivi. OFF ferma subito l'acquisizione sul dispositivo. Senza rete lo stop remoto rimane in attesa: gli autorizzati possono leggere l'ultimo punto fino alla scadenza scelta dal proprietario, massimo 24 ore.")
        Text("Conserviamo una sola ultima posizione, senza storico. La tua posizione rimane leggibile da te anche dopo la scadenza. Collegamenti diretti e gruppi sono canali indipendenti. L'occhio filtra soltanto la tua mappa. Foto private accessibili al proprietario e ai contatti autorizzati, memorizzate temporaneamente in RAM. I dati già ricevuti non possono essere ritirati dai dispositivi altrui.")
        Text("Il GPS usa la precisione restituita da Android. Frequenze e disponibilità dipendono da permessi, ricezione e risparmio energetico. La cartografia comunica con il provider dello stile; non vengono scaricate regioni offline.")
        Text("WhereWeAre v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · © WhereWeAre")
        TextButton(onClick={uri.openUri("https://www.openstreetmap.org/copyright")}){Text("© OpenStreetMap contributors")}
        TextButton(onClick={uri.openUri("https://openfreemap.org/")}){Text("OpenFreeMap · OpenMapTiles")}
        TextButton(onClick={uri.openUri("https://maplibre.org/")}){Text("MapLibre Compose / Native")}
        TextButton(onClick={uri.openUri("https://opentopomap.org/about")}){Text("OpenTopoMap · SRTM · CC-BY-SA")}
        TextButton(onClick={uri.openUri("https://www.cyclosm.org/")}){Text("CyclOSM · OpenStreetMap France")}
        Text("Gli ulteriori provider conservano le attribuzioni indicate nello stile e nel controllo di attribuzione della mappa.")
    }
}
