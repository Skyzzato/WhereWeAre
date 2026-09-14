package com.whereweare.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf

/** Resource access shared by Compose text, callbacks and formatters. Only locale changes invalidate it. */
object Strings {
    private val current=mutableStateOf<Context?>(null)
    fun configure(context: Context) {current.value=context}
    fun text(@StringRes id: Int,vararg args: Any)=requireNotNull(current.value).getString(id,*args)
}
