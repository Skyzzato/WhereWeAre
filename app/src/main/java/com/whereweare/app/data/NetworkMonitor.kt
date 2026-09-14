package com.whereweare.app.data

import android.content.Context
import android.net.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class NetworkMonitor @Inject constructor(@ApplicationContext context: Context) {
    private val manager=context.getSystemService(ConnectivityManager::class.java)
    private fun connected()=manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
    val online=callbackFlow {
        val callback=object: ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network,caps: NetworkCapabilities) { trySend(connected()) }
            override fun onLost(network: Network) { trySend(connected()) }
        }
        manager.registerDefaultNetworkCallback(callback); trySend(connected())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(CoroutineScope(SupervisorJob()+Dispatchers.Default),SharingStarted.WhileSubscribed(5_000),connected())
}
