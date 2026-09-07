package com.lanu.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
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
    private lateinit var nowPlayingTitle: TextView
    private lateinit var nowPlayingArtist: TextView
    private lateinit var positionText: TextView
    private lateinit var durationText: TextView
    private lateinit var progress: SeekBar
    private lateinit var playPause: Button
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var shuffle: Button
    private lateinit var repeat: Button
    private lateinit var libraryCount: TextView
    private lateinit var emptyState: TextView
    private var userSeeking = false
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, 500L)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateNowPlaying()
            updateProgress()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateNowPlaying()
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateNowPlaying()
            updateProgress()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updatePlayerModeLabels()
        override fun onRepeatModeChanged(repeatMode: Int) = updatePlayerModeLabels()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadDeviceMusic() else status.text = "Müzik erişimi verilmedi"
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status.text = "Bildirim izni verilmedi; medya bildirimi görünmeyebilir"
        ensureAudioPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildProfessionalUi()
        ensureNotificationPermission()
        progressHandler.post(progressUpdater)
    }

    private fun buildProfessionalUi() {
        val bg = Color.rgb(15, 17, 20)
        val surface = Color.rgb(28, 31, 36)
        val surface2 = Color.rgb(37, 40, 46)
        val primary = Color.rgb(30, 215, 96)
        val white = Color.WHITE
        val muted = Color.rgb(170, 176, 186)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(18), dp(20), 0)
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(scrollContent)
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "LANU"
            textSize = 28f
            setTextColor(white)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val brandAccent = TextView(this).apply {
            text = " MUSIC"
            textSize = 28f
            setTextColor(primary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        brandRow.addView(brand)
        brandRow.addView(brandAccent)
        val refreshTop = TextView(this).apply {
            text = "↻"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(white)
            setOnClickListener { loadDeviceMusic() }
        }
        brandRow.addView(refreshTop, LinearLayout.LayoutParams(dp(48), dp(48)).apply { gravity = Gravity.END })
        scrollContent.addView(brandRow)

        val greeting = TextView(this).apply {
            text = "Müziğin burada."
            textSize = 14f
            setTextColor(muted)
            setPadding(0, dp(2), 0, dp(14))
        }
        scrollContent.addView(greeting)

        search = EditText(this).apply {
            hint = "Şarkı, sanatçı veya albüm ara"
            setHintTextColor(Color.rgb(130, 136, 145))
            setTextColor(white)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(surface2, 16)
        }
        scrollContent.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(20) })

        val section = TextView(this).apply {
            text = "Şimdi çalıyor"
            textSize = 21f
            setTextColor(white)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        scrollContent.addView(section)

        val nowCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = rounded(surface, 20)
        }
        val nowTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val cover = TextView(this).apply {
            text = "♪"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = rounded(primary, 16)
        }
        nowTop.addView(cover, LinearLayout.LayoutParams(dp(72), dp(72)))
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        nowPlayingTitle = TextView(this).apply {
            text = "Bir parça seç"
            textSize = 18f
            setTextColor(white)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
        }
        nowPlayingArtist = TextView(this).apply {
            text = "Kitaplığından müzik çal"
            textSize = 14f
            setTextColor(muted)
            maxLines = 1
        }
        titleCol.addView(nowPlayingTitle)
        titleCol.addView(nowPlayingArtist, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        nowTop.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
        nowCard.addView(nowTop)

        val progressRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        positionText = TextView(this).apply { text = "0:00"; textSize = 11f; setTextColor(muted) }
        durationText = TextView(this).apply { text = "0:00"; textSize = 11f; setTextColor(muted) }
        progress = SeekBar(this).apply { max = 1000; isEnabled = false }
        progressRow.addView(positionText)
        progressRow.addView(progress, LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
        progressRow.addView(durationText)
        nowCard.addView(progressRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        previous = playerButton("‹", muted, 44)
        playPause = playerButton("▶", white, 58)
        next = playerButton("›", muted, 44)
        controls.addView(previous)
        controls.addView(playPause, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(24); marginEnd = dp(24) })
        controls.addView(next)
        nowCard.addView(controls, LinearLayout.LayoutParams(-1, dp(70)))
        scrollContent.addView(nowCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })

        val libraryHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val libraryTitle = TextView(this).apply {
            text = "Kitaplığın"
            textSize = 21f
            setTextColor(white)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        libraryCount = TextView(this).apply { text = ""; textSize = 13f; setTextColor(muted); gravity = Gravity.END }
        libraryHeader.addView(libraryTitle, LinearLayout.LayoutParams(0, -2, 1f))
        libraryHeader.addView(libraryCount)
        scrollContent.addView(libraryHeader)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        val list = ListView(this).apply {
            adapter = this@MainActivity.adapter
            divider = null
            setBackgroundColor(bg)
            isNestedScrollingEnabled = false
        }
        scrollContent.addView(list, LinearLayout.LayoutParams(-1, dp(330)))
        emptyState = TextView(this).apply {
            text = "Kitaplık hazırlanıyor…"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        scrollContent.addView(emptyState, LinearLayout.LayoutParams(-1, dp(80)))

        status = TextView(this).apply { text = "Kitaplık hazırlanıyor…"; textSize = 12f; setTextColor(muted) }
        scrollContent.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(18) })

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        val home = navButton("⌂", "Ana Sayfa", primary)
        val library = navButton("♫", "Kitaplık", white)
        bottom.addView(home, LinearLayout.LayoutParams(0, dp(58), 1f))
        bottom.addView(library, LinearLayout.LayoutParams(0, dp(58), 1f))
        root.addView(bottom)
        setContentView(root)

        previous.isEnabled = false
        playPause.isEnabled = false
        next.isEnabled = false
        shuffle = Button(this).apply { text = "Karıştır: Kapalı"; isEnabled = false; visibility = View.GONE }
        repeat = Button(this).apply { text = "Tekrar: Kapalı"; isEnabled = false; visibility = View.GONE }

        playPause.setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        previous.setOnClickListener { controller?.seekToPreviousMediaItem() }
        next.setOnClickListener { controller?.seekToNextMediaItem() }
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val c = controller
                if (c != null && c.duration > 0) c.seekTo((c.duration * (progress.progress / 1000f)).toLong())
                userSeeking = false
                updateProgress()
            }
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) = Unit
        })
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filterTracks(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        list.setOnItemClickListener { _, _, position, _ -> playTrack(position) }
    }

    private fun playerButton(label: String, textColor: Int, size: Int): Button = Button(this).apply {
        text = label
        textSize = if (size > 50) 22f else 30f
        setTextColor(textColor)
        setBackgroundColor(Color.TRANSPARENT)
        isEnabled = false
    }

    private fun navButton(icon: String, label: String, color: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(TextView(this@MainActivity).apply { text = icon; textSize = 22f; setTextColor(color); gravity = Gravity.CENTER })
        addView(TextView(this@MainActivity).apply { text = label; textSize = 11f; setTextColor(color); gravity = Gravity.CENTER })
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else ensureAudioPermission()
    }

    private fun ensureAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadDeviceMusic()
        else permissionLauncher.launch(permission)
    }

    private fun loadDeviceMusic() {
        tracks.clear()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        runCatching {
            contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    tracks += MusicTrack(id, cursor.getString(titleCol) ?: "Bilinmeyen parça", cursor.getString(artistCol) ?: "Bilinmeyen sanatçı", cursor.getString(albumCol) ?: "Bilinmeyen albüm", "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$id", cursor.getLong(durationCol))
                }
            }
        }.onFailure { error -> status.text = "Kitaplık hatası: ${error.message ?: "bilinmeyen hata"}" }
        filterTracks(search.text?.toString().orEmpty())
    }

    private fun filterTracks(query: String) {
        val q = query.trim()
        visibleTracks.clear()
        visibleTracks += if (q.isEmpty()) tracks else tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
        adapter.clear()
        adapter.addAll(visibleTracks.map { "${it.title}\n${it.artist}  •  ${it.album}" })
        adapter.notifyDataSetChanged()
        libraryCount.text = if (tracks.isEmpty()) "" else "${visibleTracks.size} parça"
        emptyState.visibility = if (visibleTracks.isEmpty()) View.VISIBLE else View.GONE
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
                MediaItem.Builder().setUri(track.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).build()).build()
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
            }.onFailure { error -> status.text = "Player bağlantı hatası: ${error.message ?: "bilinmeyen hata"}" }
        }, mainExecutor)
    }

    private fun enablePlayerControls() {
        playPause.isEnabled = true
        previous.isEnabled = true
        next.isEnabled = true
        shuffle.isEnabled = true
        repeat.isEnabled = true
        progress.isEnabled = true
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
        nowPlayingTitle.text = item?.mediaMetadata?.title?.toString() ?: "Bir parça seç"
        nowPlayingArtist.text = item?.mediaMetadata?.artist?.toString() ?: "Kitaplığından müzik çal"
        playPause.text = if (c.isPlaying) "Ⅱ" else "▶"
        previous.isEnabled = c.hasPreviousMediaItem()
        next.isEnabled = c.hasNextMediaItem()
        updatePlayerModeLabels()
    }

    private fun updateProgress() {
        val c = controller ?: return
        val duration = c.duration
        val position = c.currentPosition.coerceAtLeast(0L)
        if (duration > 0L && !userSeeking) progress.progress = ((position.toDouble() / duration.toDouble()) * 1000.0).toInt().coerceIn(0, 1000)
        positionText.text = formatTime(position)
        durationText.text = formatTime(duration.takeIf { it > 0L } ?: 0L)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressUpdater)
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
