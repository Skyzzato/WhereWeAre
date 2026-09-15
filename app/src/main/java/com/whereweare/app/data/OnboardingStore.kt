package com.whereweare.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class OnboardingStore @Inject constructor(@ApplicationContext context: Context) {
    private val storage=context.getSharedPreferences("onboarding",0)
    private val done=MutableStateFlow(storage.getBoolean("completed",false))
    val completed=done.asStateFlow()
    fun complete(): Boolean {
        if(!storage.edit().putBoolean("completed",true).commit()) return false
        done.value=true
        return true
    }
}
