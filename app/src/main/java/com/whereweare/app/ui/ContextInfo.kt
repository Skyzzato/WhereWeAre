package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.whereweare.app.R

@Composable fun InfoLabel(label: String,info: String?=null) {
    var opened by remember {mutableStateOf(false)}
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
        if(info!=null) IconButton(onClick={opened=true}) {Icon(Icons.Outlined.Info,Strings.text(R.string.info_action,label))}
    }
    if(opened && info!=null) AlertDialog(onDismissRequest={opened=false},title={Text(label)},text={Text(info)},
        confirmButton={TextButton(onClick={opened=false}) {Text(Strings.text(R.string.close))}})
}
@Composable fun DetailLine(icon: ImageVector,text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text,Modifier.weight(1f))
    }
}
