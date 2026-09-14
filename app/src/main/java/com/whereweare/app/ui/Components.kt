package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whereweare.app.R
import androidx.compose.material.icons.filled.*
import com.whereweare.app.domain.LocationFreshness
import java.time.Duration
import java.time.Instant

@Composable fun Notice(message: Int?) {
    message?.let { Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
        Text(stringResource(it),Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)
    } }
}
@Composable fun Busy(state: OperationState,inline: Boolean=false) {
    val host=remember {SnackbarHostState()}
    val field=state.message in setOf(R.string.not_found,R.string.group_not_found,R.string.invalid_form,R.string.self_invite,R.string.error_connected,R.string.already_group_member)
    LaunchedEffect(state.message,state.busy) {state.message?.let {if(!inline || !field) host.showSnackbar(Strings.text(it),duration=SnackbarDuration.Short)}}
    if(inline && field) state.message?.let {Text(Strings.text(it),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
    SnackbarHost(host)
}

@Composable fun SearchHeader(title: String,query: String,open: Boolean,onOpen: (Boolean)->Unit,onQuery: (String)->Unit) {
    Column {
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            IconButton(onClick={onOpen(!open);if(open) onQuery("")}) {Icon(if(open) androidx.compose.material.icons.Icons.Default.Close else androidx.compose.material.icons.Icons.Default.Search,Strings.text(if(open) R.string.close else R.string.search))}
        }
        if(open) OutlinedTextField(query,onQuery,Modifier.fillMaxWidth(),singleLine=true,label={Text(Strings.text(R.string.search_name))},trailingIcon={IconButton(onClick={onQuery("")}) {Icon(androidx.compose.material.icons.Icons.Default.Clear,Strings.text(R.string.clear_search))}})
    }
}
@Composable fun freshnessText(freshness: LocationFreshness,at: Instant,now: Instant): String {
    val minutes=Duration.between(at,now).toMinutes().coerceAtLeast(0)
    return when(freshness) {
        LocationFreshness.LIVE -> stringResource(R.string.live)
        LocationFreshness.RECENT -> stringResource(R.string.recent,minutes)
        else -> stringResource(R.string.old,minutes)
    }
}
