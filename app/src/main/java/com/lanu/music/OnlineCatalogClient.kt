package com.lanu.music

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real public music metadata source. Preview URLs are short promotional samples, not full tracks. */
object OnlineCatalogClient {
    fun search(query: String, limit: Int = 25): List<MusicTrack> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL("https://itunes.apple.com/search?term=$encoded&country=tr&media=music&entity=song&limit=$limit&explicit=No")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val preview = item.optString("previewUrl").trim()
                    val title = item.optString("trackName").trim()
                    val artist = item.optString("artistName").trim()
                    if (preview.isBlank() || title.isBlank() || artist.isBlank()) continue
                    val id = item.optLong("trackId", 0L)
                    if (id == 0L) continue
                    add(MusicTrack(
                        id = -id,
                        title = title,
                        artist = artist,
                        album = item.optString("collectionName", "Bilinmeyen albüm"),
                        uri = preview,
                        durationMs = item.optLong("trackTimeMillis", 0L),
                        albumArtUri = item.optString("artworkUrl100").takeIf { it.isNotBlank() }
                    ))
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
