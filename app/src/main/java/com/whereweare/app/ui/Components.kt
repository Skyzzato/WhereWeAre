package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whereweare.app.R
import com.whereweare.app.domain.LocationFreshness
import java.time.Duration
import java.time.Instant

@Composable fun Notice(message: Int?) {
    message?.let { Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
        Text(stringResource(it),Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)
    } }
}
@Composable fun Busy(state: OperationState) { if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth()); Notice(state.message) }
@Composable fun freshnessText(freshness: LocationFreshness,at: Instant,now: Instant): String {
    val minutes=Duration.between(at,now).toMinutes().coerceAtLeast(0)
    return when(freshness) {
        LocationFreshness.LIVE -> stringResource(R.string.live)
        LocationFreshness.RECENT -> stringResource(R.string.recent,minutes)
        else -> stringResource(R.string.old,minutes)
    }
}
