package com.whereweare.app.domain

import com.whereweare.app.data.RouteEstimate
import java.time.Instant
import kotlin.math.*

// Kept disabled until an approved routing service and field validation exist.
const val ARRIVING_EXPERIMENTAL_ENABLED=false
data class ArrivalObservation(val fix: UserLocation,val route: RouteEstimate?,val routeAt: Instant)
fun aerialMeters(aLat: Double,aLon: Double,bLat: Double,bLon: Double): Double {
    val a=sin(Math.toRadians(bLat-aLat)/2).pow(2)+cos(Math.toRadians(aLat))*cos(Math.toRadians(bLat))*sin(Math.toRadians(bLon-aLon)/2).pow(2)
    return 6371000*2*asin(sqrt(a.coerceIn(0.0,1.0)))
}
/** Pure candidate detector. No location sampling, network, notifications or arming. */
class ArrivalDetector(private val latitude: Double,private val longitude: Double,private val thresholdSeconds: Double=900.0) {
    private var previous: ArrivalObservation?=null
    private var first: Instant?=null
    private var consecutive=0
    private var alerted=false
    private var lastAlert: Instant?=null
    fun observe(sample: ArrivalObservation,now: Instant): Boolean {
        val fix=sample.fix;val route=sample.route
        if(route==null || !route.distanceMeters.isFinite() || route.distanceMeters<0 || !route.durationSeconds.isFinite() || route.durationSeconds<0 ||
            fix.precisionMeters!=0 || fix.accuracy !in 0.0..100.0 || fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0 ||
            fix.recordedAt<now.minusSeconds(120) || fix.recordedAt>now.plusSeconds(30) || sample.routeAt<now.minusSeconds(120) ||
            sample.routeAt>now.plusSeconds(30) || sample.routeAt<fix.recordedAt.minusSeconds(30)) {
            previous=null;first=null;consecutive=0;return false
        }
        if(route.durationSeconds>thresholdSeconds+300) alerted=false
        val old=previous
        if(old!=null && fix.recordedAt<=old.fix.recordedAt) return false
        val toward=old!=null && fix.recordedAt<=old.fix.recordedAt.plusSeconds(180) &&
            route.durationSeconds<requireNotNull(old.route).durationSeconds-5 && route.distanceMeters<old.route.distanceMeters-10 &&
            aerialMeters(old.fix.latitude,old.fix.longitude,fix.latitude,fix.longitude)>max(20.0,old.fix.accuracy+fix.accuracy) &&
            aerialMeters(fix.latitude,fix.longitude,latitude,longitude)<aerialMeters(old.fix.latitude,old.fix.longitude,latitude,longitude)-5
        if(toward) consecutive++ else {consecutive=1;first=fix.recordedAt}
        previous=sample
        if(consecutive>=3 && first?.plusSeconds(60)?.let {fix.recordedAt>=it}==true && route.durationSeconds<thresholdSeconds &&
            !alerted && (lastAlert==null || now>=lastAlert!!.plusSeconds(1800))) {
            alerted=true;lastAlert=now;return true
        }
        return false
    }
}
