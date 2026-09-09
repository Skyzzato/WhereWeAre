package com.whereweare.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.*
import com.whereweare.app.domain.UserLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fused: FusedLocationProviderClient,
    private val auth: AuthRepository
) {
    fun hasPermission() = ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    fun enabled() = LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))
    @SuppressLint("MissingPermission")
    fun fixes(high: Boolean) = callbackFlow {
        if(!hasPermission()) { close(SecurityException("location_permission")); return@callbackFlow }
        val callback=object: LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { fix ->
                    val ageMillis=((SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)/1_000_000).coerceAtLeast(0)
                    // Do not rejuvenate cached fixes after a connection failure.
                    if(ageMillis<=7_200_000) trySend(UserLocation(auth.userId.orEmpty(),fix.latitude,fix.longitude,fix.accuracy.toDouble(),
                        if(fix.hasSpeed()) fix.speed.toDouble() else null,
                        if(fix.hasBearing()) fix.bearing.toDouble() else null,
                        Instant.now().minusMillis(ageMillis)))
                }
            }
        }
        val request=LocationRequest.Builder(if(high) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,60_000)
            .setMinUpdateIntervalMillis(35_000).setMaxUpdateDelayMillis(60_000).setMaxUpdateAgeMillis(0).build()
        fused.requestLocationUpdates(request,callback,Looper.getMainLooper()).addOnFailureListener { close(it) }
        awaitClose { fused.removeLocationUpdates(callback) }
    }
}
