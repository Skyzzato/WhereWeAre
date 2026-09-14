package com.whereweare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.*
import com.whereweare.app.data.BootstrapGate
import com.whereweare.app.ui.*
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.status.SessionStatus

@AndroidEntryPoint class MainActivity: ComponentActivity() {
    private var link by mutableStateOf<android.net.Uri?>(null)
    override fun onStart() {super.onStart(); visible=true}
    override fun onStop() {visible=false;super.onStop()}
    companion object {@Volatile var visible=false; private set}
    override fun onNewIntent(intent: android.content.Intent) {super.onNewIntent(intent);setIntent(intent);link=intent.data ?: intent.getStringExtra("meeting_id")?.let {android.net.Uri.parse("whereweare://meeting/$it")}}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        link=intent.data ?: intent.getStringExtra("meeting_id")?.let {android.net.Uri.parse("whereweare://meeting/$it")}
        setContent {
            val appearance: AppearanceViewModel=hiltViewModel()
            val theme by appearance.theme.collectAsStateWithLifecycle()
            val language by appearance.language.collectAsStateWithLifecycle()
            val context=remember(language) {localizedActivityContext(this@MainActivity,language)}
            val stringsContext=remember(language) {localizedContext(applicationContext,language)}
            SideEffect {Strings.configure(stringsContext)}
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides context.resources.configuration) {
                MaterialTheme(colorScheme=appColors(theme)) {Surface(Modifier.fillMaxSize()) {App(appearance=appearance,link=link,linkHandled={link=null})}}
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App(auth: AuthViewModel=hiltViewModel(),bootstrap: BootstrapViewModel=hiltViewModel(),appearance: AppearanceViewModel,link: android.net.Uri?,linkHandled: ()->Unit) {
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycle,appearance) { lifecycle.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {appearance.observeMeetings()} }
    val session by auth.session.collectAsStateWithLifecycle()
    val boot by bootstrap.repository.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { bootstrap.refresh(); onPauseOrDispose {} }
    if(boot.gate!=BootstrapGate.READY || session is SessionStatus.Initializing) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Image(painterResource(R.drawable.ic_location),null,Modifier.padding(vertical=12.dp).sizeIn(maxWidth=160.dp,maxHeight=160.dp).size(96.dp),contentScale=ContentScale.Fit)
            Text("WhereWeAre",style=MaterialTheme.typography.headlineLarge)
            Text("v${BuildConfig.VERSION_NAME}")
            Spacer(Modifier.height(24.dp))
            when(boot.gate) {
                BootstrapGate.UPDATE -> { Text(Strings.text(R.string.ui_120),style=MaterialTheme.typography.headlineSmall); Text(Strings.text(R.string.ui_121)) }
                BootstrapGate.MAINTENANCE -> { Text(Strings.text(R.string.ui_122),style=MaterialTheme.typography.headlineSmall); boot.config?.maintenance_message?.let { Text(it) } }
                BootstrapGate.FIRST_CONNECTION -> Text(Strings.text(R.string.ui_123))
                BootstrapGate.BACKEND_UPDATE -> Text(Strings.text(R.string.backend_update))
                else -> CircularProgressIndicator()
            }
            if(boot.gate !in listOf(BootstrapGate.LOADING,BootstrapGate.READY)) TextButton(onClick=bootstrap::refresh) { Text(Strings.text(R.string.ui_124)) }
        }
        return
    }
    if(session !is SessionStatus.Authenticated) { Box(Modifier.safeDrawingPadding()) { AuthScreen(auth) }; return }
    val invite=remember(link) {parseTypedInvite(link)}
    val notificationPermission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    var notificationAsked by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    val appContext=LocalContext.current
    LaunchedEffect(session) {
        if(!notificationAsked && android.os.Build.VERSION.SDK_INT>=33 && com.google.firebase.FirebaseApp.getApps(appContext).isNotEmpty()) {
            notificationAsked=true
            if(androidx.core.content.ContextCompat.checkSelfPermission(appContext,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    key((session as SessionStatus.Authenticated).session.user?.id) {
        val nav=rememberNavController()
        val user=(session as SessionStatus.Authenticated).session.user?.id
        val stack by nav.currentBackStackEntryAsState()
        // Wait for NavHost to attach its graph; never navigate from an early startup effect.
        LifecycleResumeEffect(user,stack?.destination?.route) {
            if(user!=null && stack!=null && stack?.destination?.route!="settings" && (appearance.avatarDrafts.pending(user)?.length() ?: 0)>0)
                nav.navigate("settings") {launchSingleTop=true;restoreState=true}
            onPauseOrDispose {}
        }
        var focus by remember {mutableStateOf<MapFocus?>(null)}
        val notice by appearance.notification.collectAsStateWithLifecycle()
        val flare by appearance.flare.collectAsStateWithLifecycle()
        val flareSound by appearance.flareSound.collectAsStateWithLifecycle()
        val invitation by appearance.invite.collectAsStateWithLifecycle()
        var inviteToken by remember {mutableStateOf<String?>(null)}
        LaunchedEffect(invite) {
            invite?.let { nav.navigate(if(it.first=="person") "people" else "groups") {launchSingleTop=true}; linkHandled() }
        }
        LaunchedEffect(link) {
            if(link?.scheme=="whereweare" && link.host=="meeting") {focus=MapFocus(meeting=link.lastPathSegment);nav.navigate("map") {launchSingleTop=true};linkHandled()}
            else if(link?.scheme=="https" && link.host=="whereweare.app" && link.pathSegments.firstOrNull()=="join") {
                val token=link.lastPathSegment.orEmpty()
                if(token.matches(Regex("[0-9a-f]{64}"))) {inviteToken=token;appearance.resolve(token)}
                linkHandled()
            }
        }
        val routes=listOf("map","people","groups","settings")
        val labels=listOf(Strings.text(R.string.map),Strings.text(R.string.people),Strings.text(R.string.ui_119),Strings.text(R.string.settings))
        val icons=listOf(Icons.Default.Map,Icons.Default.People,Icons.Default.Groups,Icons.Default.Settings)
        Box(Modifier.fillMaxSize()) { Scaffold(topBar={ Column {TopAppBar(title={ Row(verticalAlignment=Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_location),null,Modifier.size(28.dp)); Spacer(Modifier.width(8.dp)); Text("WhereWeAre")
        } });notice?.let {point -> Surface(onClick={if(point.active) {focus=MapFocus(meeting=point.id);nav.navigate("map") {launchSingleTop=true}};appearance.notification.value=null},color=MaterialTheme.colorScheme.primaryContainer) {
            Text(if(point.active) Strings.text(R.string.ui_125, (point.creator_name).toString()) else Strings.text(R.string.ui_126, (point.creator_name).toString()),Modifier.fillMaxWidth().padding(12.dp))
        }} } },bottomBar={ NavigationBar { routes.forEachIndexed { index,route ->
            NavigationBarItem(selected=stack?.destination?.route==route,onClick={ nav.navigate(route) {
                popUpTo(nav.graph.startDestinationId) { saveState=true }; launchSingleTop=true; restoreState=true
            } },icon={ Icon(icons[index],labels[index]) },label={ Text(labels[index]) })
        } } }) { padding ->
            NavHost(nav,startDestination="map",modifier=Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background),
                enterTransition={ EnterTransition.None },exitTransition={ ExitTransition.None },popEnterTransition={ EnterTransition.None },popExitTransition={ ExitTransition.None }) {
                composable("map") { MapScreen(hiltViewModel(),focus,{focus=null}) }
                composable("people") { Surface(Modifier.fillMaxSize()) { PeopleScreen(hiltViewModel(),onShow={id -> focus=MapFocus(person=id);nav.navigate("map") {launchSingleTop=true}},initialCode=invite?.takeIf {it.first=="person"}?.second) } }
                composable("groups") { Surface(Modifier.fillMaxSize()) { GroupsScreen(hiltViewModel(),initialCode=invite?.takeIf {it.first=="group"}?.second) } }
                composable("settings") { Surface(Modifier.fillMaxSize()) { SettingsScreen(hiltViewModel(),onPrivacy={ nav.navigate("privacy") }) } }
                composable("privacy") { Surface(Modifier.fillMaxSize()) { PrivacyScreen { nav.popBackStack() } } }
            }
        }
        FlareAnimation(flare?.id?.removeSuffix(":created"),flare?.styleId ?: 1,flareSound) {appearance.finishFlare(flare)}
        }
        invitation?.let {details -> AlertDialog(onDismissRequest={appearance.invite.value=null},title={Text(Strings.text(R.string.ui_127))},text={Text(details["name"].toString().trim('"'))},confirmButton={TextButton(onClick={inviteToken?.let {appearance.resolve(it,true)}}) {Text(Strings.text(R.string.send_request))}},dismissButton={TextButton(onClick={appearance.invite.value=null}) {Text(Strings.text(R.string.ui_006))}}) }
    }
}
