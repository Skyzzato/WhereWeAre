package com.whereweare.app.data

import android.content.Context
import android.net.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkTransport { NONE, WIFI, MOBILE, ETHERNET, VPN, OTHER }
data class NetworkConnection(val online: Boolean=false,val transport: NetworkTransport=NetworkTransport.NONE)
@Singleton class NetworkMonitor @Inject constructor(@ApplicationContext context: Context) {
    private val manager=context.getSystemService(ConnectivityManager::class.java)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private fun connection(): NetworkConnection {
        val caps=manager.getNetworkCapabilities(manager.activeNetwork) ?: return NetworkConnection()
        val transport=when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransport.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            else -> NetworkTransport.OTHER
        }
        return NetworkConnection(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),transport)
    }
    val state=callbackFlow {
        val callback=object: ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network,caps: NetworkCapabilities) { trySend(connection()) }
            override fun onLost(network: Network) { trySend(connection()) }
        }
        manager.registerDefaultNetworkCallback(callback); trySend(connection())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope,SharingStarted.WhileSubscribed(5_000),connection())
    val online=state.map {it.online}.stateIn(scope,SharingStarted.WhileSubscribed(5_000),connection().online)
}
