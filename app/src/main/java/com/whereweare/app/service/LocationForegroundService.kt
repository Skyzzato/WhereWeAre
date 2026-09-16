package com.whereweare.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.whereweare.app.MainActivity
import com.whereweare.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@AndroidEntryPoint class LocationForegroundService: Service() {
    @Inject lateinit var controller: SharingController
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var notificationUpdates: Job?=null
    private fun localizedString(id: Int)=com.whereweare.app.ui.localizedContext(this,getSharedPreferences("appearance",0).getString("language","system") ?: "system").getString(id)
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("sharing",localizedString(R.string.sharing_channel),NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="STOP") { controller.requestStop(); return START_NOT_STICKY }
        val stop=PendingIntent.getService(this,1,Intent(this,LocationForegroundService::class.java).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun notification(text: Int)=NotificationCompat.Builder(this,"sharing").setSmallIcon(R.drawable.ic_location)
            .setContentTitle(localizedString(R.string.app_name)).setContentText(localizedString(text))
            .setContentIntent(open).setOngoing(true).addAction(0,localizedString(R.string.stop),stop).build()
        try {
            ServiceCompat.startForeground(this,10,notification(R.string.sharing_starting),if(Build.VERSION.SDK_INT>=29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            controller.foregroundService.value=true
            controller.attach(scope,restarting=intent==null || intent.action=="RESUME") { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            notificationUpdates?.cancel()
            notificationUpdates=scope.launch {
                controller.state.map { when {
                    it.waiting -> com.whereweare.app.ui.trackingFailureMessage(it)
                    it.active -> R.string.sharing_active
                    it.starting -> R.string.sharing_starting
                    else -> null
                } }.distinctUntilChanged().collect { text ->
                    if(text!=null) getSystemService(NotificationManager::class.java).notify(10,notification(text))
                }
            }
        } catch(_: SecurityException) { controller.serviceStartFailed();stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }
    override fun onDestroy() { controller.foregroundService.value=false; scope.cancel(); super.onDestroy() }
}
