package com.whereweare.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whereweare.app.R
import com.whereweare.app.domain.validGroupName
import com.whereweare.app.domain.groupExpiry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable fun GroupIdentity(emoji: String,name: String,modifier: Modifier=Modifier) {
    Row(modifier,verticalAlignment=Alignment.CenterVertically) {
        Text(emoji.ifBlank { "＋" },fontSize=31.sp)
        Text(name,Modifier.weight(1f).padding(start=8.dp))
    }
}

@Composable fun GroupEditor(editing: Boolean,initialName: String="",initialEmoji: String="📍",busy: Boolean,
    temporaryAvailable: Boolean=false,initialExpiry: Instant?=null,error: Int?=null,
    dismiss: ()->Unit,save: (String,String,Instant?)->Unit) {
    var name by rememberSaveable(initialName) {mutableStateOf(initialName)}
    var emoji by rememberSaveable(initialEmoji) {mutableStateOf(initialEmoji)}
    val context=LocalContext.current
    val zone=ZoneId.systemDefault()
    val initial=remember(initialExpiry) {(initialExpiry?:Instant.now().plusSeconds(8*3600)).atZone(zone)}
    var temporary by rememberSaveable {mutableStateOf(initialExpiry!=null)}
    var endDate by rememberSaveable {mutableStateOf(initial.toLocalDate().toString())}
    var endTime by rememberSaveable {mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0).toString())}
    var now by remember {mutableStateOf(Instant.now())}
    LaunchedEffect(Unit) {while(true) {now=Instant.now();kotlinx.coroutines.delay(1000)}}
    val expiry=if(temporary) groupExpiry(endDate,endTime,zone,now) else null
    val emojis=listOf("📍","👨‍👩‍👧‍👦","🏠","❤️","👋","😊","🏔️","🥾","⛰️","🌲","🏕️","🔥","🚴","🚵","🏃","⚽","🏀","🎾","🏊","⛷️","🏂","🚗","🏍️","🚐","⛵","✈️","🚆","🌍","🏖️","🏝️","🎒","🏫","🎓","💼","🛠️","💻","🎉","🎂","🎵","🍕")
    AlertDialog(properties=DialogProperties(usePlatformDefaultWidth=false),modifier=Modifier.fillMaxWidth().padding(12.dp),onDismissRequest={if(!busy) dismiss()},title={Text(Strings.text(if(editing) R.string.ui_026 else R.string.ui_017))},
        text={Column(Modifier.heightIn(max=600.dp).verticalScroll(rememberScrollState()).padding(top=16.dp)) {
            OutlinedTextField(name,{name=it},enabled=!busy,label={Text(Strings.text(R.string.ui_034))},singleLine=true,
                isError=name.isNotEmpty() && !validGroupName(name))
            if(temporaryAvailable) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Switch(temporary,{temporary=it},enabled=!busy)
                    Text(Strings.text(if(temporary) R.string.group_temporary else R.string.group_permanent))
                }
                if(temporary) {
                    OutlinedButton(enabled=!busy,onClick={
                        val d=LocalDate.parse(endDate)
                        android.app.DatePickerDialog(context,{_,y,m,day -> endDate=LocalDate.of(y,m+1,day).toString()},d.year,d.monthValue-1,d.dayOfMonth).show()
                    }) {Text(Strings.text(R.string.group_end_date,endDate))}
                    OutlinedButton(enabled=!busy,onClick={
                        val t=LocalTime.parse(endTime)
                        android.app.TimePickerDialog(context,{_,h,m -> endTime="%02d:%02d".format(java.util.Locale.ROOT,h,m)},t.hour,t.minute,android.text.format.DateFormat.is24HourFormat(context)).show()
                    }) {Text(Strings.text(R.string.group_end_time,endTime))}
                    if(expiry==null) Text(Strings.text(R.string.group_invalid_expiry),color=MaterialTheme.colorScheme.error)
                }
            }
            Busy(OperationState(busy,error),inline=true)
            Text(Strings.text(if(emoji.isBlank()) R.string.group_add_icon else R.string.ui_035,emoji))
            if(emoji.isNotBlank()) TextButton(enabled=!busy,onClick={emoji=""}) {Text(Strings.text(R.string.group_remove_icon))}
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val minimumCell=maxOf(48f,27f*androidx.compose.ui.platform.LocalDensity.current.fontScale+8f)
            val columns=(maxWidth.value/minimumCell).toInt().coerceAtLeast(1)
            val cellWidth=maxWidth/columns
            Column {emojis.chunked(columns).forEach {row -> Row {
                row.forEach {value -> TextButton(enabled=!busy,onClick={emoji=value},modifier=Modifier.width(cellWidth).heightIn(min=48.dp),
                    contentPadding=PaddingValues(1.dp),colors=ButtonDefaults.textButtonColors(
                        containerColor=if(value==emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {Text(value,fontSize=27.sp)}
            }}}}}
        }},confirmButton={TextButton(enabled=validGroupName(name)&&!busy&&(!temporary||expiry!=null),onClick={save(name.trim(),emoji,expiry)}) {
            Text(Strings.text(if(editing) R.string.group_save_changes else R.string.ui_036))
        }},dismissButton={TextButton(enabled=!busy,onClick=dismiss) {Text(Strings.text(R.string.ui_006))}})
}
