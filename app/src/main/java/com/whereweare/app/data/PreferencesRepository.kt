package com.whereweare.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class PreferencesRepository @Inject constructor(private val store: DataStore<Preferences>) {
    private val high = booleanPreferencesKey("high_accuracy")
    private val pending = stringPreferencesKey("pending_stop_user")
    val highAccuracy = store.data.map { it[high] ?: false }
    val pendingStop = store.data.map { it[pending] }
    suspend fun accuracy(value: Boolean) { store.edit { it[high] = value } }
    suspend fun pendingStop(userId: String?) { store.edit { if(userId == null) it.remove(pending) else it[pending] = userId } }
}
