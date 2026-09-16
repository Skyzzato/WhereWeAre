package com.whereweare.app.domain

/** Persisted values identify the original choices; do not migrate or rewrite them. */
fun mapAvatarDp(preference: Float,selected: Boolean=false): Float {
    val diameter=when(preference) { .75f -> 24f; 1.25f -> 52f; 1.5f -> 72f; else -> 36f }
    return if(selected) diameter*54f/32f else diameter
}
