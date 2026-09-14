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

@Composable fun GroupIdentity(emoji: String,name: String,modifier: Modifier=Modifier) {
    Row(modifier,verticalAlignment=Alignment.CenterVertically) {
        Text(emoji,fontSize=31.sp)
        Text(name,Modifier.weight(1f).padding(start=8.dp))
    }
}

@Composable fun GroupEditor(editing: Boolean,initialName: String="",initialEmoji: String="📍",busy: Boolean,
    dismiss: ()->Unit,save: (String,String)->Unit) {
    var name by rememberSaveable(initialName) {mutableStateOf(initialName)}
    var emoji by rememberSaveable(initialEmoji) {mutableStateOf(initialEmoji)}
    val emojis=listOf("📍","👨‍👩‍👧‍👦","🏠","❤️","👋","😊","🏔️","🥾","⛰️","🌲","🏕️","🔥","🚴","🚵","🏃","⚽","🏀","🎾","🏊","⛷️","🏂","🚗","🏍️","🚐","⛵","✈️","🚆","🌍","🏖️","🏝️","🎒","🏫","🎓","💼","🛠️","💻","🎉","🎂","🎵","🍕")
    AlertDialog(onDismissRequest=dismiss,title={Text(Strings.text(if(editing) R.string.ui_026 else R.string.ui_017))},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(name,{name=it},label={Text(Strings.text(R.string.ui_034))},singleLine=true,
                isError=name.isNotEmpty() && !validGroupName(name))
            Text(Strings.text(R.string.ui_035,emoji))
            emojis.chunked(5).forEach {row -> Row {
                row.forEach {value -> TextButton(onClick={emoji=value},modifier=Modifier.weight(1f).heightIn(min=48.dp),
                    contentPadding=PaddingValues(1.dp),colors=ButtonDefaults.textButtonColors(
                        containerColor=if(value==emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {Text(value,fontSize=27.sp)}
            }}}
        }},confirmButton={TextButton(enabled=validGroupName(name)&&!busy,onClick={save(name.trim(),emoji)}) {
            Text(Strings.text(if(editing) R.string.save else R.string.ui_036))
        }},dismissButton={TextButton(onClick=dismiss) {Text(Strings.text(R.string.ui_006))}})
}
