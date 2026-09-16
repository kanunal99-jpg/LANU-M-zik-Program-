package com.lanu.music

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Public catalog detail lookups. Metadata only; playable media remains the catalog preview URL. */
object CatalogDetailsClient {
    data class ArtistPage(val artistId: Long, val name: String, val albums: List<String>, val tracks: List<MusicTrack>)
    data class AlbumPage(val collectionId: Long, val name: String, val artist: String, val tracks: List<MusicTrack>)

    fun artist(name: String): ArtistPage? {
        val query = URLEncoder.encode(name.trim(), "UTF-8")
        val json = get("https://itunes.apple.com/search?term=$query&country=tr&media=music&entity=song&limit=50") ?: return null
        val arr = json.optJSONArray("results") ?: return null
        val tracks = parseTracks(arr)
        val first = tracks.firstOrNull() ?: return null
        val artistId = arr.optJSONObject(0)?.optLong("artistId", 0L) ?: 0L
        return ArtistPage(artistId, first.artist, tracks.map { it.album }.distinct(), tracks)
    }

    fun album(name: String, artist: String? = null): AlbumPage? {
        val term = if (artist.isNullOrBlank()) name else "$name $artist"
        val query = URLEncoder.encode(term.trim(), "UTF-8")
        val json = get("https://itunes.apple.com/search?term=$query&country=tr&media=music&entity=song&limit=50") ?: return null
        val arr = json.optJSONArray("results") ?: return null
        val tracks = parseTracks(arr)
        val first = tracks.firstOrNull() ?: return null
        val matching = tracks.filter { it.album.equals(name, true) }
        val chosen = if (matching.isNotEmpty()) matching else tracks.take(1)
        val firstRaw = arr.optJSONObject(0)
        return AlbumPage(firstRaw?.optLong("collectionId", 0L) ?: 0L, first.album, first.artist, chosen)
    }

    private fun get(url: String): JSONObject? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (c.responseCode !in 200..299) null
            else c.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } catch (_: Exception) { null } finally { c.disconnect() }
    }

    private fun parseTracks(arr: org.json.JSONArray): List<MusicTrack> = buildList {
        for (i in 0 until arr.length()) {
            val x = arr.optJSONObject(i) ?: continue
            val id = x.optLong("trackId", 0L)
            val title = x.optString("trackName").trim()
            val artist = x.optString("artistName").trim()
            val preview = x.optString("previewUrl").trim()
            if (id == 0L || title.isBlank() || artist.isBlank() || preview.isBlank()) continue
            add(MusicTrack(-id, title, artist, x.optString("collectionName", "Bilinmeyen albüm"), preview, x.optLong("trackTimeMillis", 0L), x.optString("artworkUrl100").takeIf { it.isNotBlank() }))
        }
    }
}
