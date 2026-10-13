package com.whereweare.app.data

import com.whereweare.app.domain.UserLocation

/** Only a fix from this boot's live provider can be uploaded; persisted/display caches cannot. */
fun fixAgeMillis(fix: UserLocation,elapsedNanos: Long): Long = fix.elapsedRealtimeNanos?.let {
    if(it>elapsedNanos || it<0) Long.MAX_VALUE else (elapsedNanos-it)/1_000_000
} ?: Long.MAX_VALUE
