package com.whereweare.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.whereweare.app.R
import com.whereweare.app.domain.normalizeInviteCode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

fun cameraAccessGranted(context: Context)=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED

@Composable fun QrDisplay(type: String,code: String,close: ()->Unit) {
    val bitmap=remember(type,code) {qrInviteBitmap(type,code).asImageBitmap()}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        val lifecycle=LocalLifecycleOwner.current.lifecycle
        DisposableEffect(window,lifecycle) {
            val state=window?.let(::QrWindowState)
            val observer=LifecycleEventObserver {_,event ->
                if(event==Lifecycle.Event.ON_RESUME) state?.show()
                if(event==Lifecycle.Event.ON_PAUSE || event==Lifecycle.Event.ON_STOP) state?.restore()
            }
            lifecycle.addObserver(observer)
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) state?.show()
            onDispose {lifecycle.removeObserver(observer);state?.restore()}
        }
        Surface(Modifier.fillMaxSize(),color=Color.White,contentColor=Color.Black) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(if(type=="person") R.string.qr_person else R.string.qr_group),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    IconButton(onClick=close) {Icon(Icons.Default.Close,stringResource(R.string.close),tint=Color.Black)}
                }
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Image(bitmap,stringResource(R.string.qr_image_description),Modifier.fillMaxWidth().aspectRatio(1f))
                }
                Text(normalizeInviteCode(code),style=MaterialTheme.typography.headlineLarge,color=Color.Black)
                Text(stringResource(R.string.qr_show_hint),Modifier.padding(vertical=20.dp),color=Color.Black)
            }
        }
    }
}

@Composable fun QrScanner(onInvite: (String,String)->Boolean,close: ()->Unit) {
    val context=LocalContext.current
    var granted by remember {mutableStateOf(cameraAccessGranted(context))}
    var error by remember {mutableStateOf<Int?>(null)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted=it;if(!it) error=R.string.qr_camera_permission}
    LifecycleResumeEffect(Unit) {granted=cameraAccessGranted(context);onPauseOrDispose {}}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.qr_scan),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    IconButton(onClick=close) {Icon(Icons.Default.Close,stringResource(R.string.close))}
                }
                Text(stringResource(R.string.qr_scan_hint))
                if(granted) QrCameraPreview(onInvite,{error=it},Modifier.weight(1f).fillMaxWidth())
                else {
                    Text(stringResource(R.string.qr_camera_permission))
                    Button(onClick={permission.launch(Manifest.permission.CAMERA)}) {Text(stringResource(R.string.qr_allow_camera))}
                    TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri()))}) {Text(stringResource(R.string.qr_open_settings))}
                    Spacer(Modifier.weight(1f))
                }
                error?.let {Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
                Text(stringResource(R.string.qr_offline),style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun QrCameraPreview(onInvite: (String,String)->Boolean,onError: (Int)->Unit,modifier: Modifier) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val controller=remember(context) {LifecycleCameraController(context)}
    val accept by rememberUpdatedState(onInvite)
    val reportError by rememberUpdatedState(onError)
    DisposableEffect(controller,lifecycle) {
        val active=AtomicBoolean(true)
        val delivered=AtomicBoolean(false)
        val executor=Executors.newSingleThreadExecutor()
        val main=ContextCompat.getMainExecutor(context)
        var lastAttempt=0L
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(executor) {image ->
            try {
                val time=SystemClock.elapsedRealtime()
                if(active.get() && !delivered.get() && time-lastAttempt>=250) {
                    lastAttempt=time
                    val plane=image.planes[0]
                    val bytes=qrLuminancePlane(plane.buffer,image.width,image.height,plane.rowStride,plane.pixelStride)
                    val text=decodeQrLuminance(bytes,image.width,image.height)
                    if(text!=null) {
                        val invite=parseQrInvite(text)
                        if(invite==null) main.execute {if(active.get()) reportError(R.string.qr_invalid)}
                        else if(delivered.compareAndSet(false,true)) main.execute {
                            if(active.get() && lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                if(!accept(invite.first,invite.second)) {reportError(R.string.qr_save_failed);delivered.set(false)}
                            } else delivered.set(false)
                        }
                    }
                }
            } catch(_: IllegalArgumentException) { /* Unsupported image layout: close and wait for a usable frame. */ }
            finally {image.close()}
        }
        try {
            controller.bindToLifecycle(lifecycle)
            controller.initializationFuture.addListener({
                try {controller.initializationFuture.get()}
                catch(_: Exception) {if(active.get()) reportError(R.string.qr_camera_unavailable)}
            },main)
        } catch(_: Exception) {reportError(R.string.qr_camera_unavailable)}
        onDispose {
            active.set(false)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor.shutdown()
        }
    }
    AndroidView(factory={PreviewView(it).apply {this.controller=controller}},modifier=modifier)
}
