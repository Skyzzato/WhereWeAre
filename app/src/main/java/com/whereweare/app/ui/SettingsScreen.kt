package com.whereweare.app.ui

import com.whereweare.app.R

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

fun durationLabel(seconds: Int)=when { seconds<60 -> Strings.text(R.string.ui_114, (seconds).toString()); seconds==60 -> Strings.text(R.string.ui_115); seconds<3600 -> Strings.text(R.string.ui_116, (seconds/60).toString()); seconds==3600 -> Strings.text(R.string.ui_117); else -> Strings.text(R.string.ui_118, (seconds/3600).toString()) }
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
    val theme by vm.theme.collectAsStateWithLifecycle()
    val scale by vm.avatarScale.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var name by remember(state.profile?.displayName) {mutableStateOf(state.profile?.displayName.orEmpty())}
    var precise by remember {mutableStateOf(vm.location.permission()==LocationPermission.PRECISE)}
    var delete by remember {mutableStateOf(false)}
    LifecycleResumeEffect(Unit) {precise=vm.location.permission()==LocationPermission.PRECISE;onPauseOrDispose {}}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Busy(operation)
        Text(Strings.text(R.string.ui_078),style=MaterialTheme.typography.titleLarge)
        state.profile?.let { Avatar(it.id,it.displayName,it.avatarPath,false,vm.avatars,64.dp) }
        vm.userId?.let {user -> AvatarEditor(user,vm.avatarDrafts,operation.busy,state.profile?.avatarPath!=null,
            onSave=vm::avatar,onRemove=vm::removeAvatar,onError={vm.message(R.string.error_generic)},error=operation.message) }
        OutlinedTextField(name,{name=it},label={Text(Strings.text(R.string.name))},singleLine=true,modifier=Modifier.fillMaxWidth())
        TextButton(onClick={vm.rename(name)},enabled=!operation.busy){Text(Strings.text(R.string.save))}
        val code=state.profile?.inviteCode.orEmpty()
        Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Column(Modifier.padding(16.dp)) {
                Text(Strings.text(R.string.invite_code),style=MaterialTheme.typography.titleSmall)
                Text(code,style=MaterialTheme.typography.headlineSmall)
                Row {
                    TextButton(enabled=code.isNotBlank(),onClick={
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(Strings.text(R.string.invite_code),code))
                        android.widget.Toast.makeText(context,com.whereweare.app.R.string.code_copied,android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text(Strings.text(R.string.ui_080)) }
                    TextButton(enabled=code.isNotBlank(),onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT,Strings.text(R.string.ui_082, (code).toString())),Strings.text(R.string.ui_083)))}) { Text(Strings.text(R.string.ui_081)) }
                }
            }
        }
        HorizontalDivider(); Text(Strings.text(R.string.ui_084),style=MaterialTheme.typography.titleLarge)
        Row { FilterChip(!high || !precise,{vm.accuracy(false)},label={Text(Strings.text(R.string.balanced))}); Spacer(Modifier.width(8.dp)); FilterChip(high && precise,{vm.accuracy(true)},enabled=precise,label={Text(Strings.text(R.string.high_accuracy))}) }
        if(!precise) { Text(Strings.text(R.string.ui_085),color=MaterialTheme.colorScheme.error)
            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri()))}){Text(Strings.text(R.string.ui_086))} }
        Choice(Strings.text(R.string.ui_087),interval,updateIntervals,::durationLabel,vm::interval)
        Choice(Strings.text(R.string.ui_088),threshold,accuracyThresholds,{"$it m"},vm::threshold)
        Choice(Strings.text(R.string.ui_089),state.profile?.visibilitySeconds ?: 86400,visibilityTimeouts,::durationLabel,vm::visibility)
        HorizontalDivider(); Text(Strings.text(R.string.map),style=MaterialTheme.typography.titleLarge)
        Choice(Strings.text(R.string.ui_090),MapStyle.fromId(style).id,MapStyle.entries.map { it.id },{MapStyle.fromId(it).label},vm::mapStyle)
        HorizontalDivider();Text(Strings.text(R.string.ui_091),style=MaterialTheme.typography.titleLarge)
        Choice(Strings.text(R.string.ui_092),theme,listOf("default","ocean","sunset","lavender","graphite","dark"),{it.replaceFirstChar(Char::uppercase)},vm::theme)
        Text(Strings.text(R.string.ui_093),style=MaterialTheme.typography.titleSmall)
        val scales=listOf(.75f,1f,1.25f,1.5f)
        val sizes=listOf(Strings.text(R.string.ui_094),Strings.text(R.string.ui_095),Strings.text(R.string.ui_096),Strings.text(R.string.ui_097))
        var sizeIndex by remember(scale) {mutableFloatStateOf(scales.indexOf(scale).coerceAtLeast(0).toFloat())}
        Slider(sizeIndex,{sizeIndex=it},valueRange=0f..3f,steps=2,onValueChangeFinished={vm.avatarScale(scales[kotlin.math.round(sizeIndex).toInt()])})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {sizes.forEach {Text(it,style=MaterialTheme.typography.labelSmall)}}
        Choice(Strings.text(R.string.ui_098),language,listOf("system","it","en"),{when(it) {"it"->Strings.text(R.string.ui_099);"en"->Strings.text(R.string.ui_100);else->Strings.text(R.string.ui_101)}},vm::language)
        HorizontalDivider(); Text(Strings.text(R.string.ui_102),style=MaterialTheme.typography.titleLarge)
        Text(vm.registeredSince?.let { Strings.text(R.string.ui_103, (it).toString()) } ?: Strings.text(R.string.ui_104))
        OutlinedButton(onClick=vm::logout,enabled=!operation.busy,modifier=Modifier.fillMaxWidth()){Text(Strings.text(R.string.ui_105))}
        TextButton(onClick={delete=true},enabled=!operation.busy){Text(Strings.text(R.string.ui_106),color=MaterialTheme.colorScheme.error)}
        HorizontalDivider()
        TextButton(onClick=onPrivacy){Text(Strings.text(R.string.ui_107))}
    }
    if(delete) ConfirmDestructive(Strings.text(R.string.ui_106),Strings.text(R.string.ui_109),{delete=false}) {delete=false;vm.deleteAccount()}
}
@Composable fun PrivacyScreen(back: ()->Unit) {
    val uri=LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick=back){Text(Strings.text(R.string.settings))}
        Text(Strings.text(R.string.ui_108),style=MaterialTheme.typography.headlineSmall)
        Text(Strings.text(R.string.ui_110))
        Text(Strings.text(R.string.ui_111))
        Text(Strings.text(R.string.ui_112))
        Text("WhereWeAre v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · © WhereWeAre")
        TextButton(onClick={uri.openUri("https://www.openstreetmap.org/copyright")}){Text("© OpenStreetMap contributors")}
        TextButton(onClick={uri.openUri("https://openfreemap.org/")}){Text("OpenFreeMap · OpenMapTiles")}
        TextButton(onClick={uri.openUri("https://maplibre.org/")}){Text("MapLibre Compose / Native")}
        TextButton(onClick={uri.openUri("https://opentopomap.org/about")}){Text("OpenTopoMap · SRTM · CC-BY-SA")}
        TextButton(onClick={uri.openUri("https://www.cyclosm.org/")}){Text("CyclOSM · OpenStreetMap France")}
        Text(Strings.text(R.string.ui_113))
    }
}
