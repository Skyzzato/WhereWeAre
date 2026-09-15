package com.whereweare.app.ui

import android.view.Window
import android.view.WindowManager

class QrWindowState(private val window: Window) {
    private val originalBrightness=window.attributes.screenBrightness
    private val originallyAwake=window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    fun show() {
        window.attributes=window.attributes.apply {screenBrightness=1f}
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    fun restore() {
        window.attributes=window.attributes.apply {screenBrightness=originalBrightness}
        if(originallyAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
