package com.whereweare.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whereweare.app.R

@Composable fun SettingsScreen(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val high by vm.highAccuracy.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val uri=LocalUriHandler.current
    var name by remember(state.profile?.displayName) { mutableStateOf(state.profile?.displayName.orEmpty()) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.settings),style=MaterialTheme.typography.headlineLarge)
        Busy(operation)
        OutlinedTextField(name,{ name=it },label={ Text(stringResource(R.string.name)) },singleLine=true,modifier=Modifier.fillMaxWidth())
        TextButton(onClick={ vm.rename(name) },enabled=!operation.busy) { Text(stringResource(R.string.save)) }
        Text(vm.email,style=MaterialTheme.typography.bodyLarge)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.your_code))
                val code=state.profile?.inviteCode.orEmpty()
                val codeLabel=stringResource(R.string.invite_code)
                val codeMessage=stringResource(R.string.code_message,code)
                Text(code,style=MaterialTheme.typography.headlineMedium)
                Row {
                    TextButton(onClick={ context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(codeLabel,code)) },enabled=code.isNotEmpty()) { Text(stringResource(R.string.copy_code)) }
                    TextButton(onClick={ context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,codeMessage),null)) },enabled=code.isNotEmpty()) { Text(stringResource(R.string.share_code)) }
                }
            }
        }
        Text(stringResource(R.string.precision_mode),style=MaterialTheme.typography.titleMedium)
        Row {
            FilterChip(selected=!high,onClick={ vm.accuracy(false) },label={ Text(stringResource(R.string.balanced)) })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected=high,onClick={ vm.accuracy(true) },label={ Text(stringResource(R.string.high_accuracy)) })
        }
        HorizontalDivider()
        Text(stringResource(R.string.privacy),style=MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.privacy_text))
        Text(stringResource(R.string.about),style=MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.about_text))
        TextButton(onClick={ uri.openUri("https://www.openstreetmap.org/copyright") }) { Text(stringResource(R.string.osm)) }
        OutlinedButton(onClick={ vm.logout() },enabled=!operation.busy,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.logout)) }
    }
}
