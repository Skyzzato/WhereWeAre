package com.whereweare.app.ui

import android.content.*
import android.media.*
import android.os.PowerManager
import com.whereweare.app.R
import com.whereweare.app.data.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One application player. No service, wake lock, or dependency from the location engine. */
@Singleton class BackgroundMusic @Inject constructor(@ApplicationContext private val context: Context,preferences: PreferencesRepository) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val audio=context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer?=null
    private var enabled=false
    private var foreground=false
    private var interrupted=false
    private var position=0
    private var focusHeld=false
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true).setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener {change ->
            if(change==AudioManager.AUDIOFOCUS_GAIN) {focusHeld=true;if(!interrupted) update()}
            else {interrupted=true;release()}
        }.build()
    private val receiver=object: BroadcastReceiver() {
        override fun onReceive(c: Context?,intent: Intent?) {
            if(intent?.action in setOf(AudioManager.ACTION_AUDIO_BECOMING_NOISY,Intent.ACTION_SCREEN_OFF)) {interrupted=true;release()}
        }
    }
    init {
        androidx.core.content.ContextCompat.registerReceiver(context,receiver,IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);addAction(Intent.ACTION_SCREEN_OFF)
        },androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {preferences.backgroundMusic.collectLatest {enabled=it;if(it) interrupted=false;update()}}
        scope.launch {AudioPriority.state.collectLatest {update()}}
        // Observe effects and other playback without ever acquiring location or keeping the CPU awake.
        scope.launch {while(true) {delay(1_000);if(foreground) update()}}
    }
    fun visible(value: Boolean) {foreground=value;if(value) interrupted=false;update()}
    private fun update() {
        val interactive=context.getSystemService(PowerManager::class.java).isInteractive
        val effect=AudioPriority.state.value>0
        if(!enabled || !foreground || !interactive || interrupted || effect || audio.mode!=AudioManager.MODE_NORMAL) {release();return}
        if(player!=null) return
        // Do not duck or interrupt an existing music player for decorative audio.
        if(audio.isMusicActive) return
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        focusHeld=true
        try {
            player=MediaPlayer.create(context,R.raw.lounge,attributes,0)?.apply {
                isLooping=true;setVolume(.08f,.08f);seekTo(position)
                setOnErrorListener {_,_,_ -> interrupted=true;release();true}
                start()
            }
            if(player==null) release()
        } catch(_: Exception) {release()}
    }
    private fun release() {
        player?.let {position=runCatching {it.currentPosition}.getOrDefault(position);runCatching {it.release()}}
        player=null
        if(focusHeld) {focusHeld=false;audio.abandonAudioFocusRequest(focus)}
    }
}

/** Short effects can silence the decorative player; GPS never reads this state. */
object AudioPriority { val state=MutableStateFlow(0)
    fun begin() {state.value++}
    fun end() {state.value=(state.value-1).coerceAtLeast(0)}
}
