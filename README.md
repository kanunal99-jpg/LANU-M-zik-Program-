# LANU Music

Spotify-benzeri, bağımsız ve ücretsiz Android müzik oynatıcı.

## Hedef
- Kişisel / lisanslı müzik kütüphanesi
- Arka planda ve ekran kilitliyken oynatma
- Çevrimdışı indirilen müzikleri oynatma
- Favoriler, playlistler, sanatçı ve albüm görünümü
- Kendi müziklerini uygulamaya senkronize etme
- GitHub Actions ile otomatik ücretsiz APK üretimi

> Not: Spotify'ın lisanslı ses kataloğu, DRM'i veya Premium erişim kısıtlamaları kopyalanmaz/aşılmaz. Uygulama yalnızca kullanıcının sahip olduğu veya kullanım hakkı bulunan içerikleri destekler.

## Teknoloji
Android Native + Kotlin + Media3/ExoPlayer.

## Doğrulanmış mevcut durum
- Media3 ExoPlayer + MediaSession playback service aktif.
- Android foreground media playback izinleri tanımlı.
- Cihazdaki gerçek MediaStore ses kütüphanesi kullanılır; sahte şarkı/stream kataloğu eklenmez.
- Playlist, favori, kuyruk, shuffle/repeat, seek ve albüm kapağı fallback'i mevcut.
- GitHub Actions statik smoke kontrolleri + debug APK build + SHA-256 checksum üretimi yapar.
- Gemini API anahtarı APK içine gömülmez. Canlı Gemini entegrasyonu için güvenli bir backend/provider katmanı gereklidir; şu an güvenli yerel katalog/fallback çalışır.

## 📱 En Son Doğrulanmış APK

**CI Run:** `#50`  
**Kaynak commit:** `063e16b205430dd2f098c71a744add493081247f`  
**APK:** `lanu-music-main-063e16b.apk`  
**SHA-256:** `bf28807d94da1faa15577fc51607db987b3228c5238b706d622921ea08e37b82`

👉 **[⬇️ LANU Music APK'yı indir](https://github.com/kanunal99-jpg/LANU-M-zik-Program-/raw/refs/heads/main/releases/lanu-music-main-063e16b.apk)**

👉 **[SHA-256 dosyasını görüntüle](https://github.com/kanunal99-jpg/LANU-M-zik-Program-/blob/main/releases/lanu-music-main-063e16b.apk.sha256)**
