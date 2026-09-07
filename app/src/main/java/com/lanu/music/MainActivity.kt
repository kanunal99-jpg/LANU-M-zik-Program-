package com.lanu.music

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private var controller: MediaController? = null
    private val tracks = mutableListOf<MusicTrack>()
    private val recentIds = linkedSetOf<Long>()
    private val favoriteIds = linkedSetOf<Long>()
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: Button
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var playlistStore: PlaylistStore
    private val artworkExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadDeviceMusic() else status.text = "Müzik erişimi verilmedi"
    }
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ensureAudioPermission() }
    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateMiniPlayer() }
        override fun onIsPlayingChanged(isPlaying: Boolean) { updateMiniPlayer() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("lanu_music", Context.MODE_PRIVATE)
        playlistStore = PlaylistStore(this)
        loadLocalState()
        buildShell()
        ensureNotificationPermission()
        connectPlayer()
    }

    override fun onDestroy() {
        artworkExecutor.shutdownNow()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onDestroy()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(11,13,16)); setPadding(dp(18),dp(14),dp(18),0) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,0,0,dp(20)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(buildMiniPlayer(), LinearLayout.LayoutParams(-1,dp(64)))
        root.addView(buildBottomBar(), LinearLayout.LayoutParams(-1,dp(66)))
        setContentView(root)
        renderHome()
    }

    private fun renderHome() {
        content.removeAllViews()
        val muted=Color.rgb(157,164,174); val surface=Color.rgb(25,28,33); val green=Color.rgb(30,215,96)
        val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        header.addView(TextView(this).apply{text="LANU";textSize=29f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,dp(52),1f))
        header.addView(TextView(this).apply{text="↻";textSize=27f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setOnClickListener{loadDeviceMusic()}},LinearLayout.LayoutParams(dp(48),dp(48)))
        content.addView(header)
        content.addView(TextView(this).apply{text=if(tracks.isEmpty())"Müziğin burada."else"Kitaplığın hazır.";textSize=14f;setTextColor(muted);setPadding(0,0,0,dp(12))})
        val search=EditText(this).apply{hint="Şarkı, sanatçı veya albüm ara";setHintTextColor(Color.rgb(120,126,136));setTextColor(Color.WHITE);textSize=15f;inputType=InputType.TYPE_CLASS_TEXT;setSingleLine(true);setPadding(dp(16),0,dp(16),0);background=rounded(Color.rgb(34,38,44),16)}
        search.setOnEditorActionListener{_,_,_->renderSearch(search.text.toString());true}
        content.addView(search,LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(18)})
        content.addView(sectionTitle("Hızlı erişim",muted))
        val chips=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        chips.addView(chip("Son çalınanlar"){renderCollection("Son çalınanlar",recentTracks())})
        chips.addView(chip("Favoriler"){renderCollection("Favoriler",favoriteTracks())})
        chips.addView(chip("Çalma listeleri"){renderPlaylists()})
        chips.addView(chip("Albümler"){renderCollection("Albümler",tracks.distinctBy{it.album})})
        content.addView(chips,LinearLayout.LayoutParams(-1,dp(46)).apply{bottomMargin=dp(18)})
        if(tracks.isEmpty()) addEmptyState(surface,muted) else {
            content.addView(sectionTitle("Son çalınanlar",Color.WHITE)); addTrackRail(recentTracks().take(6),surface,muted)
            content.addView(sectionTitle("Favorilerin",Color.WHITE)); addTrackList(favoriteTracks().take(6),surface,muted)
            content.addView(sectionTitle("Albümler",Color.WHITE)); addAlbumRail(surface,muted)
            content.addView(sectionTitle("Sanatçılar",Color.WHITE)); addArtistRail(surface,muted)
            content.addView(sectionTitle("Kitaplığın",Color.WHITE)); addTrackList(tracks.take(30),surface,muted)
        }
        status=TextView(this).apply{text=if(tracks.isEmpty())"Cihaz müzikleri bekleniyor…"else"Yerel kitaplık • çevrimdışı";textSize=12f;setTextColor(muted);setPadding(0,dp(10),0,dp(4))}
        content.addView(status)
    }

    private fun renderSearch(query:String){val q=query.trim();if(q.isEmpty())return renderHome();renderCollection("Arama",tracks.filter{it.title.contains(q,true)||it.artist.contains(q,true)||it.album.contains(q,true)})}
    private fun renderCollection(title:String,items:List<MusicTrack>){content.removeAllViews();val muted=Color.rgb(157,164,174);val surface=Color.rgb(25,28,33);content.addView(TextView(this).apply{text=title;textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD});content.addView(TextView(this).apply{text="${items.size} öğe";textSize=13f;setTextColor(muted);setPadding(0,dp(4),0,dp(16))});if(items.isEmpty())addHint("Burada henüz içerik yok.",muted)else addTrackList(items,surface,muted);addBack()}

    private fun renderPlaylists(){content.removeAllViews();val muted=Color.rgb(157,164,174);val surface=Color.rgb(25,28,33);val green=Color.rgb(30,215,96);content.addView(TextView(this).apply{text="Çalma listeleri";textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD});content.addView(TextView(this).apply{text="Müziğini kendi koleksiyonlarınla düzenle";textSize=13f;setTextColor(muted);setPadding(0,dp(4),0,dp(14))});content.addView(Button(this).apply{text="+ Yeni çalma listesi";setTextColor(Color.WHITE);background=rounded(green,16);setOnClickListener{showCreatePlaylistDialog()}},LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(14)});val lists=playlistStore.load();lists.forEach{p->val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(10),dp(8),dp(10));background=rounded(surface,16);setOnClickListener{renderPlaylist(p.id)}};row.addView(TextView(this).apply{text="♫";textSize=25f;setTextColor(green);gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(48),dp(52)));val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,dp(6),0)};info.addView(TextView(this).apply{text=p.name;textSize=15f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;maxLines=1});info.addView(TextView(this).apply{text="${p.trackIds.size} parça";textSize=11f;setTextColor(muted);setPadding(0,dp(4),0,0)});row.addView(info,LinearLayout.LayoutParams(0,-2,1f));row.addView(TextView(this).apply{text="⋮";textSize=25f;setTextColor(muted);gravity=Gravity.CENTER;setOnClickListener{showPlaylistActions(p.id)}},LinearLayout.LayoutParams(dp(44),dp(52)));content.addView(row,LinearLayout.LayoutParams(-1,dp(72)).apply{bottomMargin=dp(7)})};if(lists.isEmpty())addHint("İlk çalma listeni oluştur ve parçalarını ekle.",muted);addBack()}

    private fun renderPlaylist(id:String){val p=playlistStore.load().firstOrNull{it.id==id}?:return renderPlaylists();val items=p.trackIds.mapNotNull{tid->tracks.firstOrNull{it.id==tid}};content.removeAllViews();val muted=Color.rgb(157,164,174);val surface=Color.rgb(25,28,33);val green=Color.rgb(30,215,96);content.addView(TextView(this).apply{text=p.name;textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD});content.addView(TextView(this).apply{text="${items.size} parça";textSize=13f;setTextColor(muted);setPadding(0,dp(4),0,dp(12))});val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};actions.addView(Button(this).apply{text="▶ Tümünü çal";setTextColor(Color.WHITE);background=rounded(green,16);setOnClickListener{playPlaylist(items)}},LinearLayout.LayoutParams(0,dp(48),1f));actions.addView(Button(this).apply{text="Parça ekle";setTextColor(Color.WHITE);background=rounded(surface,16);setOnClickListener{showAddTracksDialog(p.id)}},LinearLayout.LayoutParams(0,dp(48),1f).apply{marginStart=dp(8)});content.addView(actions,LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(14)});items.forEach{addPlaylistTrackRow(p.id,it,surface,muted)};if(items.isEmpty())addHint("Bu liste boş. “Parça ekle” ile kitaplığından seçim yap.",muted);addBack{renderPlaylists()}}

    private fun addPlaylistTrackRow(playlistId:String,track:MusicTrack,surface:Int,muted:Int){val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(7),dp(6),dp(7));background=rounded(surface,14);setOnClickListener{playPlaylist(playlistStore.load().firstOrNull{it.id==playlistId}?.trackIds?.mapNotNull{tid->tracks.firstOrNull{it.id==tid}}?:listOf(track))}};val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setImageResource(android.R.drawable.ic_media_play);background=rounded(Color.rgb(44,48,55),10)};row.addView(art,LinearLayout.LayoutParams(dp(52),dp(52)));val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(4),0)};info.addView(TextView(this).apply{text=track.title;textSize=14f;setTextColor(Color.WHITE);maxLines=1});info.addView(TextView(this).apply{text=track.artist;textSize=11f;setTextColor(muted);setPadding(0,dp(4),0,0)});row.addView(info,LinearLayout.LayoutParams(0,-2,1f));row.addView(TextView(this).apply{text="−";textSize=24f;setTextColor(muted);gravity=Gravity.CENTER;setOnClickListener{playlistStore.removeTrack(playlistId,track.id);renderPlaylist(playlistId)}},LinearLayout.LayoutParams(dp(42),dp(52)));loadArtwork(track,art);content.addView(row,LinearLayout.LayoutParams(-1,dp(66)).apply{bottomMargin=dp(6)})}

    private fun showCreatePlaylistDialog(){val input=EditText(this).apply{hint="Örn. Akşam sürüşü";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT};AlertDialog.Builder(this).setTitle("Yeni çalma listesi").setView(input).setNegativeButton("Vazgeç",null).setPositiveButton("Oluştur"){_,_->if(playlistStore.create(input.text.toString())!=null)renderPlaylists()else status.text="Geçerli bir liste adı girin"}.show()}
    private fun showAddTracksDialog(playlistId:String){if(tracks.isEmpty())return;val selected=playlistStore.load().firstOrNull{it.id==playlistId}?.trackIds?.toSet().orEmpty();val labels=tracks.map{"${it.title} • ${it.artist}"}.toTypedArray();val checked=tracks.map{selected.contains(it.id)}.toBooleanArray();AlertDialog.Builder(this).setTitle("Playlist'e parça ekle").setMultiChoiceItems(labels,checked){_,which,isChecked->if(isChecked)playlistStore.addTrack(playlistId,tracks[which].id)else playlistStore.removeTrack(playlistId,tracks[which].id)}.setPositiveButton("Bitti"){_,_->renderPlaylist(playlistId)}.setNegativeButton("Vazgeç",null).show()}
    private fun showPlaylistActions(id:String){val p=playlistStore.load().firstOrNull{it.id==id}?:return;AlertDialog.Builder(this).setTitle(p.name).setItems(arrayOf("Yeniden adlandır","Sil")){_,which->if(which==0)showRenamePlaylistDialog(id,p.name)else AlertDialog.Builder(this).setTitle("Liste silinsin mi?").setMessage("${p.name} silinecek. Parçalar cihazdan silinmez.").setNegativeButton("Vazgeç",null).setPositiveButton("Sil"){_,_->playlistStore.delete(id);renderPlaylists()}.show()}.show()}
    private fun showRenamePlaylistDialog(id:String,current:String){val input=EditText(this).apply{setText(current);setSingleLine(true)};AlertDialog.Builder(this).setTitle("Playlist adını değiştir").setView(input).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet"){_,_->playlistStore.rename(id,input.text.toString());renderPlaylists()}.show()}

    private fun playPlaylist(items:List<MusicTrack>){val c=controller?:return;if(items.isEmpty())return;c.setMediaItems(items.map(::toMediaItem));c.prepare();c.play();rememberRecent(items.first().id);updateMiniPlayer()}
    private fun playTrack(track:MusicTrack)=playPlaylist(listOf(track))
    private fun toMediaItem(track:MusicTrack)=MediaItem.Builder().setMediaId(track.id.toString()).setUri(track.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).build()).build()

    private fun addTrackList(items:List<MusicTrack>,surface:Int,muted:Int){items.forEach{track->val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(7),dp(6),dp(7));background=rounded(surface,14);setOnClickListener{playTrack(track)}};val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setImageResource(android.R.drawable.ic_media_play);background=rounded(Color.rgb(44,48,55),10)};row.addView(art,LinearLayout.LayoutParams(dp(54),dp(54)));val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(2),0)};info.addView(TextView(this).apply{text=track.title;textSize=14f;setTextColor(Color.WHITE);maxLines=1});info.addView(TextView(this).apply{text="${track.artist} • ${track.album}";textSize=11f;setTextColor(muted);setPadding(0,dp(4),0,0)});row.addView(info,LinearLayout.LayoutParams(0,-2,1f));row.addView(TextView(this).apply{text=if(favoriteIds.contains(track.id))"★"else"☆";textSize=22f;setTextColor(Color.rgb(30,215,96));gravity=Gravity.CENTER;setOnClickListener{toggleFavorite(track);renderHome()}},LinearLayout.LayoutParams(dp(44),dp(54)));row.addView(TextView(this).apply{text="+";textSize=21f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setOnClickListener{showQuickPlaylistPicker(track)}},LinearLayout.LayoutParams(dp(40),dp(54)));loadArtwork(track,art);content.addView(row,LinearLayout.LayoutParams(-1,dp(68)).apply{bottomMargin=dp(6)})}}
    private fun showQuickPlaylistPicker(track:MusicTrack){val lists=playlistStore.load();if(lists.isEmpty()){showCreatePlaylistDialog();return};val names=lists.map{it.name}.toTypedArray();AlertDialog.Builder(this).setTitle("Playlist'e ekle").setItems(names){_,which->playlistStore.addTrack(lists[which].id,track.id);status.text="${track.title} • ${lists[which].name} listesine eklendi"}.setNegativeButton("Vazgeç",null).show()}

    private fun addTrackRail(items:List<MusicTrack>,surface:Int,muted:Int){if(items.isEmpty()){addHint("Henüz içerik yok.",muted);return};val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};items.forEach{track->val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(5),dp(4),dp(5),dp(6));setOnClickListener{playTrack(track)}};val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setImageResource(android.R.drawable.ic_media_play);background=rounded(surface,14)};box.addView(art,LinearLayout.LayoutParams(dp(108),dp(108)));box.addView(TextView(this).apply{text=track.title;textSize=12f;setTextColor(Color.WHITE);maxLines=1});box.addView(TextView(this).apply{text=track.artist;textSize=11f;setTextColor(muted);maxLines=1});loadArtwork(track,art);row.addView(box,LinearLayout.LayoutParams(dp(118),-2))};content.addView(ScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(row)},LinearLayout.LayoutParams(-1,dp(158)).apply{bottomMargin=dp(18)})}
    private fun addAlbumRail(surface:Int,muted:Int){val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};tracks.distinctBy{it.album}.take(8).forEach{t->val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(5),dp(4),dp(5),dp(6));setOnClickListener{renderCollection(t.album,tracks.filter{it.album==t.album})}};val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setImageResource(android.R.drawable.ic_menu_gallery);background=rounded(surface,14)};box.addView(art,LinearLayout.LayoutParams(dp(116),dp(116)));box.addView(TextView(this).apply{text=t.album;textSize=12f;setTextColor(Color.WHITE);maxLines=1});box.addView(TextView(this).apply{text=t.artist;textSize=11f;setTextColor(muted)});loadArtwork(t,art);row.addView(box,LinearLayout.LayoutParams(dp(126),-2))};content.addView(ScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(row)},LinearLayout.LayoutParams(-1,dp(160)).apply{bottomMargin=dp(18)})}
    private fun addArtistRail(surface:Int,muted:Int){val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};tracks.distinctBy{it.artist}.take(8).forEach{t->val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(5),dp(4),dp(5),dp(6));setOnClickListener{renderCollection(t.artist,tracks.filter{it.artist==t.artist})}};val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setImageResource(android.R.drawable.ic_menu_myplaces);background=rounded(surface,60);clipToOutline=true};box.addView(art,LinearLayout.LayoutParams(dp(96),dp(96)));box.addView(TextView(this).apply{text=t.artist;textSize=12f;setTextColor(Color.WHITE);maxLines=1;gravity=Gravity.CENTER});loadArtwork(t,art);row.addView(box,LinearLayout.LayoutParams(dp(120),-2))};content.addView(ScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(row)},LinearLayout.LayoutParams(-1,dp(140)).apply{bottomMargin=dp(18)})}
    private fun addEmptyState(surface:Int,muted:Int){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(24),dp(30),dp(24),dp(30));background=rounded(surface,22)};box.addView(TextView(this).apply{text="♫";textSize=44f;setTextColor(Color.rgb(30,215,96));gravity=Gravity.CENTER});box.addView(TextView(this).apply{text="Müziğinizi keşfetmeye hazır";textSize=19f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setPadding(0,dp(8),0,dp(6))});box.addView(TextView(this).apply{text="Cihazındaki yerel parçalar burada güvenli ve çevrimdışı listelenir.";textSize=13f;setTextColor(muted);gravity=Gravity.CENTER});content.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(16)})}
    private fun addHint(text:String,muted:Int){content.addView(TextView(this).apply{this.text=text;textSize=13f;setTextColor(muted);setPadding(dp(4),dp(6),dp(4),dp(14))})}
    private fun addBack(action:()->Unit={renderHome()}){content.addView(TextView(this).apply{text="← Ana sayfa";textSize=14f;setTextColor(Color.rgb(30,215,96));gravity=Gravity.CENTER;setPadding(0,dp(22),0,dp(16));setOnClickListener{action()}})}
    private fun sectionTitle(text:String,color:Int)=TextView(this).apply{this.text=text;textSize=20f;setTextColor(color);typeface=Typeface.DEFAULT_BOLD;setPadding(0,dp(2),0,dp(10))}
    private fun chip(text:String,action:()->Unit)=TextView(this).apply{this.text=text;textSize=12f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;background=rounded(Color.rgb(34,38,44),18);setPadding(dp(13),0,dp(13),0);setOnClickListener{action()}}
    private fun buildMiniPlayer():View{val mini=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(6),dp(6),dp(6));background=rounded(Color.rgb(34,38,44),14)};mini.addView(TextView(this).apply{text="♫";textSize=24f;setTextColor(Color.rgb(30,215,96));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(46),dp(46)));val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,dp(6),0)};miniTitle=TextView(this).apply{text="LANU Music";textSize=13f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;maxLines=1};miniArtist=TextView(this).apply{text="Bir parça seç";textSize=11f;setTextColor(Color.rgb(157,164,174));maxLines=1};info.addView(miniTitle);info.addView(miniArtist);mini.addView(info,LinearLayout.LayoutParams(0,-2,1f));miniPlay=Button(this).apply{text="▶";textSize=17f;setTextColor(Color.WHITE);setBackgroundColor(Color.TRANSPARENT);setOnClickListener{controller?.let{if(it.isPlaying)it.pause()else it.play()}}};mini.addView(miniPlay,LinearLayout.LayoutParams(dp(52),dp(52)));return mini}
    private fun buildBottomBar():View{val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};bar.addView(TextView(this).apply{text="⌂\nAna Sayfa";textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.rgb(30,215,96));setOnClickListener{renderHome()}},LinearLayout.LayoutParams(0,-1,1f));bar.addView(TextView(this).apply{text="♫\nKitaplık";textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.rgb(157,164,174));setOnClickListener{renderCollection("Kitaplık",tracks)}},LinearLayout.LayoutParams(0,-1,1f));return bar}
    private fun toggleFavorite(track:MusicTrack){if(!favoriteIds.add(track.id))favoriteIds.remove(track.id);saveLocalState()}
    private fun rememberRecent(id:Long){recentIds.remove(id);recentIds.add(id);while(recentIds.size>30)recentIds.remove(recentIds.first());saveLocalState()}
    private fun recentTracks()=recentIds.asReversed().mapNotNull{id->tracks.firstOrNull{it.id==id}}
    private fun favoriteTracks()=favoriteIds.mapNotNull{id->tracks.firstOrNull{it.id==id}}.asReversed()
    private fun loadLocalState(){recentIds.addAll(readIds("recent"));favoriteIds.addAll(readIds("favorites"))}
    private fun saveLocalState(){prefs.edit().putString("recent",recentIds.joinToString(",")).putString("favorites",favoriteIds.joinToString(",")).apply()}
    private fun readIds(key:String)=prefs.getString(key,"").orEmpty().split(',').mapNotNull{it.toLongOrNull()}
    private fun loadArtwork(track:MusicTrack,view:ImageView){view.setImageResource(android.R.drawable.ic_menu_gallery);artworkExecutor.execute{val bitmap=runCatching{AlbumArtResolver.load(this,track.uri)}.getOrNull();runOnUiThread{if(!isFinishing&&bitmap!=null)view.setImageBitmap(bitmap)}}}
    private fun connectPlayer(){val token=SessionToken(this,ComponentName(this,PlaybackService::class.java));val future=MediaController.Builder(this,token).buildAsync();future.addListener({runCatching{controller=future.get();controller?.addListener(playerListener);updateMiniPlayer()}.onFailure{status.text="Oynatıcı bağlantısı kurulamadı"}},ContextCompat.getMainExecutor(this))}
    private fun updateMiniPlayer(){if(!::miniTitle.isInitialized)return;val item=controller?.currentMediaItem;miniTitle.text=item?.mediaMetadata?.title?:"LANU Music";miniArtist.text=item?.mediaMetadata?.artist?:"Bir parça seç";miniPlay.text=if(controller?.isPlaying==true)"Ⅱ"else"▶"}
    private fun ensureNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)else ensureAudioPermission()}
    private fun ensureAudioPermission(){val p=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE;if(ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED)loadDeviceMusic()else permissionLauncher.launch(p)}
    private fun loadDeviceMusic(){tracks.clear();val projection=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION);runCatching{contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,projection,"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,"${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use{c->val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val title=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);val artist=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);val album=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);val duration=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);while(c.moveToNext()){val tid=c.getLong(id);tracks+=MusicTrack(tid,c.getString(title)?:"Bilinmeyen parça",c.getString(artist)?:"Bilinmeyen sanatçı",c.getString(album)?:"Bilinmeyen albüm","${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$tid",c.getLong(duration))}}}.onFailure{status.text="Kitaplık hatası: ${it.message?:"bilinmeyen hata"}"};runOnUiThread{renderHome()}}
    private fun rounded(color:Int,radiusDp:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radiusDp).toFloat()}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
