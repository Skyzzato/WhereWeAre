package com.whereweare.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.whereweare.app.data.AuthRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class StopSharingWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    @EntryPoint @InstallIn(SingletonComponent::class)
    interface Dependencies { fun controller(): SharingController; fun auth(): AuthRepository }
    override suspend fun doWork(): Result {
        val deps=EntryPointAccessors.fromApplication(applicationContext,Dependencies::class.java)
        return try {
            deps.auth().session.first { it !is SessionStatus.Initializing }
            if(deps.controller().completePendingStop(inputData.getString("user_id") ?: return Result.failure())) Result.success() else Result.retry()
        } catch(e: CancellationException) { throw e } catch(_: Exception) { Result.retry() }
    }
}
