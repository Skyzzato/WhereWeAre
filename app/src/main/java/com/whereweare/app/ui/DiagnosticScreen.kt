package com.whereweare.app.ui

import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whereweare.app.R
import com.whereweare.app.data.NetworkTransport
import com.whereweare.app.domain.LocationPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable private fun DiagnosticRow(label: Int,value: String) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(stringResource(label),style=MaterialTheme.typography.labelLarge)
        Text(value,style=MaterialTheme.typography.bodyLarge)
    }
}

@Composable fun DiagnosticScreen(vm: SettingsViewModel,location: Boolean,close: ()->Unit) {
    val clock=remember {flow {while(true) {emit(Instant.now());delay(1_000)}}}
    val now by clock.collectAsStateWithLifecycle(initialValue=Instant.now())
    val tracking by vm.trackingDiagnostics.collectAsStateWithLifecycle()
    val foreground by vm.foregroundDiagnostics.collectAsStateWithLifecycle()
    val connection by vm.connectionDiagnostics.collectAsStateWithLifecycle()
    val network by vm.networkDiagnostics.collectAsStateWithLifecycle()
    val stop by vm.pendingStopDiagnostics.collectAsStateWithLifecycle(initialValue=null)
    val high by vm.highAccuracy.collectAsStateWithLifecycle()
    val unavailable=stringResource(R.string.diag_unavailable)
    val yes=stringResource(R.string.yes)
    val no=stringResource(R.string.no)
    fun bool(value: Boolean?)=when(value) {true->yes;false->no;null->unavailable}
    fun timestamp(value: Instant?)=value?.let {DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it)} ?: unavailable
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(if(location) R.string.diag_location else R.string.diag_connection),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    IconButton(onClick=close) {Icon(Icons.Default.Close,stringResource(R.string.diag_close))}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(location) {
                        val details by vm.location.details.collectAsStateWithLifecycle()
                        val permission=remember(now) {vm.location.permission()}
                        val enabled=remember(now) {vm.location.enabled()}
                        val satellites=remember(permission) {vm.location.satellites()}
                        val observedSatellites by satellites.collectAsStateWithLifecycle(initialValue=null)
                        val freshSatellites=observedSatellites?.takeIf {Duration.between(it.observedAt,now).seconds in 0..10 && enabled}
                        val own=details?.takeIf {it.fix.userId==vm.userId}
                        val fix=own?.fix ?: tracking.fix?.takeIf {it.userId==vm.userId}
                        val context=LocalContext.current
                        val batteryExempt=remember(now) {context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)}
                        DiagnosticRow(R.string.diag_location_services,bool(enabled))
                        DiagnosticRow(R.string.diag_permission,stringResource(when(permission) {
                            LocationPermission.NONE->R.string.diag_permission_none
                            LocationPermission.APPROXIMATE->R.string.diag_permission_approximate
                            LocationPermission.PRECISE->R.string.diag_permission_precise
                        }))
                        DiagnosticRow(R.string.diag_mode,stringResource(if(high && permission==LocationPermission.PRECISE) R.string.high_accuracy else R.string.balanced))
                        DiagnosticRow(R.string.diag_last_fix,timestamp(fix?.recordedAt))
                        DiagnosticRow(R.string.diag_fix_age,fix?.let {stringResource(R.string.diag_seconds,Duration.between(it.recordedAt,now).seconds.coerceAtLeast(0))} ?: unavailable)
                        DiagnosticRow(R.string.diag_accuracy,fix?.let {"±${it.accuracy.toLong()} m"} ?: unavailable)
                        DiagnosticRow(R.string.diag_coordinates,fix?.let {String.format(Locale.US,"%.6f, %.6f",it.latitude,it.longitude)} ?: unavailable)
                        DiagnosticRow(R.string.diag_altitude,own?.altitude?.let {String.format(Locale.getDefault(),"%.1f m",it)} ?: unavailable)
                        DiagnosticRow(R.string.diag_speed,fix?.speed?.let {String.format(Locale.getDefault(),"%.1f m/s",it)} ?: unavailable)
                        DiagnosticRow(R.string.diag_bearing,fix?.bearing?.let {String.format(Locale.getDefault(),"%.1f°",it)} ?: unavailable)
                        DiagnosticRow(R.string.diag_provider,own?.provider ?: unavailable)
                        DiagnosticRow(R.string.diag_satellites_visible,freshSatellites?.visible?.toString() ?: unavailable)
                        DiagnosticRow(R.string.diag_satellites_used,freshSatellites?.used?.toString() ?: unavailable)
                        DiagnosticRow(R.string.diag_last_sent,timestamp(tracking.lastSent))
                        DiagnosticRow(R.string.diag_last_ack,timestamp(tracking.lastAcknowledged))
                        DiagnosticRow(R.string.diag_sharing,bool(tracking.active))
                        DiagnosticRow(R.string.diag_foreground,bool(foreground))
                        DiagnosticRow(R.string.diag_recovery,bool(tracking.recovering && tracking.starting))
                        DiagnosticRow(R.string.diag_battery_optimized,bool(!batteryExempt))
                        Text(stringResource(R.string.diag_location_note),style=MaterialTheme.typography.bodySmall)
                    } else {
                        val operation by vm.operation.collectAsStateWithLifecycle()
                        val testResult by vm.diagnosticTestResult.collectAsStateWithLifecycle()
                        DiagnosticRow(R.string.diag_internet,bool(network.online))
                        DiagnosticRow(R.string.diag_network,stringResource(when(network.transport) {
                            NetworkTransport.NONE->R.string.diag_network_none
                            NetworkTransport.WIFI->R.string.diag_network_wifi
                            NetworkTransport.MOBILE->R.string.diag_network_mobile
                            NetworkTransport.ETHERNET->R.string.diag_network_ethernet
                            NetworkTransport.VPN->R.string.diag_network_vpn
                            NetworkTransport.OTHER->R.string.diag_network_other
                        }))
                        DiagnosticRow(R.string.diag_server,if(connection.serverReachable==null) stringResource(R.string.connection_checking) else bool(connection.serverReachable))
                        connection.failure?.let {Text(stringResource(when(it) {"session" -> R.string.error_auth;"forbidden" -> R.string.error_forbidden;"service" -> R.string.connection_service;else -> R.string.connection_unreachable}))}
                        DiagnosticRow(R.string.diag_session,bool(connection.sessionVerified))
                        DiagnosticRow(R.string.diag_realtime,bool(connection.realtimeConnected))
                        DiagnosticRow(R.string.diag_last_write,timestamp(connection.lastWrite))
                        DiagnosticRow(R.string.diag_last_read,timestamp(connection.lastRead))
                        DiagnosticRow(R.string.diag_latency,connection.latencyMillis?.let {"$it ms"} ?: unavailable)
                        DiagnosticRow(R.string.diag_in_flight,connection.inFlight.toString())
                        DiagnosticRow(R.string.diag_upload_pending,bool(tracking.pendingUpload))
                        DiagnosticRow(R.string.diag_stop_pending,bool(stop!=null))
                        Button(onClick=vm::testConnection,enabled=!operation.busy,modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.diag_run_test))}
                        if(operation.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        testResult?.let {Text(stringResource(it),style=MaterialTheme.typography.bodyLarge)}
                        Text(stringResource(R.string.diag_connection_note),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
