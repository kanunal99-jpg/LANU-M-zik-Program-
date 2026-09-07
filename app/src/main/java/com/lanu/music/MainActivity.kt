package com.lanu.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : AppCompatActivity() {
    private var controller: MediaController? = null
    private var playerConnectionInProgress = false
    private val tracks = mutableListOf<MusicTrack>()
    private val visibleTracks = mutableListOf<MusicTrack>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var playPause: Button
    private lateinit var nowPlayingTitle: TextView
    private lateinit var nowPlayingArtist: TextView
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var shuffle: Button
    private lateinit var repeat: Button

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateNowPlaying()
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateNowPlaying()
        override fun onPlaybackStateChanged(playbackState: Int) = updateNowPlaying()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updatePlayerModeLabels()
        override fun onRepeatModeChanged(repeatMode: Int) = updatePlayerModeLabels()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadDeviceMusic() else status.text = "Müzik erişimi verilmedi"
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            status.text = "Bildirim izni verilmedi; medya bildirimi görünmeyebilir"
        }
        ensureAudioPermission()
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
            setPadding(0, 16, 0, 12)
        })

        search = EditText(this).apply {
            hint = "Şarkı, sanatçı veya albüm ara…"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        root.addView(search)

        nowPlayingTitle = TextView(this).apply {
            text = "Şimdi çalıyor"
            textSize = 20f
        }
        root.addView(nowPlayingTitle)
        nowPlayingArtist = TextView(this).apply {
            text = "Bir parça seç"
            textSize = 15f
        }
        root.addView(nowPlayingArtist)

        val playerControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        previous = Button(this).apply { text = "Önceki"; isEnabled = false }
        playPause = Button(this).apply { text = "Oynat"; isEnabled = false }
        next = Button(this).apply { text = "Sonraki"; isEnabled = false }
        playerControls.addView(previous, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        playerControls.addView(playPause, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        playerControls.addView(next, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(playerControls)

        val modeControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shuffle = Button(this).apply { text = "Karıştır: Kapalı"; isEnabled = false }
        repeat = Button(this).apply { text = "Tekrar: Kapalı"; isEnabled = false }
        modeControls.addView(shuffle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        modeControls.addView(repeat, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(modeControls)

        status = TextView(this).apply { text = "Müzik kitaplığı hazırlanıyor…"; textSize = 15f }
        root.addView(status)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val connect = Button(this).apply { text = "Player" }
        val refresh = Button(this).apply { text = "Yenile" }
        controls.addView(connect)
        controls.addView(refresh)
        root.addView(controls)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        val list = ListView(this).apply { adapter = this@MainActivity.adapter }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        connect.setOnClickListener { connectPlayer() }
        playPause.setOnClickListener {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        previous.setOnClickListener { controller?.seekToPreviousMediaItem() }
        next.setOnClickListener { controller?.seekToNextMediaItem() }
        shuffle.setOnClickListener {
            controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
        }
        repeat.setOnClickListener {
            controller?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        }
        refresh.setOnClickListener { loadDeviceMusic() }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filterTracks(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        list.setOnItemClickListener { _, _, position, _ -> playTrack(position) }

        setContentView(root)
        ensureNotificationPermission()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ensureAudioPermission()
        }
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
        runCatching {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
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
        }.onFailure { error -> status.text = "Kitaplık hatası: ${error.message ?: "bilinmeyen hata"}" }
        filterTracks(search.text?.toString().orEmpty())
    }

    private fun filterTracks(query: String) {
        val q = query.trim()
        visibleTracks.clear()
        visibleTracks += if (q.isEmpty()) tracks else tracks.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
        adapter.clear()
        adapter.addAll(visibleTracks.map { "${it.title}\n${it.artist} • ${it.album}" })
        adapter.notifyDataSetChanged()
        status.text = when {
            tracks.isEmpty() -> "Cihazda müzik bulunamadı"
            q.isNotEmpty() -> "${visibleTracks.size} sonuç / ${tracks.size} parça"
            else -> "${tracks.size} parça bulundu"
        }
    }

    private fun playTrack(position: Int) {
        if (position !in visibleTracks.indices) return
        val selected = visibleTracks[position]
        val queueIndex = tracks.indexOfFirst { it.id == selected.id }
        if (queueIndex < 0) return

        connectPlayer {
            val queue = tracks.map { track ->
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
            }
            controller?.setMediaItems(queue, queueIndex, 0L)
            controller?.prepare()
            controller?.play()
            status.text = "Çalıyor: ${selected.title} — ${selected.artist}"
            updateNowPlaying()
        }
    }

    private fun connectPlayer(afterConnected: (() -> Unit)? = null) {
        if (controller != null) {
            enablePlayerControls()
            afterConnected?.invoke()
            updateNowPlaying()
            return
        }
        if (playerConnectionInProgress) return
        playerConnectionInProgress = true
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            playerConnectionInProgress = false
            runCatching { future.get() }.onSuccess { mediaController ->
                controller = mediaController
                controller?.addListener(playerListener)
                enablePlayerControls()
                status.text = "LANU Player bağlı"
                updateNowPlaying()
                afterConnected?.invoke()
            }.onFailure { error ->
                status.text = "Player bağlantı hatası: ${error.message ?: "bilinmeyen hata"}"
            }
        }, mainExecutor)
    }

    private fun enablePlayerControls() {
        playPause.isEnabled = true
        previous.isEnabled = true
        next.isEnabled = true
        shuffle.isEnabled = true
        repeat.isEnabled = true
        updatePlayerModeLabels()
    }

    private fun updatePlayerModeLabels() {
        val c = controller ?: return
        shuffle.text = if (c.shuffleModeEnabled) "Karıştır: Açık" else "Karıştır: Kapalı"
        repeat.text = when (c.repeatMode) {
            Player.REPEAT_MODE_ALL -> "Tekrar: Tümü"
            Player.REPEAT_MODE_ONE -> "Tekrar: Tek"
            else -> "Tekrar: Kapalı"
        }
    }

    private fun updateNowPlaying() {
        val c = controller ?: return
        val item = c.currentMediaItem
        nowPlayingTitle.text = item?.mediaMetadata?.title?.toString() ?: "Şimdi çalıyor"
        nowPlayingArtist.text = item?.mediaMetadata?.artist?.toString() ?: "Bir parça seç"
        playPause.text = if (c.isPlaying) "Duraklat" else "Oynat"
        previous.isEnabled = c.hasPreviousMediaItem()
        next.isEnabled = c.hasNextMediaItem()
        updatePlayerModeLabels()
    }

    override fun onDestroy() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
