package com.whereweare.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp class WhereWeAreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.whereweare.app.ui.Strings.configure(this)
        if(BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() && BuildConfig.FIREBASE_SENDER_ID.isNotBlank()) {
            com.google.firebase.FirebaseApp.initializeApp(this,com.google.firebase.FirebaseOptions.Builder().setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY).setProjectId(BuildConfig.FIREBASE_PROJECT_ID).setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID).build())
        }
        org.maplibre.compose.map.DefaultMapRuntime.configure(org.maplibre.compose.map.MapRuntimeOptions(
            maximumCacheSizeBytes=64L*1024*1024,
            requestInterceptor=org.maplibre.compose.resource.MapRequestInterceptor(headers={
                mapOf("User-Agent" to "WhereWeAre/${BuildConfig.VERSION_NAME} (Android; https://github.com/Skyzzato/WhereWeAre)")
            })
        ))
    }
}
