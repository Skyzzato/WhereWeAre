package com.whereweare.app.data

import com.whereweare.app.BuildConfig
import com.whereweare.app.domain.UserLocation
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.URI
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import javax.inject.Inject

enum class TravelMode(val costing: String) { WALK("pedestrian"), CAR("auto"), BICYCLE("bicycle") }
data class RouteEstimate(val distanceMeters: Double,val durationSeconds: Double)
interface RoutingRepository {
    val available: Boolean
    suspend fun route(origin: UserLocation,latitude: Double,longitude: Double,mode: TravelMode,now: Instant): RouteEstimate?
}
fun validRoutingEndpoint(endpoint: String)=runCatching {
    val uri=URI(endpoint)
    uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.query==null && uri.fragment==null
}.getOrDefault(false)
fun validRouteOrigin(origin: UserLocation,now: Instant)=origin.precisionMeters==0 && origin.accuracy in 0.0..100.0 &&
    origin.recordedAt>=now.minusSeconds(120) && origin.recordedAt<=now.plusSeconds(30) &&
    origin.latitude in -90.0..90.0 && origin.longitude in -180.0..180.0
fun valhallaRequest(origin: UserLocation,latitude: Double,longitude: Double,mode: TravelMode)=buildJsonObject {
    putJsonArray("locations") {
        add(buildJsonObject {put("lat",origin.latitude);put("lon",origin.longitude)})
        add(buildJsonObject {put("lat",latitude);put("lon",longitude)})
    }
    put("costing",mode.costing);put("units","kilometers");put("directions_type","none")
    if(mode==TravelMode.WALK) putJsonObject("costing_options") {
        putJsonObject("pedestrian") {put("max_hiking_difficulty",1);put("use_hills",0.5)}
    }
}.toString()
fun parseValhallaResponse(body: String): RouteEstimate?=runCatching {
    val trip=Json.parseToJsonElement(body).jsonObject.getValue("trip").jsonObject
    require(trip.getValue("status").jsonPrimitive.int==0)
    require(trip.getValue("units").jsonPrimitive.content in setOf("kilometers","km"))
    val summary=trip.getValue("summary").jsonObject
    val meters=summary.getValue("length").jsonPrimitive.double*1000
    val seconds=summary.getValue("time").jsonPrimitive.double
    require(meters.isFinite() && meters in 0.0..40_000_000.0 && seconds.isFinite() && seconds in 0.0..31_536_000.0)
    RouteEstimate(meters,seconds)
}.getOrNull()
fun interface RoutingTransport {suspend fun post(endpoint: String,body: String): String}
class HttpsRoutingTransport: RoutingTransport {
    override suspend fun post(endpoint: String,body: String): String=withContext(Dispatchers.IO) {
        val connection=URI(endpoint).toURL().openConnection() as HttpsURLConnection
        try {
            connection.requestMethod="POST";connection.connectTimeout=8000;connection.readTimeout=10000
            connection.instanceFollowRedirects=false;connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json")
            connection.outputStream.use {it.write(body.toByteArray(Charsets.UTF_8))}
            check(connection.responseCode==200)
            connection.inputStream.bufferedReader().use {reader ->
                val buffer=CharArray(4096);val result=StringBuilder()
                while(true) {ensureActive();val n=reader.read(buffer);if(n<0) break;check(result.length+n<=524288);result.append(buffer,0,n)}
                result.toString()
            }
        } finally {connection.disconnect()}
    }
}
class ValhallaRoutingRepository(private val endpoint: String,private val transport: RoutingTransport=HttpsRoutingTransport()): RoutingRepository {
    override val available=validRoutingEndpoint(endpoint)
    override suspend fun route(origin: UserLocation,latitude: Double,longitude: Double,mode: TravelMode,now: Instant): RouteEstimate? {
        if(!available || !validRouteOrigin(origin,now) || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return try {withTimeout(20_000) {parseValhallaResponse(transport.post(endpoint,valhallaRequest(origin,latitude,longitude,mode)))}}
        catch(_: TimeoutCancellationException) {null} catch(e: CancellationException) {throw e} catch(_: Exception) {null}
    }
}
class ConfiguredRoutingRepository @Inject constructor(): RoutingRepository {
    private val delegate=ValhallaRoutingRepository(if(BuildConfig.ROUTING_PROVIDER=="valhalla") BuildConfig.ROUTING_ENDPOINT else "")
    override val available get()=delegate.available
    override suspend fun route(origin: UserLocation,latitude: Double,longitude: Double,mode: TravelMode,now: Instant)=delegate.route(origin,latitude,longitude,mode,now)
}
