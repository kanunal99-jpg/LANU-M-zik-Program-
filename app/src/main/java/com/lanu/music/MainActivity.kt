package com.lanu.music

import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : AppCompatActivity() {
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        root.addView(TextView(this).apply { text = "LANU Music"; textSize = 30f })
        root.addView(TextView(this).apply {
            text = "Kişisel müzik kütüphanen\n\nÇevrimdışı • Arka plan • Favoriler • Playlistler"
            textSize = 18f
            setPadding(0, 24, 0, 24)
        })

        val status = TextView(this).apply { text = "Oynatıcı hazır değil"; textSize = 16f }
        root.addView(status)

        val start = Button(this).apply { text = "Oynatıcıyı Başlat" }
        val playPause = Button(this).apply { text = "Oynat / Duraklat"; isEnabled = false }
        root.addView(start)
        root.addView(playPause)

        start.setOnClickListener {
            if (controller == null) {
                val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
                val future = MediaController.Builder(this, token).buildAsync()
                future.addListener({
                    runCatching { future.get() }.onSuccess { mediaController ->
                        controller = mediaController
                        playPause.isEnabled = true
                        status.text = "Oynatıcı bağlı • Demo parça hazırlanıyor"
                        mediaController.play()
                    }.onFailure { error ->
                        status.text = "Oynatıcı bağlantı hatası: ${error.message ?: "bilinmeyen hata"}"
                    }
                }, mainExecutor)
            } else {
                controller?.play()
            }
        }

        playPause.setOnClickListener {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }

        setContentView(root)
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
