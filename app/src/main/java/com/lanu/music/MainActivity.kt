package com.lanu.music

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        root.addView(TextView(this).apply { text = "LANU Music"; textSize = 30f })
        root.addView(TextView(this).apply {
            text = "Kişisel müzik kütüphanen\n\nÇevrimdışı • Arka plan • Favoriler • Playlistler"
            textSize = 18f
            setPadding(0, 24, 0, 24)
        })
        root.addView(Button(this).apply {
            text = "Oynatıcıyı Başlat"
            setOnClickListener { startService(Intent(this@MainActivity, PlaybackService::class.java)) }
        })
        setContentView(root)
    }
}
