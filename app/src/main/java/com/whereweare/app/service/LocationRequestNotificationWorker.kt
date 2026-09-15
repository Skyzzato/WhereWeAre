package com.whereweare.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.whereweare.app.MainActivity
import com.whereweare.app.R
import com.whereweare.app.domain.LocationRequest
import com.whereweare.app.ui.localizedContext
import dagger.hilt.android.EntryPointAccessors
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class LocationRequestNotificationWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val entry=EntryPointAccessors.fromApplication(applicationContext,PushDependencies::class.java)
        return try {
            entry.auth().awaitSession()
            val user=entry.auth().userId ?: return Result.success()
            if(user!=inputData.getString("recipient")) return Result.success()
            val id=inputData.getString("request_id") ?: return Result.success()
            val request=entry.client().postgrest.rpc("location_request_inbox").decodeList<LocationRequest>().firstOrNull {it.id==id} ?: return Result.success()
            if(MainActivity.visible || entry.auth().userId!=user) return Result.success()
            val context=localizedContext(applicationContext,entry.preferences().language.first())
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return Result.success()
            val manager=context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("location-requests",context.getString(R.string.location_request_channel),NotificationManager.IMPORTANCE_DEFAULT))
            val intent=Intent(context,MainActivity::class.java).setData(Uri.parse("whereweare://location-request/$id")).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val open=PendingIntent.getActivity(context,id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if(!entry.preferences().markMeetingSeen(user,"location-request:$id")) return Result.success()
            manager.notify("location-request",id.hashCode(),NotificationCompat.Builder(context,"location-requests")
                .setSmallIcon(R.drawable.ic_location).setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.location_request_received,request.sender_name)).setContentIntent(open).setAutoCancel(true).build())
            Result.success()
        } catch(e: CancellationException) {throw e}
        catch(_: Exception) {if(runAttemptCount<5) Result.retry() else Result.failure()}
    }
}
