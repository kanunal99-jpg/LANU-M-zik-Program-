package com.lanu.music

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real public catalog detail lookups. Metadata only; playable media remains preview URLs. */
object CatalogDetailsClient {
    data class ArtistPage(val artistId: Long, val name: String, val albums: List<String>, val tracks: List<MusicTrack>)
    data class AlbumPage(val collectionId: Long, val name: String, val artist: String, val tracks: List<MusicTrack>)

    fun artist(name: String): ArtistPage? {
        val query = URLEncoder.encode(name.trim(), "UTF-8")
        val artistJson = get("https://itunes.apple.com/search?term=$query&country=tr&media=music&entity=musicArtist&limit=10") ?: return null
        val artistResults = artistJson.optJSONArray("results") ?: return null
        val artist = (0 until artistResults.length())
            .mapNotNull { artistResults.optJSONObject(it) }
            .firstOrNull { it.optString("artistName").equals(name.trim(), true) }
            ?: artistResults.optJSONObject(0)
            ?: return null
        val artistId = artist.optLong("artistId", 0L)
        val artistName = artist.optString("artistName", name).trim()
        if (artistId == 0L || artistName.isBlank()) return null

        val albumsJson = get("https://itunes.apple.com/lookup?id=$artistId&country=tr&entity=album&limit=200")
        val albums = albumsJson?.optJSONArray("results")?.let { parseAlbumNames(it) }.orEmpty()

        val tracksJson = get("https://itunes.apple.com/lookup?id=$artistId&country=tr&entity=song&limit=200")
        val tracks = tracksJson?.optJSONArray("results")?.let { parseTracks(it) }.orEmpty()
        return ArtistPage(artistId, artistName, albums, tracks)
    }

    fun album(name: String, artist: String? = null): AlbumPage? {
        val term = if (artist.isNullOrBlank()) name else "$name $artist"
        val query = URLEncoder.encode(term.trim(), "UTF-8")
        val albumJson = get("https://itunes.apple.com/search?term=$query&country=tr&media=music&entity=album&limit=20") ?: return null
        val results = albumJson.optJSONArray("results") ?: return null
        val album = (0 until results.length())
            .mapNotNull { results.optJSONObject(it) }
            .firstOrNull { it.optString("collectionName").equals(name.trim(), true) && (artist.isNullOrBlank() || it.optString("artistName").equals(artist.trim(), true)) }
            ?: (0 until results.length()).mapNotNull { results.optJSONObject(it) }.firstOrNull()
            ?: return null

        val collectionId = album.optLong("collectionId", 0L)
        val albumName = album.optString("collectionName", name).trim()
        val albumArtist = album.optString("artistName", artist ?: "Bilinmeyen sanatçı").trim()
        if (collectionId == 0L || albumName.isBlank()) return null

        val lookup = get("https://itunes.apple.com/lookup?id=$collectionId&country=tr&entity=song&limit=200")
        val tracks = lookup?.optJSONArray("results")?.let { parseTracks(it) }.orEmpty()
        val filtered = tracks.filter { it.album.equals(albumName, true) && it.artist.equals(albumArtist, true) }
        return AlbumPage(collectionId, albumName, albumArtist, if (filtered.isNotEmpty()) filtered else tracks)
    }

    private fun parseAlbumNames(arr: JSONArray): List<String> = buildList {
        for (i in 0 until arr.length()) {
            val x = arr.optJSONObject(i) ?: continue
            val name = x.optString("collectionName").trim()
            if (name.isNotBlank()) add(name)
        }
    }.distinct()

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

    private fun parseTracks(arr: JSONArray): List<MusicTrack> = buildList {
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
