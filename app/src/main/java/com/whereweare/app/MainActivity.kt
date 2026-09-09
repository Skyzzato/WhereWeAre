package com.whereweare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.whereweare.app.ui.*
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.status.SessionStatus

@AndroidEntryPoint class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF147D73),secondary=Color(0xFF4F635F),tertiary=Color(0xFF52618C))) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App(auth: AuthViewModel=hiltViewModel()) {
    val session by auth.session.collectAsStateWithLifecycle()
    when(session) {
        is SessionStatus.Initializing -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        is SessionStatus.Authenticated -> key((session as SessionStatus.Authenticated).session.user?.id) {
            val nav=rememberNavController()
            val backStack by nav.currentBackStackEntryAsState()
            val destinations=listOf("map" to R.string.map,"people" to R.string.people,"settings" to R.string.settings)
            Scaffold(topBar={ TopAppBar(title={ Text(stringResource(R.string.app_name)) }) },bottomBar={
                NavigationBar { destinations.forEach { (route,label) ->
                    NavigationBarItem(selected=(backStack?.destination?.route ?: "map")==route,
                        onClick={ nav.navigate(route) { popUpTo(nav.graph.startDestinationId) { saveState=true }; launchSingleTop=true; restoreState=true } },
                        icon={ Text(when(route) { "map" -> "◎"; "people" -> "♧"; else -> "⚙" }) },label={ Text(stringResource(label)) })
                } }
            }) { padding ->
                NavHost(nav,startDestination="map",modifier=Modifier.padding(padding)) {
                    composable("map") { MapScreen(hiltViewModel()) }
                    composable("people") { PeopleScreen(hiltViewModel()) }
                    composable("settings") { SettingsScreen(hiltViewModel()) }
                }
            }
        }
        else -> Box(Modifier.safeDrawingPadding()) { AuthScreen(auth) }
    }
}
