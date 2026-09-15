package com.whereweare.app

import com.whereweare.app.data.*
import com.whereweare.app.domain.UserLocation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RoutingTest {
    private val now=Instant.parse("2026-09-15T12:00:00Z")
    private val origin=UserLocation("private-id",46.0,11.0,10.0,null,null,now)
    private val response="""{"trip":{"status":0,"units":"kilometers","summary":{"length":1.5,"time":480}}}"""
    @Test fun disabledOrUncertainOriginsNeverSendCoordinates()=runBlocking {
        var calls=0
        val transport=RoutingTransport {_,_ -> calls++;response}
        assertNull(ValhallaRoutingRepository("",transport).route(origin,46.1,11.1,TravelMode.WALK,now))
        val repo=ValhallaRoutingRepository("https://routing.example/route",transport)
        assertNull(repo.route(origin.copy(precisionMeters=500),46.1,11.1,TravelMode.WALK,now))
        assertNull(repo.route(origin.copy(recordedAt=now.minusSeconds(121)),46.1,11.1,TravelMode.WALK,now))
        assertNull(repo.route(origin.copy(accuracy=200.0),46.1,11.1,TravelMode.WALK,now))
        assertEquals(0,calls)
        assertEquals(RouteEstimate(1500.0,480.0),repo.route(origin,46.1,11.1,TravelMode.WALK,now))
        assertEquals(1,calls)
    }
    @Test fun profilesAndPayloadDoNotIncludeIdentity() {
        TravelMode.entries.forEach {mode ->
            val payload=valhallaRequest(origin,46.1,11.1,mode)
            assertFalse(payload.contains(origin.userId))
            assertEquals(mode.costing,Json.parseToJsonElement(payload).jsonObject.getValue("costing").jsonPrimitive.content)
        }
        assertFalse(validRoutingEndpoint("http://routing.example/route"))
        assertFalse(validRoutingEndpoint("https://user:secret@routing.example/route"))
        assertFalse(validRoutingEndpoint("https://routing.example/route?key=secret"))
    }
    @Test fun malformedAndFailedProviderResponsesNeverProduceEta() {
        assertNull(parseValhallaResponse(response.replace("kilometers","miles")))
        assertNull(parseValhallaResponse(response.replace("480","-1")))
        assertNull(parseValhallaResponse("{}"))
        assertNull(parseValhallaResponse(response.replace("\"status\":0","\"status\":400")))
    }
}
