package com.whereweare.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Wait for an in-flight refresh; never resume a request with the old token. */
internal class SessionRefreshGate {
    private val mutex=Mutex()
    suspend fun refresh(token: ()->String?,renew: suspend ()->Unit) {
        val observed=token()
        mutex.withLock {if(token()==observed) renew()}
    }
}
