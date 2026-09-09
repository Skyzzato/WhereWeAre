package com.whereweare.app.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.location.LocationServices
import com.whereweare.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton

private val Context.preferences by preferencesDataStore("preferences")
@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun supabase() = createSupabaseClient(
        BuildConfig.SUPABASE_URL.ifBlank { "https://unconfigured.supabase.co" },
        BuildConfig.SUPABASE_ANON_KEY.ifBlank { "unconfigured" }
    ) { install(Auth); install(Postgrest); install(Realtime) }
    @Provides @Singleton fun fused(@ApplicationContext context: Context) = LocationServices.getFusedLocationProviderClient(context)
    @Provides @Singleton fun preferences(@ApplicationContext context: Context) = context.preferences
}
