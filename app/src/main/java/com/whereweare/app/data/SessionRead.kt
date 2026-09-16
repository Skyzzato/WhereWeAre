package com.whereweare.app.data

import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class SessionUnavailableException: IllegalStateException("session_unavailable")

/** Read-only recovery: at most one token renewal and two transient retries, 30s total. */
suspend fun <T> sessionRead(ready: suspend ()->Unit,renew: suspend ()->Unit,read: suspend ()->T): T = withTimeout(30_000) {
    var renewed=false
    var retries=0
    while(true) {
        try { ready(); return@withTimeout withTimeout(8_000) {read()} }
        catch(e: Exception) {
            if((e is SessionUnavailableException || e is RestException && e.statusCode==401) && !renewed) {
                renewed=true
                renew()
            } else if(retryableRead(e) && retries<2) {
                delay(1_000L shl retries++)
            } else throw e
        }
    }
    @Suppress("UNREACHABLE_CODE") error("unreachable")
}
