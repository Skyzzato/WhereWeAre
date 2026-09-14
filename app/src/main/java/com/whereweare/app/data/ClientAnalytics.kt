package com.whereweare.app.data

import com.whereweare.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/** Optional, typed events only: no location, contact name, or arbitrary exception payload. */
@Singleton class ClientAnalytics @Inject constructor(private val client: SupabaseClient,private val preferences: PreferencesRepository) {
    private val json=Json {ignoreUnknownKeys=true}
    suspend fun preferencesChanged() {
        try {
            val config=preferences.bootstrap.first()?.let {json.decodeFromString<BootstrapConfig>(it)} ?: return
            if(!config.features.client_analytics) return
            val theme=preferences.theme.first();val scale=preferences.avatarScale.first();val language=preferences.language.first()
            client.postgrest.rpc("record_client_event",buildJsonObject {
                put("event_name","preferences");put("version",BuildConfig.VERSION_NAME)
                putJsonObject("properties") {put("theme",theme);put("avatar_scale",scale);put("language",language)}
            })
        } catch(e: CancellationException) {throw e} catch(_: Exception) { /* Telemetry must never block a setting. */ }
    }
}
