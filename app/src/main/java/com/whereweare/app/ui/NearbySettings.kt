package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whereweare.app.R
import kotlinx.coroutines.*

@Composable fun NearbySettings(vm: SettingsViewModel) {
    val state by vm.nearby.state.collectAsStateWithLifecycle()
    val snapshot by vm.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    val context=androidx.compose.ui.platform.LocalContext.current
    var busy by remember {mutableStateOf(false)}
    var failure by remember {mutableStateOf(false)}
    var elapsed by remember {mutableLongStateOf(android.os.SystemClock.elapsedRealtime())}
    LaunchedEffect(Unit) {while(true) {elapsed=android.os.SystemClock.elapsedRealtime();delay(1000)}}
    LaunchedEffect(Unit) {vm.nearby.sync()}
    Text(Strings.text(R.string.nearby_optin_explanation))
    Text(Strings.text(R.string.nearby_refresh_policy),style=MaterialTheme.typography.bodySmall)
    if(!snapshot.nearbySosAvailable) Text(Strings.text(R.string.nearby_setup_required))
    Row {
        Text(Strings.text(R.string.nearby_receive),Modifier.weight(1f))
        Switch(state.pending ?: (state.status?.opted_in==true),onCheckedChange={agree -> scope.launch {vm.nearby.consent(agree)}},
            enabled=state.status!=null && (snapshot.nearbySosAvailable || state.status?.opted_in==true))
    }
    if(state.pending!=null) Text(Strings.text(R.string.nearby_pending))
    if(state.failed) Text(Strings.text(R.string.nearby_sync_failed))
    if(state.status==null || state.failed) TextButton(onClick={scope.launch {vm.nearby.consent(false)}}) {
        Text(Strings.text(R.string.nearby_revoke))
    }
    if(state.status?.opted_in==true) {
        Text(Strings.text(R.string.nearby_permanent))
        Text(Strings.text(if(state.availableNow(elapsed)) R.string.nearby_available else R.string.nearby_expired))
        state.status?.available_until?.let {Text(eventTime(it))}
        OutlinedButton(enabled=!busy && state.pending==null,onClick={scope.launch {
            busy=true;failure=false
            try {vm.nearby.refresh(vm.location.snapshot(true))}
            catch(e: CancellationException) {if(e is TimeoutCancellationException) failure=true else throw e}
            catch(_: Exception) {failure=true} finally {busy=false}
        }}) {Text(Strings.text(if(busy) R.string.position_waiting else R.string.nearby_refresh))}
        if(failure) Text(Strings.text(R.string.nearby_refresh_failed))
    }
    Text(Strings.text(R.string.nearby_notifications_hint),style=MaterialTheme.typography.bodySmall)
    val pushConfigured=com.whereweare.app.BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
        com.whereweare.app.BuildConfig.FIREBASE_API_KEY.isNotBlank() && com.whereweare.app.BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
        com.whereweare.app.BuildConfig.FIREBASE_SENDER_ID.isNotBlank()
    if(!pushConfigured) Text(Strings.text(R.string.nearby_push_not_configured))
    else if(!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) Text(Strings.text(R.string.nearby_push_denied))
}
