package com.whereweare.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.whereweare.app.domain.*

/** Navigation stays available before metadata and after a transient failure. */
@Composable fun SosMapButton(snapshot: Snapshot,events: List<AppEvent>,modifier: Modifier=Modifier,openEditor: ()->Unit,openEvent: (String)->Unit) {
    Button(onClick={
        val active=events.firstOrNull {it.kind=="sos" && it.sender_id==snapshot.profile?.id}
        if(active!=null) openEvent(active.id) else openEditor()
    },modifier=modifier,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFB3261E))) {Text("SOS")}
}
