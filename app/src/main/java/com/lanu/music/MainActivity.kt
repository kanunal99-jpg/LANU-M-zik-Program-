package com.lanu.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : AppCompatActivity() {
    private var controller: MediaController? = null
    private val tracks = mutableListOf<MusicTrack>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var status: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadDeviceMusic() else status.text = "Müzik erişimi verilmedi"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 24)
        }
        root.addView(TextView(this).apply { text = "LANU Music"; textSize = 30f })
        root.addView(TextView(this).apply {
            text = "Cihazındaki müzikleri otomatik bulur ve LANU Player içinde çalar."
            textSize = 16f
            setPadding(0, 16, 0, 16)
        })

        status = TextView(this).apply { text = "Müzik kitaplığı hazırlanıyor…"; textSize = 15f }
        root.addView(status)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val connect = Button(this).apply { text = "Player" }
        val playPause = Button(this).apply { text = "Oynat / Duraklat"; isEnabled = false }
        controls.addView(connect)
        controls.addView(playPause)
        root.addView(controls)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        val list = ListView(this).apply { adapter = this@MainActivity.adapter }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        connect.setOnClickListener { connectPlayer(playPause) }
        playPause.setOnClickListener {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val track = tracks[position]
            connectPlayer(playPause) {
                controller?.setMediaItem(
                    MediaItem.Builder()
                        .setUri(track.uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .setAlbumTitle(track.album)
                                .build()
                        )
                        .build()
                )
                controller?.prepare()
                controller?.play()
                status.text = "Çalıyor: ${track.title} — ${track.artist}"
            }
        }

        setContentView(root)
        ensureAudioPermission()
    }

    private fun ensureAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadDeviceMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun loadDeviceMusic() {
        tracks.clear()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                tracks += MusicTrack(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Bilinmeyen parça",
                    artist = cursor.getString(artistCol) ?: "Bilinmeyen sanatçı",
                    album = cursor.getString(albumCol) ?: "Bilinmeyen albüm",
                    uri = "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$id",
                    durationMs = cursor.getLong(durationCol)
                )
            }
        }
        adapter.clear()
        adapter.addAll(tracks.map { "${it.title}\n${it.artist} • ${it.album}" })
        adapter.notifyDataSetChanged()
        status.text = if (tracks.isEmpty()) {
            "Cihazda müzik bulunamadı"
        } else {
            "${tracks.size} parça bulundu"
        }
    }

    private fun connectPlayer(playPause: Button, afterConnected: (() -> Unit)? = null) {
        if (controller != null) {
            playPause.isEnabled = true
            afterConnected?.invoke()
            return
        }
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { mediaController ->
                controller = mediaController
                playPause.isEnabled = true
                status.text = "LANU Player bağlı"
                afterConnected?.invoke()
            }.onFailure { error ->
                status.text = "Player bağlantı hatası: ${error.message ?: "bilinmeyen hata"}"
            }
        }, mainExecutor)
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
