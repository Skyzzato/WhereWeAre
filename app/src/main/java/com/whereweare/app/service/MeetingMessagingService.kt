package com.whereweare.app.service

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.*
import com.whereweare.app.*
import com.whereweare.app.R
import com.whereweare.app.data.*
import com.whereweare.app.ui.localizedContext
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.*
import javax.inject.*

class MeetingMessagingService: FirebaseMessagingService() {
    override fun onNewToken(token: String) {PushRegistration.enqueue(this)}
    override fun onMessageReceived(message: RemoteMessage) {
        val id=message.data["meeting_id"] ?: return
        if(!id.matches(Regex("[0-9a-f-]{36}"))) return
        WorkManager.getInstance(this).enqueueUniqueWork("meeting-${message.messageId ?: id}",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MeetingNotificationWorker>().setInputData(workDataOf("meeting_id" to id,"recipient" to message.data["recipient"],"kind" to message.data["kind"]))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build())
    }
}

@Singleton class PushRegistration @Inject constructor(@ApplicationContext private val context: Context,private val auth: AuthRepository,private val client: SupabaseClient) {
    suspend fun unregister() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {WorkManager.getInstance(context).cancelUniqueWork("push-registration").result.get()}
        if(FirebaseApp.getApps(context).isEmpty() || auth.userId==null) return
        val token=FirebaseMessaging.getInstance().token.await()
        client.postgrest.rpc("unregister_device",buildJsonObject {put("device_token",token)})
    }
    companion object {fun enqueue(context: Context) {
        if(FirebaseApp.getApps(context).isEmpty()) return
        WorkManager.getInstance(context).enqueueUniqueWork("push-registration",ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PushRegistrationWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }}
}
class PushRegistrationWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val entry=dagger.hilt.android.EntryPointAccessors.fromApplication(applicationContext,PushDependencies::class.java)
        return try {
            entry.auth().awaitSession();if(entry.auth().userId==null || FirebaseApp.getApps(applicationContext).isEmpty()) return Result.success()
            val lang=entry.preferences().language.first()
            entry.client().postgrest.rpc("register_device",buildJsonObject {put("device_token",FirebaseMessaging.getInstance().token.await());put("lang",localizedContext(applicationContext,lang).resources.configuration.locales[0].language)})
            Result.success()
        } catch(e: kotlinx.coroutines.CancellationException) {throw e} catch(_: Exception) {Result.retry()}
    }
}
class MeetingNotificationWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val entry=dagger.hilt.android.EntryPointAccessors.fromApplication(applicationContext,PushDependencies::class.java)
        return try {
            entry.auth().awaitSession();val user=entry.auth().userId ?: return Result.success()
            if(user!=inputData.getString("recipient")) return Result.success()
            val id=inputData.getString("meeting_id") ?: return Result.success()
            val points=entry.client().postgrest.rpc("meeting_inbox").decodeList<com.whereweare.app.domain.MeetingPoint>()
            val point=points.firstOrNull {it.id==id} ?: return Result.success()
            if(MainActivity.visible || entry.auth().userId!=user) return Result.success()
            val context=localizedContext(applicationContext,entry.preferences().language.first())
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return Result.success()
            val manager=context.getSystemService(NotificationManager::class.java)
            val channelId="meetings-v032"
            val channel=NotificationChannel(channelId,context.getString(R.string.meeting_channel),NotificationManager.IMPORTANCE_DEFAULT)
            channel.setSound(android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.flare_notification}"),android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
            manager.createNotificationChannel(channel)
            val intent=Intent(context,MainActivity::class.java).setData(android.net.Uri.parse("whereweare://meeting/$id")).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val open=PendingIntent.getActivity(context,id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if(!entry.preferences().markMeetingSeen(user,"push:$id:${point.active}")) return Result.success()
            manager.notify(id.hashCode(),NotificationCompat.Builder(context,channelId).setSmallIcon(R.drawable.ic_location).setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(if(point.active) R.string.meeting_created else R.string.meeting_removed,point.creator_name)).setContentIntent(open).setAutoCancel(true).build())
            Result.success()
        } catch(e: kotlinx.coroutines.CancellationException) {throw e} catch(_: Exception) {if(runAttemptCount<5) Result.retry() else Result.failure()}
    }
}
@dagger.hilt.EntryPoint @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PushDependencies {fun auth(): AuthRepository;fun client(): SupabaseClient;fun preferences(): PreferencesRepository}
