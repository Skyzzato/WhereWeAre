package com.whereweare.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.*
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

@Singleton class LocationRepository @Inject constructor(@ApplicationContext private val context: Context,
    private val fused: FusedLocationProviderClient,private val auth: AuthRepository) {
    private fun granted(p: String)=ContextCompat.checkSelfPermission(context,p)==PackageManager.PERMISSION_GRANTED
    fun permission()=locationPermission(granted(Manifest.permission.ACCESS_COARSE_LOCATION),granted(Manifest.permission.ACCESS_FINE_LOCATION))
    fun hasPermission()=permission()!=LocationPermission.NONE
    fun enabled()=LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))
    private fun priority(high: Boolean)=if(high && permission()==LocationPermission.PRECISE) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
    private fun domain(fix: Location): UserLocation? {
        val age=((SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)/1_000_000).coerceAtLeast(0)
        if(!fix.hasAccuracy() || age>30_000) return null
        return UserLocation(auth.userId.orEmpty(),fix.latitude,fix.longitude,fix.accuracy.toDouble(),
            if(fix.hasSpeed()) fix.speed.toDouble() else null,if(fix.hasBearing()) fix.bearing.toDouble() else null,Instant.now().minusMillis(age))
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
                check(hasPermission()) { "location_permission" }
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
