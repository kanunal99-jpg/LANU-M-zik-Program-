package com.lanu.music

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real public music metadata source. Preview URLs are optional; no fake playable source is created. */
object OnlineCatalogClient {
    private const val TAG = "LANU-Catalog"

    fun search(query: String, limit: Int = 25): List<MusicTrack> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()

        // Primary: Turkish storefront. Alternative: US storefront if TR has no usable metadata.
        val primary = request(clean, "tr", limit)
        if (primary.isNotEmpty()) return primary
        return request(clean, "us", limit)
    }

    private fun request(query: String, country: String, limit: Int): List<MusicTrack> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://itunes.apple.com/search?term=$encoded&country=$country&media=music&entity=song&limit=$limit&explicit=No")
        val connection = try {
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LANU-Music/1.0")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Catalog connection failed: $country", e)
            return emptyList()
        }

        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Catalog HTTP ${connection.responseCode}: $country")
                return emptyList()
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val title = item.optString("trackName").trim()
                    val artist = item.optString("artistName").trim()
                    val id = item.optLong("trackId", 0L)
                    if (id == 0L || title.isBlank() || artist.isBlank()) continue

                    val preview = item.optString("previewUrl").trim()
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
        } catch (e: Exception) {
            Log.w(TAG, "Catalog response parsing failed: $country", e)
            emptyList()
        } finally {
            connection.disconnect()
        }
    }
}
