package com.whereweare.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class PreferencesRepository @Inject constructor(private val store: DataStore<Preferences>) {
    private val high = booleanPreferencesKey("high_accuracy")
    private val pending = stringPreferencesKey("pending_stop_user")
    private val intervalKey=intPreferencesKey("interval_seconds")
    private val thresholdKey=intPreferencesKey("accuracy_threshold")
    private val styleKey=stringPreferencesKey("map_style")
    private val bootstrapKey=stringPreferencesKey("bootstrap")
    val interval=store.data.map { it[intervalKey] ?: 60 }
    val threshold=store.data.map { it[thresholdKey] ?: 100 }
    val mapStyle=store.data.map { it[styleKey] ?: "standard" }
    val bootstrap=store.data.map { it[bootstrapKey] }
    fun hidden(user: String)=store.data.map { it[stringSetPreferencesKey("hidden_$user")] ?: emptySet() }
    suspend fun hide(user: String,ids: Set<String>,hide: Boolean) { store.edit { p ->
        val key=stringSetPreferencesKey("hidden_$user"); val old=p[key] ?: emptySet(); p[key]=if(hide) old+ids else old-ids
    } }
    suspend fun interval(value: Int) { require(value in com.whereweare.app.domain.updateIntervals); store.edit { it[intervalKey]=value } }
    suspend fun threshold(value: Int) { require(value in com.whereweare.app.domain.accuracyThresholds); store.edit { it[thresholdKey]=value } }
    suspend fun mapStyle(value: String) { require(value in listOf("standard","topo")); store.edit { it[styleKey]=value } }
    suspend fun bootstrap(value: String) { store.edit { it[bootstrapKey]=value } }
    suspend fun clearUser(user: String) { store.edit { it.remove(stringSetPreferencesKey("hidden_$user")); it.remove(stringPreferencesKey("last_fix_$user")) } }
    fun lastFix(user: String)=store.data.map { it[stringPreferencesKey("last_fix_$user")] }
    suspend fun lastFix(user: String,value: String) { store.edit { it[stringPreferencesKey("last_fix_$user")]=value } }
    val highAccuracy = store.data.map { it[high] ?: false }
    val pendingStop = store.data.map { it[pending] }
    suspend fun accuracy(value: Boolean) { store.edit { it[high] = value } }
    suspend fun pendingStop(userId: String?) { store.edit { if(userId == null) it.remove(pending) else it[pending] = userId } }
}
