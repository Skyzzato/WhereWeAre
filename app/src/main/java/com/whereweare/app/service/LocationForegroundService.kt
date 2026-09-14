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
import javax.inject.Inject

@AndroidEntryPoint class LocationForegroundService: Service() {
    @Inject lateinit var controller: SharingController
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
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
        val notification=NotificationCompat.Builder(this,"sharing").setSmallIcon(R.drawable.ic_location)
            .setContentTitle(localizedString(R.string.app_name)).setContentText(localizedString(R.string.sharing_active))
            .setContentIntent(open).setOngoing(true).addAction(0,localizedString(R.string.stop),stop).build()
        try {
            ServiceCompat.startForeground(this,10,notification,if(Build.VERSION.SDK_INT>=29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            controller.attach(scope,restarting=intent==null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        } catch(_: SecurityException) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
