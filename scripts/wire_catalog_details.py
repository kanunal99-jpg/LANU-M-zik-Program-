from pathlib import Path

path = Path('app/src/main/java/com/lanu/music/MainActivity.kt')
s = path.read_text()

if 'private fun addCatalogEntityLinks' in s and 'CatalogDetailsClient.artist' in s and 'CatalogDetailsClient.album' in s:
    print('Catalog detail navigation already wired; no source rewrite needed')
    raise SystemExit(0)

old = '''    private fun renderCatalogCollection(query:String,items:List<MusicTrack>){
        content.removeAllViews()
        val muted=Color.rgb(157,164,174); val surface=Color.rgb(25,28,33); val green=Color.rgb(30,215,96)
        content.addView(TextView(this).apply{text="Gerçek katalog";textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD})
        content.addView(TextView(this).apply{text="“$query” • ${items.size} sonuç • Apple/iTunes gerçek katalog verisi";textSize=13f;setTextColor(muted);setPadding(0,dp(4),0,dp(16))})
        if(items.isEmpty()) addHint("Bu aramada sonuç bulunamadı. İnternet bağlantısını kontrol edip başka bir sanatçı veya şarkı deneyin.",muted)
        else {
            content.addView(TextView(this).apply{text="▶ Önizlemeler  •  kısa tanıtım örnekleri";textSize=12f;setTextColor(green);setPadding(0,0,0,dp(10))})
            addTrackList(items,surface,muted)
        }
        content.addView(TextView(this).apply{text="Önizlemeler yalnızca katalog içeriğini tanıtmak içindir.";textSize=11f;setTextColor(muted);setPadding(0,dp(10),0,dp(8))})
        addBack()
    }
'''

new = '''    private fun renderCatalogCollection(query:String,items:List<MusicTrack>){
        content.removeAllViews()
        val muted=Color.rgb(157,164,174); val surface=Color.rgb(25,28,33); val green=Color.rgb(30,215,96)
        content.addView(TextView(this).apply{text="Gerçek katalog";textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD})
        content.addView(TextView(this).apply{text="“$query” • ${items.size} sonuç • Apple/iTunes gerçek katalog verisi";textSize=13f;setTextColor(muted);setPadding(0,dp(4),0,dp(12))})
        if(items.isEmpty()) addHint("Bu aramada sonuç bulunamadı. İnternet bağlantısını kontrol edip başka bir sanatçı veya şarkı deneyin.",muted)
        else {
            addCatalogEntityLinks(items,surface,muted,green)
            content.addView(TextView(this).apply{text="▶ Önizlemeler  •  kısa tanıtım örnekleri";textSize=12f;setTextColor(green);setPadding(0,dp(10),0,dp(10))})
            addTrackList(items,surface,muted)
        }
        content.addView(TextView(this).apply{text="Önizlemeler yalnızca katalog içeriğini tanıtmak içindir.";textSize=11f;setTextColor(muted);setPadding(0,dp(10),0,dp(8))})
        addBack()
    }

    private fun addCatalogEntityLinks(items:List<MusicTrack>,surface:Int,muted:Int,green:Int){
        val artists=items.map{it.artist}.filter{it.isNotBlank()}.distinct().take(8)
        val albums=items.map{it.album to it.artist}.filter{it.first.isNotBlank()}.distinctBy{it.first.lowercase()}.take(12)
        if(artists.isNotEmpty()){
            content.addView(sectionTitle("Sanatçı sayfaları",Color.WHITE))
            artists.forEach{artist->
                content.addView(Button(this).apply{
                    text=artist; setTextColor(Color.WHITE); textSize=14f; gravity=Gravity.START or Gravity.CENTER_VERTICAL
                    background=rounded(surface,14); setPadding(dp(14),0,dp(14),0)
                    setOnClickListener{loadArtistPage(artist){renderCatalogCollection("$artist",items)}}
                },LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(6)})
            }
        }
        if(albums.isNotEmpty()){
            content.addView(sectionTitle("Albüm sayfaları",Color.WHITE))
            albums.forEach{(album,artist)->
                content.addView(Button(this).apply{
                    text="$album\\n$artist"; setTextColor(Color.WHITE); textSize=13f; gravity=Gravity.START or Gravity.CENTER_VERTICAL
                    background=rounded(surface,14); setPadding(dp(14),0,dp(14),0)
                    setOnClickListener{loadAlbumPage(album,artist){renderCatalogCollection("$album",items)}}
                },LinearLayout.LayoutParams(-1,dp(58)).apply{bottomMargin=dp(6)})
            }
        }
    }

    private fun loadArtistPage(name:String,back:()->Unit){
        status.text="Sanatçı sayfası yükleniyor…"
        catalogExecutor.execute{
            val page=runCatching{CatalogDetailsClient.artist(name)}.getOrNull()
            runOnUiThread{
                if(isFinishing)return@runOnUiThread
                if(page==null) { renderError("Sanatçı bilgisi alınamadı. Gerçek katalog korunuyor; tekrar deneyebilirsin.",back) }
                else renderArtistPage(page,back)
            }
        }
    }

    private fun renderArtistPage(page:CatalogDetailsClient.ArtistPage,back:()->Unit){
        content.removeAllViews()
        val muted=Color.rgb(157,164,174); val surface=Color.rgb(25,28,33); val green=Color.rgb(30,215,96)
        content.addView(TextView(this).apply{text=page.name;textSize=28f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD})
        content.addView(TextView(this).apply{text="Gerçek katalog sanatçı sayfası • ${page.tracks.size} önizleme";textSize=13f;setTextColor(muted);setPadding(0,dp(4),0,dp(14))})
        if(page.albums.isNotEmpty()){
            content.addView(sectionTitle("Albümler",Color.WHITE))
            page.albums.take(30).forEach{album->
                content.addView(Button(this).apply{
                    text=album;setTextColor(Color.WHITE);textSize=14f;gravity=Gravity.START or Gravity.CENTER_VERTICAL
                    background=rounded(surface,14);setPadding(dp(14),0,dp(14),0)
                    setOnClickListener{loadAlbumPage(album,page.name){renderArtistPage(page,back)}}
                },LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(6)})
            }
        }
        if(page.tracks.isNotEmpty()){
            content.addView(sectionTitle("Şarkılar",Color.WHITE)); addTrackList(page.tracks,surface,muted)
        } else addHint("Bu sanatçı için oynatılabilir önizleme bulunamadı.",muted)
        addBack(back)
    }

    private fun loadAlbumPage(name:String,artist:String?,back:()->Unit){
        status.text="Albüm sayfası yükleniyor…"
        catalogExecutor.execute{
            val page=runCatching{CatalogDetailsClient.album(name,artist)}.getOrNull()
            runOnUiThread{
                if(isFinishing)return@runOnUiThread
                if(page==null) renderError("Albüm bilgisi alınamadı. Gerçek katalog korunuyor; tekrar deneyebilirsin.",back)
                else renderAlbumPage(page,back)
            }
        }
    }

    private fun renderAlbumPage(page:CatalogDetailsClient.AlbumPage,back:()->Unit){
        content.removeAllViews()
        val muted=Color.rgb(157,164,174); val surface=Color.rgb(25,28,33); val green=Color.rgb(30,215,96)
        content.addView(TextView(this).apply{text=page.name;textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD})
        content.addView(TextView(this).apply{text=page.artist;textSize=14f;setTextColor(green);setPadding(0,dp(4),0,dp(4))})
        content.addView(TextView(this).apply{text="Gerçek katalog albüm sayfası • ${page.tracks.size} önizleme";textSize=12f;setTextColor(muted);setPadding(0,0,0,dp(14))})
        if(page.tracks.isNotEmpty()) addTrackList(page.tracks,surface,muted) else addHint("Bu albüm için oynatılabilir önizleme bulunamadı.",muted)
        addBack(back)
    }

    private fun renderError(message:String,back:()->Unit){
        content.removeAllViews()
        val muted=Color.rgb(157,164,174)
        addHint(message,muted)
        addBack(back)
    }
'''

if old not in s:
    raise SystemExit('renderCatalogCollection target not found; refusing unsafe rewrite')

s=s.replace(old,new,1)
path.write_text(s)
print('Catalog detail navigation wired into MainActivity')
