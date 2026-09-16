package com.lanu.music

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real public catalog detail lookups. Metadata only; playable media remains preview URLs. */
object CatalogDetailsClient {
    private const val TAG = "LANU-Catalog"

    data class ArtistPage(val artistId: Long, val name: String, val albums: List<String>, val tracks: List<MusicTrack>)
    data class AlbumPage(val collectionId: Long, val name: String, val artist: String, val tracks: List<MusicTrack>)

    fun artist(name: String): ArtistPage? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val query = encode(cleanName)
        val artistJson = get("https://itunes.apple.com/search?term=$query&country=tr&media=music&entity=musicArtist&limit=10") ?: return null
        val artistResults = artistJson.optJSONArray("results") ?: return null
        val artist = (0 until artistResults.length())
            .mapNotNull { artistResults.optJSONObject(it) }
            .firstOrNull { it.optString("artistName").equals(cleanName, true) }
            ?: artistResults.optJSONObject(0)
            ?: return null
        val artistId = artist.optLong("artistId", 0L)
        val artistName = artist.optString("artistName", cleanName).trim()
        if (artistId == 0L || artistName.isBlank()) return null

        // Main: artist lookup. Alternative: artist-scoped album search.
        val albumsLookup = get("https://itunes.apple.com/lookup?id=$artistId&country=tr&entity=album&limit=200")
        val albums = albumsLookup?.optJSONArray("results")?.let { parseAlbumNames(it) }
            .orEmpty()
            .ifEmpty {
                searchJson(artistName, "album", "artistTerm", 200)?.optJSONArray("results")?.let { parseAlbumNames(it) }.orEmpty()
            }

        // Main: artist lookup. Alternative: artist-scoped song search.
        val tracksLookup = get("https://itunes.apple.com/lookup?id=$artistId&country=tr&entity=song&limit=200")
        val tracks = tracksLookup?.optJSONArray("results")?.let { parseTracks(it) }
            .orEmpty()
            .ifEmpty {
                searchJson(artistName, "song", "artistTerm", 200)?.optJSONArray("results")?.let { parseTracks(it) }.orEmpty()
            }
        return ArtistPage(artistId, artistName, albums, tracks)
    }

    fun album(name: String, artist: String? = null): AlbumPage? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val cleanArtist = artist?.trim().orEmpty()
        val term = if (cleanArtist.isBlank()) cleanName else "$cleanName $cleanArtist"
        val albumJson = get("https://itunes.apple.com/search?term=${encode(term)}&country=tr&media=music&entity=album&limit=20") ?: return null
        val results = albumJson.optJSONArray("results") ?: return null
        val album = (0 until results.length())
            .mapNotNull { results.optJSONObject(it) }
            .firstOrNull {
                it.optString("collectionName").equals(cleanName, true) &&
                    (cleanArtist.isBlank() || it.optString("artistName").equals(cleanArtist, true))
            }
            ?: (0 until results.length()).mapNotNull { results.optJSONObject(it) }.firstOrNull()
            ?: return null

        val collectionId = album.optLong("collectionId", 0L)
        val albumName = album.optString("collectionName", cleanName).trim()
        val albumArtist = album.optString("artistName", cleanArtist.ifBlank { "Bilinmeyen sanatçı" }).trim()
        if (collectionId == 0L || albumName.isBlank()) return null

        // Main: collection lookup. Alternative: song search constrained by album/artist terms.
        val lookup = get("https://itunes.apple.com/lookup?id=$collectionId&country=tr&entity=song&limit=200")
        val lookupTracks = lookup?.optJSONArray("results")?.let { parseTracks(it) }.orEmpty()
        val filtered = lookupTracks.filter { it.album.equals(albumName, true) && it.artist.equals(albumArtist, true) }
        val tracks = if (filtered.isNotEmpty()) filtered else lookupTracks.ifEmpty {
            searchJson("$albumName $albumArtist", "song", null, 50)?.optJSONArray("results")?.let { arr ->
                parseTracks(arr).filter { it.album.equals(albumName, true) && it.artist.equals(albumArtist, true) }
            }.orEmpty()
        }
        return AlbumPage(collectionId, albumName, albumArtist, tracks)
    }

    private fun searchJson(term: String, entity: String, attribute: String?, limit: Int): JSONObject? {
        val attributePart = attribute?.let { "&attribute=${encode(it)}" }.orEmpty()
        return get("https://itunes.apple.com/search?term=${encode(term)}&country=tr&media=music&entity=$entity$attributePart&limit=$limit")
    }

    private fun encode(value: String): String = URLEncoder.encode(value.trim(), "UTF-8")

    private fun parseAlbumNames(arr: JSONArray): List<String> = buildList {
        for (i in 0 until arr.length()) {
            val x = arr.optJSONObject(i) ?: continue
            val name = x.optString("collectionName").trim()
            if (name.isNotBlank()) add(name)
        }
    }.distinct()

    private fun get(url: String): JSONObject? {
        val c = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Catalog connection setup failed", e)
            return null
        }
        return try {
            if (c.responseCode !in 200..299) {
                Log.w(TAG, "Catalog HTTP ${c.responseCode}")
                null
            } else {
                c.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Catalog request failed", e)
            null
        } finally {
            c.disconnect()
        }
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
