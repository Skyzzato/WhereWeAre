package com.whereweare.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log

/** At most two short players; released at completion and when the screen stops. */
class FlareAudio(private val context: Context) : AutoCloseable {
    private val players=mutableListOf<MediaPlayer>()
    fun play(resource: Int) {
        val audio=context.getSystemService(AudioManager::class.java)
        // UNKNOWN interruption filters are common and do not mean that audio is muted.
        if(audio.ringerMode==AudioManager.RINGER_MODE_SILENT) {Log.d("WhereWeAreAudio","skipped: silent ringer");return}
        try {
            val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val player=MediaPlayer.create(context,resource,attributes,0) ?: return
            player.setVolume(1f,1f)
            players.add(player)
            player.setOnCompletionListener {players.remove(it);it.release()}
            player.setOnErrorListener {p,_,_ -> players.remove(p);p.release();true}
            player.start()
        } catch(_: Exception) {close()}
    }
    override fun close() {players.toList().forEach {runCatching {it.release()}};players.clear()}
}
