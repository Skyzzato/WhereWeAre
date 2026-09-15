package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whereweare.app.R

@Composable fun SettingsSection(title: String,initiallyExpanded: Boolean=false,content: @Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable(title) {mutableStateOf(initiallyExpanded)}
    OutlinedCard(Modifier.fillMaxWidth()) {
        TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,Strings.text(if(expanded) R.string.settings_collapse else R.string.settings_expand))
            }
        }
        if(expanded) Column(Modifier.padding(start=16.dp,end=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
