package com.whereweare.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.GnssStatus
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import com.whereweare.app.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceLocationDetails(val fix: UserLocation,val altitude: Double?,val provider: String?)
data class SatelliteDetails(val visible: Int,val used: Int,val observedAt: Instant)
@Singleton class LocationRepository @Inject constructor(@ApplicationContext private val context: Context,
    private val fused: FusedLocationProviderClient,private val auth: AuthRepository) {
    private val mutableDetails=MutableStateFlow<DeviceLocationDetails?>(null)
    val details=mutableDetails.asStateFlow()
    private fun granted(p: String)=ContextCompat.checkSelfPermission(context,p)==PackageManager.PERMISSION_GRANTED
    fun permission()=locationPermission(granted(Manifest.permission.ACCESS_COARSE_LOCATION),granted(Manifest.permission.ACCESS_FINE_LOCATION))
    fun hasPermission()=permission()!=LocationPermission.NONE
    fun enabled()=LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))
    fun deviceStatus()=DeviceStatus(validBatteryLevel(context.getSystemService(android.os.BatteryManager::class.java)
        .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)),enabled())
    private fun priority(high: Boolean)=if(high && permission()==LocationPermission.PRECISE) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
    suspend fun snapshot(high: Boolean): UserLocation {
        check(hasPermission()) {"location_permission"};check(enabled()) {"location_disabled"}
        return withTimeout(35_000) {fixes(high,300).first()}
    }
    private fun domain(fix: Location): UserLocation? {
        val age=((SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)/1_000_000).coerceAtLeast(0)
        if(!fix.hasAccuracy() || age>30_000) return null
        val result=UserLocation(auth.userId.orEmpty(),fix.latitude,fix.longitude,fix.accuracy.toDouble(),
            if(fix.hasSpeed()) fix.speed.toDouble() else null,if(fix.hasBearing()) fix.bearing.toDouble() else null,Instant.now().minusMillis(age))
        mutableDetails.value=DeviceLocationDetails(result,if(fix.hasAltitude()) fix.altitude else null,fix.provider)
        return result
    }
    // Passive GNSS diagnostics: registering this listener never starts location tracking.
    @SuppressLint("MissingPermission")
    fun satellites(): Flow<SatelliteDetails?> = callbackFlow {
        if(permission()!=LocationPermission.PRECISE) {trySend(null);close();return@callbackFlow}
        val manager=context.getSystemService(LocationManager::class.java)
        val callback=object: GnssStatus.Callback() {
            override fun onStopped() {trySend(null)}
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                trySend(SatelliteDetails(status.satelliteCount,(0 until status.satelliteCount).count {status.usedInFix(it)},Instant.now()))
            }
        }
        val registered=try {manager.registerGnssStatusCallback(callback,Handler(Looper.getMainLooper()))}
            catch(_: SecurityException) {false}
        trySend(null)
        if(!registered) {close();return@callbackFlow}
        awaitClose { manager.unregisterGnssStatusCallback(callback) }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    fun fixes(high: Boolean,seconds: Int): Flow<UserLocation> {
        require(seconds in updateIntervals)
        if(seconds<300) return callbackFlow {
            if(!hasPermission()) { close(SecurityException("location_permission")); return@callbackFlow }
            val callback=object: LocationCallback() {
                override fun onLocationResult(result: LocationResult) { result.lastLocation?.let(::domain)?.let { trySend(it) } }
            }
            val request=LocationRequest.Builder(priority(high),seconds*1000L)
                .setMinUpdateIntervalMillis(seconds*1000L).setMaxUpdateDelayMillis(0)
                .setMaxUpdateAgeMillis(0).setWaitForAccurateLocation(false).build()
            fused.requestLocationUpdates(request,callback,Looper.getMainLooper()).addOnFailureListener { close(it) }
            awaitClose { fused.removeLocationUpdates(callback) }
        }
        // Release FLP between bounded single-shot acquisitions for long intervals.
        return flow {
            while(currentCoroutineContext().isActive) {
                if(!hasPermission()) throw SecurityException("location_permission")
                if(!enabled()) {delay(5_000);continue}
                val token=CancellationTokenSource()
                val started=SystemClock.elapsedRealtime()
                var acquired=false
                try {
                    val request=CurrentLocationRequest.Builder().setPriority(priority(high))
                        .setMaxUpdateAgeMillis(0).setDurationMillis(30_000).build()
                    fused.getCurrentLocation(request,token.token).await(token)?.let(::domain)?.let { emit(it); acquired=true }
                } finally { token.cancel() }
                delay(if(acquired) (seconds*1000L-(SystemClock.elapsedRealtime()-started)).coerceAtLeast(0) else 30_000)
            }
        }
    }
}
