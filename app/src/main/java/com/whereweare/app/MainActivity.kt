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
import androidx.compose.ui.graphics.luminance
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
    @javax.inject.Inject lateinit var nearbyConsent: com.whereweare.app.data.NearbySosRepository
    @javax.inject.Inject lateinit var inviteStore: com.whereweare.app.data.InviteStore
    private var invalidInvite by mutableStateOf(false)
    private fun receiveInvite(uri: android.net.Uri) {
        val intended=(uri.scheme=="whereweare" && uri.host in setOf("person","group")) || (uri.scheme=="https" && BuildConfig.INVITE_BASE_URL.isNotBlank() && uri.host==android.net.Uri.parse(BuildConfig.INVITE_BASE_URL).host && uri.pathSegments.firstOrNull()!="join")
        if(intended) invalidInvite=!inviteStore.accept(uri)
    }
    @javax.inject.Inject lateinit var nearby: com.whereweare.app.data.NearbySosRepository
    @javax.inject.Inject lateinit var sharing: com.whereweare.app.data.SharingRepository
    private var link by mutableStateOf<android.net.Uri?>(null)
    override fun onStart() {super.onStart(); visible=true;sharing.refresh()}
    override fun onStop() {visible=false;super.onStop()}
    companion object {@Volatile var visible=false; private set}
    override fun onNewIntent(intent: android.content.Intent) {super.onNewIntent(intent);setIntent(intent);intent.data?.let {receiveInvite(it)};link=intent.data ?: intent.getStringExtra("meeting_id")?.let {android.net.Uri.parse("whereweare://meeting/$it")}}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if(savedInstanceState==null) intent.data?.let {receiveInvite(it)}
        link=intent.data ?: intent.getStringExtra("meeting_id")?.let {android.net.Uri.parse("whereweare://meeting/$it")}
        setContent {
            val appearance: AppearanceViewModel=hiltViewModel()
            val theme by appearance.theme.collectAsStateWithLifecycle()
            val language by appearance.language.collectAsStateWithLifecycle()
            val context=remember(language) {localizedActivityContext(this@MainActivity,language)}
            val stringsContext=remember(language) {localizedContext(applicationContext,language)}
            LaunchedEffect(invalidInvite,language) {if(invalidInvite) {android.widget.Toast.makeText(stringsContext,stringsContext.getString(R.string.invalid_invite),android.widget.Toast.LENGTH_SHORT).show();invalidInvite=false}}
            SideEffect {Strings.configure(stringsContext)
                val bars=androidx.core.view.WindowCompat.getInsetsController(window,window.decorView)
                val light=appColors(theme).background.luminance()>.5f
                bars.isAppearanceLightStatusBars=light;bars.isAppearanceLightNavigationBars=light
            }
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
    val onboarding by appearance.onboarding.collectAsStateWithLifecycle()
    val online by appearance.sharing.online.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycle,boot.gate,session,onboarding,online) {
        if(online && onboarding && boot.gate==BootstrapGate.READY && session is SessionStatus.Authenticated) {
            lifecycle.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                appearance.controller.resumeFromVisibleActivity()
            }
        }
    }
    LifecycleResumeEffect(Unit) { bootstrap.refresh(); onPauseOrDispose {} }
    val onboardingOperation by appearance.operation.collectAsStateWithLifecycle()
    BootstrapBoundary(boot,session is SessionStatus.Initializing,bootstrap::refresh) {
    if(!onboarding) {OnboardingScreen(onboardingOperation,appearance::finishOnboarding);return@BootstrapBoundary}
    if(session !is SessionStatus.Authenticated) { Box(Modifier.safeDrawingPadding()) { AuthScreen(auth) }; return@BootstrapBoundary }
    val nearbyState by appearance.nearby.state.collectAsStateWithLifecycle()
    var receiveNearby by androidx.compose.runtime.saveable.rememberSaveable((session as SessionStatus.Authenticated).session.user?.id) {mutableStateOf(true)}
    if(nearbyState.status?.consent_initialized==false && nearbyState.pending==null) {
        AlertDialog(onDismissRequest={},title={Text(Strings.text(R.string.nearby_initial_title))},text={Column {
            Text(Strings.text(R.string.nearby_optin_explanation))
            Text(Strings.text(R.string.nearby_refresh_policy))
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(receiveNearby,{receiveNearby=it});Text(Strings.text(R.string.nearby_receive))}
            if(nearbyState.failed) Text(Strings.text(R.string.nearby_sync_failed))
        }},confirmButton={TextButton(onClick={appearance.confirmNearby(receiveNearby)}) {Text(Strings.text(R.string.nearby_confirm))}})
    }
    val invite by appearance.invites.pending.collectAsStateWithLifecycle()
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
            if(invite==null && user!=null && stack!=null && stack?.destination?.route!="settings" && (appearance.avatarDrafts.pending(user)?.length() ?: 0)>0)
                nav.navigate("settings") {launchSingleTop=true;restoreState=true}
            onPauseOrDispose {}
        }
        var focus by remember {mutableStateOf<MapFocus?>(null)}
        var scanning by remember {mutableStateOf(false)}
        val notice by appearance.notification.collectAsStateWithLifecycle()
        val eventNotice by appearance.eventNotice.collectAsStateWithLifecycle()
        val sharingSnapshot by appearance.sharing.state.collectAsStateWithLifecycle()
        val flare by appearance.flare.collectAsStateWithLifecycle()
        val flareSound by appearance.flareSound.collectAsStateWithLifecycle()
        val invitation by appearance.invite.collectAsStateWithLifecycle()
        var inviteToken by remember {mutableStateOf<String?>(null)}
        LaunchedEffect(invite?.id,stack!=null) {
            if(stack!=null) invite?.let { nav.navigate(if(it.type=="person") "people" else "groups") {launchSingleTop=true} }
        }
        LaunchedEffect(link) {
            if(link?.scheme=="whereweare" && link.host=="event") {focus=MapFocus(event=link.lastPathSegment);nav.navigate("map") {launchSingleTop=true};linkHandled()}
            else if(link?.scheme=="whereweare" && link.host=="location-request") {nav.navigate("people") {launchSingleTop=true};linkHandled()}
            else if(link?.scheme=="whereweare" && link.host=="meeting") {focus=MapFocus(meeting=link.lastPathSegment);nav.navigate("map") {launchSingleTop=true};linkHandled()}
            else if(link?.scheme=="https" && BuildConfig.INVITE_BASE_URL.isNotBlank() && link.host==android.net.Uri.parse(BuildConfig.INVITE_BASE_URL).host && link.pathSegments.firstOrNull()=="join") {
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
        } });if(sharingSnapshot.locationRequests.isNotEmpty() && stack?.destination?.route!="people") TextButton(onClick={nav.navigate("people") {launchSingleTop=true}}) {Text(Strings.text(R.string.location_request_pending,sharingSnapshot.locationRequests.size))}
        eventNotice?.let {event -> TextButton(onClick={focus=MapFocus(event=event.id);nav.navigate("map") {launchSingleTop=true};appearance.eventNotice.value=null}) {Text(eventReceivedText(event))}}
        notice?.let {point -> Surface(onClick={if(point.active || point.completed_at!=null) {focus=MapFocus(meeting=point.id);nav.navigate("map") {launchSingleTop=true}};appearance.notification.value=null},color=MaterialTheme.colorScheme.primaryContainer) {
            Text(if(point.completed_at!=null) Strings.text(R.string.flare_completed_notification,point.creator_name) else if(point.active) Strings.text(R.string.ui_125,point.creator_name) else Strings.text(R.string.ui_126,point.creator_name),Modifier.fillMaxWidth().padding(12.dp))
        }} } },bottomBar={ NavigationBar { routes.forEachIndexed { index,route ->
            NavigationBarItem(selected=stack?.destination?.route==route,onClick={ nav.navigate(route) {
                popUpTo(nav.graph.startDestinationId) { saveState=true }; launchSingleTop=true; restoreState=true
            } },icon={ Icon(icons[index],labels[index]) },label={ Text(labels[index]) })
        } } }) { padding ->
            NavHost(nav,startDestination="map",modifier=Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background),
                enterTransition={ EnterTransition.None },exitTransition={ ExitTransition.None },popEnterTransition={ EnterTransition.None },popExitTransition={ ExitTransition.None }) {
                composable("map") { MapScreen(hiltViewModel(),focus,{focus=null},onPlaces={nav.navigate("places")}) }
                composable("people") { Surface(Modifier.fillMaxSize()) {val current=invite?.takeIf {it.type=="person"};PeopleScreen(hiltViewModel(),onShow={id -> focus=MapFocus(person=id);nav.navigate("map") {launchSingleTop=true}},initialCode=current?.code,inviteId=current?.id,inviteHandled={current?.let {appearance.invites.consume(it.id)}},onScan={scanning=true}) } }
                composable("groups") { Surface(Modifier.fillMaxSize()) {val current=invite?.takeIf {it.type=="group"}; GroupsScreen(hiltViewModel(),initialCode=current?.code,inviteId=current?.id,inviteHandled={current?.let {appearance.invites.consume(it.id)}},onScan={scanning=true}) } }
                composable("settings") { Surface(Modifier.fillMaxSize()) { SettingsScreen(hiltViewModel(),onCenterPlace={place -> focus=MapFocus(place=place);nav.navigate("map") {popUpTo("map") {inclusive=false};launchSingleTop=true}},onPrivacy={ nav.navigate("privacy") }) } }
                composable("places") { PlacesScreen(hiltViewModel<SettingsViewModel>(),onCenter={place -> focus=MapFocus(place=place);nav.navigate("map") {popUpTo("map") {inclusive=false};launchSingleTop=true}}) {nav.popBackStack()} }
                composable("privacy") { Surface(Modifier.fillMaxSize()) { PrivacyScreen { nav.popBackStack() } } }
            }
        }
        FlareAnimation(flare?.id?.removeSuffix(":created"),flare?.styleId ?: com.whereweare.app.domain.FlareStyles.DEFAULT_ID,flareSound) {appearance.finishFlare(flare)}
        if(scanning) QrScanner(onInvite={type,code -> appearance.invites.accept(android.net.Uri.parse(qrInvitePayload(type,code))).also {if(it) scanning=false}},close={scanning=false})
        }
        invitation?.let {details -> AlertDialog(onDismissRequest={appearance.invite.value=null},title={Text(Strings.text(R.string.ui_127))},text={Text(details["name"].toString().trim('"'))},confirmButton={TextButton(onClick={inviteToken?.let {appearance.resolve(it,true)}}) {Text(Strings.text(R.string.send_request))}},dismissButton={TextButton(onClick={appearance.invite.value=null}) {Text(Strings.text(R.string.ui_006))}}) }
    }
}
}

// Keep all onboarding, authentication and navigation content behind the server gate.
@Composable internal fun BootstrapBoundary(boot: com.whereweare.app.data.BootstrapState,initializing: Boolean,retry: ()->Unit,content: @Composable ()->Unit) {
    if(boot.gate!=BootstrapGate.READY || initializing) {
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
            if(boot.gate !in listOf(BootstrapGate.LOADING,BootstrapGate.READY)) TextButton(onClick=retry) { Text(Strings.text(R.string.ui_124)) }
        }
        return
    }
    content()
}
