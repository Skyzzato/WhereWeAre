package com.whereweare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.compose.*
import com.whereweare.app.data.BootstrapGate
import com.whereweare.app.ui.*
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.status.SessionStatus

@AndroidEntryPoint class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF147D73),secondary=Color(0xFF4F635F),tertiary=Color(0xFF52618C))) {
            Surface(Modifier.fillMaxSize()) { App() }
        } }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App(auth: AuthViewModel=hiltViewModel(),bootstrap: BootstrapViewModel=hiltViewModel()) {
    val session by auth.session.collectAsStateWithLifecycle()
    val boot by bootstrap.repository.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { bootstrap.refresh(); onPauseOrDispose {} }
    if(boot.gate!=BootstrapGate.READY || session is SessionStatus.Initializing) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Image(painterResource(R.drawable.ic_location),null,Modifier.size(80.dp))
            Text("WhereWeAre",style=MaterialTheme.typography.headlineLarge)
            Text("v${BuildConfig.VERSION_NAME}")
            Spacer(Modifier.height(24.dp))
            when(boot.gate) {
                BootstrapGate.UPDATE -> { Text("Aggiornamento necessario",style=MaterialTheme.typography.headlineSmall); Text("Questa versione di WhereWeAre non è più supportata. Aggiorna l'app per continuare a utilizzarla.") }
                BootstrapGate.MAINTENANCE -> { Text("WhereWeAre è temporaneamente in manutenzione",style=MaterialTheme.typography.headlineSmall); boot.config?.maintenance_message?.let { Text(it) } }
                BootstrapGate.FIRST_CONNECTION -> Text("È necessaria una prima connessione per verificare la compatibilità dell'app. Controlla la rete e riprova.")
                else -> CircularProgressIndicator()
            }
            if(boot.gate !in listOf(BootstrapGate.LOADING,BootstrapGate.READY)) TextButton(onClick=bootstrap::refresh) { Text("Riprova") }
        }
        return
    }
    if(session !is SessionStatus.Authenticated) { Box(Modifier.safeDrawingPadding()) { AuthScreen(auth) }; return }
    key((session as SessionStatus.Authenticated).session.user?.id) {
        val nav=rememberNavController()
        val stack by nav.currentBackStackEntryAsState()
        val routes=listOf("map","people","groups","settings")
        val labels=listOf("Mappa","Persone","Gruppi","Impostazioni")
        val icons=listOf(Icons.Default.Map,Icons.Default.People,Icons.Default.Groups,Icons.Default.Settings)
        Scaffold(topBar={ TopAppBar(title={ Row(verticalAlignment=Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_location),null,Modifier.size(28.dp)); Spacer(Modifier.width(8.dp)); Text("WhereWeAre")
        } }) },bottomBar={ NavigationBar { routes.forEachIndexed { index,route ->
            NavigationBarItem(selected=stack?.destination?.route==route,onClick={ nav.navigate(route) {
                popUpTo(nav.graph.startDestinationId) { saveState=true }; launchSingleTop=true; restoreState=true
            } },icon={ Icon(icons[index],labels[index]) },label={ Text(labels[index]) })
        } } }) { padding ->
            NavHost(nav,startDestination="map",modifier=Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background),
                enterTransition={ EnterTransition.None },exitTransition={ ExitTransition.None },popEnterTransition={ EnterTransition.None },popExitTransition={ ExitTransition.None }) {
                composable("map") { MapScreen(hiltViewModel()) }
                composable("people") { Surface(Modifier.fillMaxSize()) { PeopleScreen(hiltViewModel()) } }
                composable("groups") { Surface(Modifier.fillMaxSize()) { GroupsScreen(hiltViewModel()) } }
                composable("settings") { Surface(Modifier.fillMaxSize()) { SettingsScreen(hiltViewModel(),onPrivacy={ nav.navigate("privacy") }) } }
                composable("privacy") { Surface(Modifier.fillMaxSize()) { PrivacyScreen { nav.popBackStack() } } }
            }
        }
    }
}
