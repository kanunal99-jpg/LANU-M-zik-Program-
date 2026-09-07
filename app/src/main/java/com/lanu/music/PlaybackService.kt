package com.lanu.music

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            setMediaItem(MediaItem.Builder().setUri("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3").build())
            prepare()
        }
        val sessionIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, sessionIntent, PendingIntent.FLAG_IMMUTABLE)
        mediaSession = MediaSession.Builder(this, player).setSessionActivity(pendingIntent).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}
