package com.lanu.music

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {
    private var controller: MediaController? = null
    private var connectionPending = false
    private val tracks = mutableListOf<MusicTrack>()
    private val visibleTracks = mutableListOf<MusicTrack>()
    private val recentIds = linkedSetOf<Long>()
    private val favoriteIds = linkedSetOf<Long>()
    private val playlistIds = linkedSetOf<Long>()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var libraryCount: TextView
    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var nowCover: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: Button
    private lateinit var progress: SeekBar
    private lateinit var positionText: TextView
    private lateinit var durationText: TextView
    private lateinit var homeNav: TextView
    private lateinit var libraryNav: TextView
    private var userSeeking = false
    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private val artworkGeneration = AtomicInteger(0)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("lanu_music", Context.MODE_PRIVATE) }
    private val progressRunnable = object : Runnable {
        override fun run() { updateProgress(); handler.postDelayed(this, 500L) }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateNowPlaying(); updateProgress(); renderHome() }
        override fun onIsPlayingChanged(isPlaying: Boolean) { updateNowPlaying(); updateMiniPlayer() }
        override fun onPlaybackStateChanged(playbackState: Int) { updateNowPlaying(); updateProgress() }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) { }
        override fun onRepeatModeChanged(mode: Int) { }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadDeviceMusic() else status.text = "Müzik erişimi verilmedi"
    }
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status.text = "Bildirim izni verilmedi; medya bildirimi görünmeyebilir"
        ensureAudioPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadLocalState()
        buildUi()
        ensureNotificationPermission()
        connectPlayer()
        handler.post(progressRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        artworkExecutor.shutdownNow()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onDestroy()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 13, 16))
            setPadding(dp(18), dp(14), dp(18), 0)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(18)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(buildBottomBar(), LinearLayout.LayoutParams(-1, dp(68)))
        setContentView(root)
        renderHome()
    }

    private fun renderHome() {
        content.removeAllViews()
        val white = Color.WHITE
        val muted = Color.rgb(157, 164, 174)
        val green = Color.rgb(30, 215, 96)
        val surface = Color.rgb(25, 28, 33)
        val surface2 = Color.rgb(34, 38, 44)

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "LANU"; textSize = 29f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(TextView(this).apply {
            text = "↻"; textSize = 27f; setTextColor(white); gravity = Gravity.CENTER
            setOnClickListener { loadDeviceMusic() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(header)

        content.addView(TextView(this).apply {
            text = if (tracks.isEmpty()) "Müziğin burada." else "Kitaplığın hazır."
            textSize = 14f; setTextColor(muted); setPadding(0, 0, 0, dp(12))
        })

        search = EditText(this).apply {
            hint = "Şarkı, sanatçı veya albüm ara"
            setHintTextColor(Color.rgb(120, 126, 136)); setTextColor(white); textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(true); setPadding(dp(16), 0, dp(16), 0)
            background = rounded(surface2, 16)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderSearchResults(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(20) })

        if (tracks.isNotEmpty()) {
            content.addView(sectionTitle("Hızlı erişim", muted))
            val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chips.addView(chip("Son çalınanlar") { renderCollection("Son çalınanlar", recentTracks()) })
            chips.addView(chip("Favoriler") { renderCollection("Favoriler", favoriteTracks()) })
            chips.addView(chip("Albüm") { renderCollection("Albümler", tracks.distinctBy { it.album }) })
            chips.addView(chip("Sanatçı") { renderCollection("Sanatçılar", tracks.distinctBy { it.artist }) })
            content.addView(chips, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(18) })

            content.addView(sectionTitle("Son çalınanlar", white))
            addTrackRail(recentTracks().take(6), surface, muted)

            content.addView(sectionTitle("Favorilerin", white))
            val favs = favoriteTracks().take(6)
            if (favs.isEmpty()) addHint("Bir şarkının yanındaki ☆ düğmesine dokunarak favorilerine ekle.", muted)
            else addTrackRail(favs, surface, muted)

            content.addView(sectionTitle("Albümler", white))
            addAlbumRail(tracks.distinctBy { it.album }.take(8), surface, muted)

            content.addView(sectionTitle("Sanatçılar", white))
            addArtistRail(tracks.distinctBy { it.artist }.take(8), surface, muted)

            content.addView(sectionTitle("Kitaplığın", white))
            libraryCount = TextView(this).apply { textSize = 12f; setTextColor(muted); text = "${tracks.size} parça" }
            content.addView(libraryCount, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addTrackList(tracks.take(30), surface, muted)
        } else {
            addEmptyState(surface, muted)
        }

        content.addView(sectionTitle("Şimdi çalıyor", white), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        addNowPlayingCard(surface, muted, green)
        status = TextView(this).apply { text = if (tracks.isEmpty()) "Cihaz müzikleri bekleniyor…" else "Yerel kitaplık • çevrimdışı"; textSize = 12f; setTextColor(muted); setPadding(0, dp(8), 0, dp(6)) }
        content.addView(status)
        updateNowPlaying()
    }

    private fun renderSearchResults(query: String) {
        if (query.trim().isEmpty()) { renderHome(); return }
        content.removeAllViews()
        val white = Color.WHITE; val muted = Color.rgb(157, 164, 174); val surface = Color.rgb(25, 28, 33)
        content.addView(TextView(this).apply { text = "Arama"; textSize = 26f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD })
        content.addView(TextView(this).apply { text = "${query.trim()} için sonuçlar"; textSize = 13f; setTextColor(muted); setPadding(0, dp(4), 0, dp(16)) })
        val q = query.trim()
        val results = tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
        addTrackList(results, surface, muted)
        if (results.isEmpty()) addHint("Sonuç bulunamadı. Başka bir şarkı, sanatçı veya albüm deneyin.", muted)
    }

    private fun renderCollection(title: String, items: List<MusicTrack>) {
        content.removeAllViews()
        val white = Color.WHITE; val muted = Color.rgb(157, 164, 174); val surface = Color.rgb(25, 28, 33)
        content.addView(TextView(this).apply { text = title; textSize = 27f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD })
        content.addView(TextView(this).apply { text = "${items.size} öğe"; textSize = 13f; setTextColor(muted); setPadding(0, dp(4), 0, dp(16)) })
        if (items.isEmpty()) addHint("Burada henüz içerik yok.", muted) else addTrackList(items, surface, muted)
        content.addView(TextView(this).apply { text = "← Ana sayfa"; textSize = 14f; setTextColor(Color.rgb(30,215,96)); gravity = Gravity.CENTER; setPadding(0, dp(22), 0, dp(16)); setOnClickListener { renderHome() } })
    }

    private fun addNowPlayingCard(surface: Int, muted: Int, green: Int) {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(14)); background = rounded(surface, 20) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nowCover = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_media_play); background = rounded(Color.rgb(45,49,56), 14); clipToOutline = true; contentDescription = "Albüm kapağı" }
        row.addView(nowCover, LinearLayout.LayoutParams(dp(76), dp(76)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        nowTitle = TextView(this).apply { text = "Bir parça seç"; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; maxLines = 1 }
        nowArtist = TextView(this).apply { text = "Kitaplığından müzik çal"; textSize = 13f; setTextColor(muted); maxLines = 1 }
        col.addView(nowTitle); col.addView(nowArtist, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val fav = TextView(this).apply { textSize = 26f; setTextColor(green); gravity = Gravity.CENTER; setOnClickListener { toggleCurrentFavorite() } }
        row.addView(fav, LinearLayout.LayoutParams(dp(42), dp(50)))
        card.addView(row)
        progress = SeekBar(this).apply { max = 1000; isEnabled = false }
        val pr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        positionText = TextView(this).apply { text = "0:00"; textSize = 11f; setTextColor(muted) }
        durationText = TextView(this).apply { text = "0:00"; textSize = 11f; setTextColor(muted) }
        pr.addView(positionText); pr.addView(progress, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginStart = dp(8); marginEnd = dp(8) }); pr.addView(durationText)
        card.addView(pr)
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(s: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) { controller?.let { if (it.duration > 0) it.seekTo((it.duration * (progress.progress / 1000f)).toLong()) }; userSeeking = false; updateProgress() }
            override fun onProgressChanged(s: SeekBar?, value: Int, fromUser: Boolean) = Unit
        })
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val prev = playerButton("‹", muted, 44) { controller?.seekToPreviousMediaItem() }
        val play = playerButton("▶", Color.WHITE, 56) { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        val next = playerButton("›", muted, 44) { controller?.seekToNextMediaItem() }
        controls.addView(prev); controls.addView(play, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(24); marginEnd = dp(24) }); controls.addView(next)
        card.addView(controls, LinearLayout.LayoutParams(-1, dp(66)))
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
    }

    private fun addTrackRail(items: List<MusicTrack>, surface: Int, muted: Int) {
        if (items.isEmpty()) { addHint("Henüz bir geçmiş yok.", muted); return }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { track ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(4), dp(5), dp(8)); setOnClickListener { playTrack(track) } }
            val art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_media_play); background = rounded(surface, 14); contentDescription = track.album }
            card.addView(art, LinearLayout.LayoutParams(dp(108), dp(108)))
            card.addView(TextView(this).apply { text = track.title; textSize = 12f; setTextColor(Color.WHITE); maxLines = 1 }, LinearLayout.LayoutParams(dp(108), -2).apply { topMargin = dp(7) })
            card.addView(TextView(this).apply { text = track.artist; textSize = 11f; setTextColor(muted); maxLines = 1 })
            loadArtwork(track, art, 108)
            row.addView(card, LinearLayout.LayoutParams(dp(118), -2))
        }
        val scroll = ScrollView(this).apply { isHorizontalScrollBarEnabled = false; isHorizontalFadingEdgeEnabled = true; addView(row) }
        content.addView(scroll, LinearLayout.LayoutParams(-1, dp(158)).apply { bottomMargin = dp(18) })
    }

    private fun addAlbumRail(items: List<MusicTrack>, surface: Int, muted: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { albumTrack ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(4), dp(5), dp(6)); setOnClickListener { renderCollection(albumTrack.album, tracks.filter { it.album == albumTrack.album }) } }
            val art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_menu_gallery); background = rounded(surface, 14); contentDescription = albumTrack.album }
            box.addView(art, LinearLayout.LayoutParams(dp(116), dp(116)))
            box.addView(TextView(this).apply { text = albumTrack.album; textSize = 12f; setTextColor(Color.WHITE); maxLines = 1 }, LinearLayout.LayoutParams(dp(116), -2).apply { topMargin = dp(7) })
            box.addView(TextView(this).apply { text = albumTrack.artist; textSize = 11f; setTextColor(muted); maxLines = 1 })
            loadArtwork(albumTrack, art, 116); row.addView(box, LinearLayout.LayoutParams(dp(126), -2))
        }
        content.addView(ScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }, LinearLayout.LayoutParams(-1, dp(160)).apply { bottomMargin = dp(18) })
    }

    private fun addArtistRail(items: List<MusicTrack>, surface: Int, muted: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { artistTrack ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(5), dp(4), dp(5), dp(6)); setOnClickListener { renderCollection(artistTrack.artist, tracks.filter { it.artist == artistTrack.artist }) } }
            val art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_menu_myplaces); background = rounded(surface, 60); contentDescription = artistTrack.artist; clipToOutline = true }
            box.addView(art, LinearLayout.LayoutParams(dp(96), dp(96)))
            box.addView(TextView(this).apply { text = artistTrack.artist; textSize = 12f; setTextColor(Color.WHITE); maxLines = 1; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(110), -2).apply { topMargin = dp(7) })
            loadArtwork(artistTrack, art, 96); row.addView(box, LinearLayout.LayoutParams(dp(120), -2))
        }
        content.addView(ScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }, LinearLayout.LayoutParams(-1, dp(140)).apply { bottomMargin = dp(18) })
    }

    private fun addTrackList(items: List<MusicTrack>, surface: Int, muted: Int) {
        items.forEach { track ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(6), dp(8)); background = rounded(surface, 14); setOnClickListener { playTrack(track) } }
            val art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_media_play); background = rounded(Color.rgb(44,48,55), 10); contentDescription = track.album }
            row.addView(art, LinearLayout.LayoutParams(dp(54), dp(54)))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(4), 0) }
            col.addView(TextView(this).apply { text = track.title; textSize = 14f; setTextColor(Color.WHITE); maxLines = 1 })
            col.addView(TextView(this).apply { text = "${track.artist} • ${track.album}"; textSize = 11f; setTextColor(muted); maxLines = 1 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            val fav = TextView(this).apply { text = if (favoriteIds.contains(track.id)) "★" else "☆"; textSize = 22f; setTextColor(Color.rgb(30,215,96)); gravity = Gravity.CENTER; setOnClickListener { toggleFavorite(track); text = if (favoriteIds.contains(track.id)) "★" else "☆" } }
            row.addView(fav, LinearLayout.LayoutParams(dp(44), dp(54)))
            loadArtwork(track, art, 54)
            content.addView(row, LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(6) })
        }
    }

    private fun addEmptyState(surface: Int, muted: Int) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(30), dp(24), dp(30)); background = rounded(surface, 22) }
        box.addView(TextView(this).apply { text = "♫"; textSize = 44f; setTextColor(Color.rgb(30,215,96)); gravity = Gravity.CENTER })
        box.addView(TextView(this).apply { text = "Müziğinizi keşfetmeye hazır"; textSize = 19f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(6)) })
        box.addView(TextView(this).apply { text = "LANU Music cihazındaki yerel parçaları burada güvenli ve çevrimdışı şekilde listeler."; textSize = 13f; setTextColor(muted); gravity = Gravity.CENTER })
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
    }

    private fun addHint(text: String, muted: Int) {
        content.addView(TextView(this).apply { this.text = text; textSize = 13f; setTextColor(muted); setPadding(dp(4), dp(4), dp(4), dp(14)) })
    }

    private fun sectionTitle(text: String, color: Int): TextView = TextView(this).apply { this.text = text; textSize = 20f; setTextColor(color); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(2), 0, dp(10)) }

    private fun chip(text: String, action: () -> Unit): TextView = TextView(this).apply { this.text = text; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = rounded(Color.rgb(34,38,44), 18); setPadding(dp(13), 0, dp(13), 0); setOnClickListener { action() } }

    private fun buildBottomBar(): View {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(6)) }
        homeNav = TextView(this).apply { text = "⌂\nAna Sayfa"; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.rgb(30,215,96)); setOnClickListener { renderHome() } }
        libraryNav = TextView(this).apply { text = "♫\nKitaplık"; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.rgb(157,164,174)); setOnClickListener { renderCollection("Kitaplık", tracks) } }
        bar.addView(homeNav, LinearLayout.LayoutParams(0, -1, 1f)); bar.addView(libraryNav, LinearLayout.LayoutParams(0, -1, 1f))
        return bar
    }

    private fun playerButton(label: String, color: Int, size: Int, action: () -> Unit): Button = Button(this).apply { text = label; textSize = if (size > 50) 22f else 30f; setTextColor(color); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { action() } }

    private fun playTrack(track: MusicTrack) {
        val c = controller ?: return
        val items = visibleTracks.ifEmpty { tracks }.map { toMediaItem(it) }
        val index = items.indexOfFirst { it.mediaId == track.id.toString() }
        if (index < 0) return
        c.setMediaItems(items, index, 0L)
        c.prepare(); c.play()
        rememberRecent(track.id)
        updateNowPlaying()
    }

    private fun toMediaItem(track: MusicTrack): MediaItem = MediaItem.Builder().setMediaId(track.id.toString()).setUri(track.uri).setMediaMetadata(
        MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).build()
    ).build()

    private fun connectPlayer() {
        if (connectionPending || controller != null) return
        connectionPending = true
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            connectionPending = false
            runCatching {
                controller = future.get()
                controller?.addListener(playerListener)
                updateNowPlaying()
            }.onFailure { status.text = "Oynatıcı bağlantısı kurulamadı" }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateNowPlaying() {
        val c = controller ?: return
        if (!::nowTitle.isInitialized) return
        val item = c.currentMediaItem
        if (item == null) {
            nowTitle.text = "Bir parça seç"; nowArtist.text = "Kitaplığından müzik çal"; nowCover.setImageResource(android.R.drawable.ic_media_play); updateMiniPlayer(); return
        }
        nowTitle.text = item.mediaMetadata.title ?: "Bilinmeyen parça"
        nowArtist.text = item.mediaMetadata.artist ?: "Bilinmeyen sanatçı"
        val track = tracks.firstOrNull { it.id.toString() == item.mediaId }
        if (track != null) loadArtwork(track, nowCover, 76)
        updateMiniPlayer()
    }

    private fun updateMiniPlayer() {
        if (!::miniTitle.isInitialized) return
        val item = controller?.currentMediaItem
        miniTitle.text = item?.mediaMetadata?.title ?: "LANU Music"
        miniArtist.text = item?.mediaMetadata?.artist ?: "Bir parça seç"
        miniPlay.text = if (controller?.isPlaying == true) "Ⅱ" else "▶"
    }

    private fun updateProgress() {
        if (!::progress.isInitialized) return
        val c = controller ?: return
        val d = c.duration
        val p = c.currentPosition.coerceAtLeast(0L)
        progress.isEnabled = d > 0
        if (!userSeeking && d > 0) progress.progress = ((p.toDouble() / d.toDouble()) * 1000).toInt().coerceIn(0, 1000)
        positionText.text = formatMs(p); durationText.text = formatMs(d.coerceAtLeast(0L))
    }

    private fun updateMiniBarVisibility() { }

    private fun formatMs(ms: Long): String { val total = ms / 1000; return "${total / 60}:${(total % 60).toString().padStart(2, '0')}" }

    private fun loadArtwork(track: MusicTrack, view: ImageView, sizeDp: Int) {
        val generation = artworkGeneration.incrementAndGet()
        view.setImageResource(android.R.drawable.ic_menu_gallery)
        artworkExecutor.execute {
            val bitmap = runCatching { AlbumArtResolver.load(this, track.uri) }.getOrNull()
            runOnUiThread { if (!isFinishing && artworkGeneration.get() >= generation && bitmap != null) view.setImageBitmap(bitmap) }
        }
    }

    private fun toggleCurrentFavorite() {
        val id = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        tracks.firstOrNull { it.id == id }?.let { toggleFavorite(it); renderHome() }
    }

    private fun toggleFavorite(track: MusicTrack) {
        if (!favoriteIds.add(track.id)) favoriteIds.remove(track.id)
        saveLocalState()
    }

    private fun rememberRecent(id: Long) {
        recentIds.remove(id); recentIds.add(id)
        while (recentIds.size > 30) recentIds.remove(recentIds.first())
        saveLocalState()
    }

    private fun recentTracks(): List<MusicTrack> = recentIds.asReversed().mapNotNull { id -> tracks.firstOrNull { it.id == id } }
    private fun favoriteTracks(): List<MusicTrack> = favoriteIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } }.asReversed()

    private fun loadLocalState() {
        readIds("recent").forEach { recentIds.add(it) }
        readIds("favorites").forEach { favoriteIds.add(it) }
        readIds("playlist").forEach { playlistIds.add(it) }
    }

    private fun saveLocalState() {
        prefs.edit().putString("recent", recentIds.joinToString(",")).putString("favorites", favoriteIds.joinToString(",")).putString("playlist", playlistIds.joinToString(",")).apply()
    }

    private fun readIds(key: String): List<Long> = prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else ensureAudioPermission()
    }

    private fun ensureAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadDeviceMusic() else permissionLauncher.launch(permission)
    }

    private fun loadDeviceMusic() {
        tracks.clear()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        runCatching {
            contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val trackId = cursor.getLong(id)
                    tracks += MusicTrack(trackId, cursor.getString(title) ?: "Bilinmeyen parça", cursor.getString(artist) ?: "Bilinmeyen sanatçı", cursor.getString(album) ?: "Bilinmeyen albüm", "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$trackId", cursor.getLong(duration))
                }
            }
        }.onFailure { status.text = "Kitaplık hatası: ${it.message ?: "bilinmeyen hata"}" }
        runOnUiThread { renderHome() }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
