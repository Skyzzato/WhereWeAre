package com.whereweare.app.ui

import com.whereweare.app.R

fun availableAccuracy(value: Double?): Long? = value?.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE.toDouble() }?.toLong()
fun accuracyText(value: Double?): String = availableAccuracy(value)?.let { Strings.text(R.string.accuracy_value,it.toString()) }
    ?: Strings.text(R.string.accuracy_unavailable)
fun sharingAccuracyText(published: Boolean,value: Double?): String = if(!published) Strings.text(R.string.position_waiting)
    else Strings.text(R.string.position_shared_accuracy,accuracyText(value))
