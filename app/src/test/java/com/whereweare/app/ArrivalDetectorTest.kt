package com.whereweare.app

import com.whereweare.app.domain.*
import com.whereweare.app.data.RouteEstimate
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ArrivalDetectorTest {
    private val start=Instant.parse("2026-09-15T12:00:00Z")
    private fun sample(step: Int,eta: Double=800.0-step*30,route: Boolean=true)=ArrivalObservation(
        UserLocation("u",46.01-step*.001,11.0,5.0,null,null,start.plusSeconds(step*30L)),
        if(route) RouteEstimate(1500.0-step*100,eta) else null,start.plusSeconds(step*30L))
    @Test fun requiresRealEtaAndConsecutiveCoherentMovement() {
        assertFalse(ARRIVING_EXPERIMENTAL_ENABLED)
        val detector=ArrivalDetector(46.0,11.0)
        repeat(4) {assertFalse(detector.observe(sample(it,route=false),sample(it).routeAt))}
        assertFalse(detector.observe(sample(4),sample(4).routeAt))
        assertFalse(detector.observe(sample(5),sample(5).routeAt))
        assertTrue(detector.observe(sample(6),sample(6).routeAt))
        assertFalse(detector.observe(sample(7),sample(7).routeAt))
    }
    @Test fun stationaryStaleAndRecedingSamplesDoNotTrigger() {
        val stationary=ArrivalDetector(46.0,11.0)
        repeat(4) {i -> val s=sample(i);assertFalse(stationary.observe(s.copy(fix=s.fix.copy(latitude=46.01)),s.routeAt))}
        val increasing=ArrivalDetector(46.0,11.0)
        repeat(4) {i -> val s=sample(i,eta=700.0+i*30);assertFalse(increasing.observe(s,s.routeAt))}
        val stale=ArrivalDetector(46.0,11.0)
        repeat(4) {i -> val s=sample(i);assertFalse(stale.observe(s,s.routeAt.plusSeconds(121)))}
    }
}
