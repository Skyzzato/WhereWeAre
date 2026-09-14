package com.whereweare.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp class WhereWeAreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        org.maplibre.compose.map.DefaultMapRuntime.configure(org.maplibre.compose.map.MapRuntimeOptions(
            maximumCacheSizeBytes=64L*1024*1024,
            requestInterceptor=org.maplibre.compose.resource.MapRequestInterceptor(headers={
                mapOf("User-Agent" to "WhereWeAre/${BuildConfig.VERSION_NAME} (Android; https://github.com/Skyzzato/WhereWeAre)")
            })
        ))
    }
}
