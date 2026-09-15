package com.whereweare.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class PreferencesRepository @Inject constructor(private val store: DataStore<Preferences>,@dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context) {
    private val high = booleanPreferencesKey("high_accuracy")
    private val pending = stringPreferencesKey("pending_stop_user")
    private val pendingSession = stringPreferencesKey("pending_stop_session")
    val pendingStopSession=store.data.map {it[pendingSession]}
    private val intervalKey=intPreferencesKey("interval_seconds")
    private val thresholdKey=intPreferencesKey("accuracy_threshold")
    private val styleKey=stringPreferencesKey("map_style")
    private val bootstrapKey=stringPreferencesKey("bootstrap")
    private val trackingKey=stringPreferencesKey("tracking_session")
    private val themeKey=stringPreferencesKey("theme")
    private val scaleKey=floatPreferencesKey("avatar_scale")
    private val languageKey=stringPreferencesKey("language")
    private val flareStyleKey=intPreferencesKey("flare_style_id")
    private val flareSoundKey=booleanPreferencesKey("flare_sound")
    val flareStyle=store.data.map {com.whereweare.app.domain.FlareStyles.normalize(it[flareStyleKey])}
    val flareSound=store.data.map {it[flareSoundKey] ?: true}
    suspend fun flareStyle(id: Int) {store.edit {it[flareStyleKey]=com.whereweare.app.domain.FlareStyles.normalize(id)}}
    suspend fun flareSound(enabled: Boolean) {store.edit {it[flareSoundKey]=enabled}}
    private val json=kotlinx.serialization.json.Json {ignoreUnknownKeys=true}
    private fun defaults(p: Preferences)=p[bootstrapKey]?.let { runCatching { json.decodeFromString<BootstrapConfig>(it).defaults }.getOrNull() } ?: GlobalDefaults()
    val theme=store.data.map { it[themeKey] ?: defaults(it).theme }
    val avatarScale=store.data.map { it[scaleKey] ?: defaults(it).avatar_scale.takeIf { s -> s in listOf(.75f,1f,1.25f,1.5f) } ?: 1f }
    val language=store.data.map { it[languageKey] ?: "system" }
    suspend fun theme(value: String) { require(value in listOf("default","ocean","sunset","lavender","graphite","dark")); store.edit { it[themeKey]=value } }
    suspend fun avatarScale(value: Float) { require(value in listOf(.75f,1f,1.25f,1.5f)); store.edit { it[scaleKey]=value } }
    suspend fun language(value: String) { require(value in listOf("system","it","en")); store.edit { it[languageKey]=value }; context.getSharedPreferences("appearance",0).edit().putString("language",value).apply() }
    fun hiddenGroups(user: String)=store.data.map { it[stringSetPreferencesKey("hidden_groups_$user")] ?: emptySet() }
    suspend fun hideGroup(user: String,gid: String,hide: Boolean) { store.edit { p -> val key=stringSetPreferencesKey("hidden_groups_$user"); val old=p[key] ?: emptySet(); p[key]=if(hide) old+gid else old-gid } }
    suspend fun markMeetingSeen(user: String,event: String): Boolean { var fresh=false; store.edit { p -> val key=stringSetPreferencesKey("meeting_seen_$user"); val seen=p[key] ?: emptySet(); fresh=event !in seen; p[key]=seen+event }; return fresh }
    val trackingSession=store.data.map { p -> p[trackingKey]?.let { runCatching { kotlinx.serialization.json.Json.decodeFromString<TrackingSession>(it) }.getOrNull() } }
    suspend fun trackingSession(value: TrackingSession?) { store.edit { p ->
        if(value!=null) check(p[pending]==null) { "stop_pending" }
        if(value==null) p.remove(trackingKey) else p[trackingKey]=kotlinx.serialization.json.Json.encodeToString(TrackingSession.serializer(),value)
    } }
    val interval=store.data.map { it[intervalKey] ?: defaults(it).gps_interval_seconds.takeIf { n -> n in com.whereweare.app.domain.updateIntervals } ?: 60 }
    val threshold=store.data.map { it[thresholdKey] ?: 100 }
    val mapStyle=store.data.map { it[styleKey] ?: "standard" }
    val bootstrap=store.data.map { it[bootstrapKey] }
    fun hidden(user: String)=store.data.map { it[stringSetPreferencesKey("hidden_$user")] ?: emptySet() }
    suspend fun hide(user: String,ids: Set<String>,hide: Boolean) { store.edit { p ->
        val key=stringSetPreferencesKey("hidden_$user"); val old=p[key] ?: emptySet(); p[key]=if(hide) old+ids else old-ids
    } }
    suspend fun interval(value: Int) { require(value in com.whereweare.app.domain.updateIntervals); store.edit { it[intervalKey]=value } }
    suspend fun threshold(value: Int) { require(value in com.whereweare.app.domain.accuracyThresholds); store.edit { it[thresholdKey]=value } }
    suspend fun mapStyle(value: String) { require(com.whereweare.app.domain.MapStyle.entries.any { it.id==value }); store.edit { it[styleKey]=value } }
    suspend fun bootstrap(value: String) { store.edit { it[bootstrapKey]=value } }
    suspend fun clearUser(user: String) { store.edit { it.remove(stringSetPreferencesKey("hidden_$user")); it.remove(stringPreferencesKey("last_fix_$user")) } }
    fun lastFix(user: String)=store.data.map { it[stringPreferencesKey("last_fix_$user")] }
    suspend fun lastFix(user: String,value: String) { store.edit { it[stringPreferencesKey("last_fix_$user")]=value } }
    val highAccuracy = store.data.map { it[high] ?: defaults(it).high_accuracy }
    val pendingStop = store.data.map { it[pending] }
    suspend fun accuracy(value: Boolean) { store.edit { it[high] = value } }
    suspend fun pendingStop(userId: String?,sessionId: String?=null) { store.edit {
        if(userId == null) it.remove(pending) else { it[pending] = userId; it.remove(trackingKey) }
        if(userId!=null && sessionId!=null) it[pendingSession]=sessionId else it.remove(pendingSession)
    } }
}

@kotlinx.serialization.Serializable
data class TrackingSession(val userId: String,val sessionId: String,val revision: Long?=null)
