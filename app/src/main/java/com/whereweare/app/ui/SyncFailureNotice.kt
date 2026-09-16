package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whereweare.app.R
import com.whereweare.app.domain.Snapshot

fun syncFailureMessage(snapshot: Snapshot): Int = if(snapshot.syncInProgress) R.string.sync_waiting else when(snapshot.syncError) {
    "session" -> R.string.error_auth
    "forbidden" -> R.string.error_forbidden
    "response" -> R.string.connection_response
    "service" -> R.string.connection_service
    else -> R.string.connection_unreachable
}
@Composable fun SyncFailureNotice(snapshot: Snapshot,retry: ()->Unit) {
    Surface(color=MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(Strings.text(syncFailureMessage(snapshot)))
            TextButton(onClick=retry,enabled=!snapshot.syncInProgress) {Text(Strings.text(R.string.ui_124))}
        }
    }
}

fun trackingFailureMessage(tracking: com.whereweare.app.service.TrackingState): Int =
    syncFailureMessage(Snapshot(syncInProgress=tracking.retrying,syncError=tracking.failure))
