package com.whereweare.app.data

import com.whereweare.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun sosCountdown(tick: (Int)->Unit,elapsed: ()->Unit) {
    for(second in 5 downTo 1) {tick(second);delay(1000)}
    elapsed()
}

/** The registration ACK is the only transition to CONFIRMED. */
suspend fun dispatchSos(capture: suspend ()->UserLocation?,register: suspend (UserLocation?)->Unit,report: (SosSendState)->Unit) {
    report(SosSendState.SENDING)
    try {
        val fix=try {capture()} catch(e: CancellationException) {throw e} catch(_: Exception) {null}
        register(fix)
        report(SosSendState.CONFIRMED)
    } catch(e: CancellationException) {report(SosSendState.UNKNOWN);throw e}
    catch(_: Exception) {report(SosSendState.UNKNOWN)}
}
