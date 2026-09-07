package com.lanu.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LanuPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
    val createdAt: Long,
    val updatedAt: Long
)

class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("lanu_music", Context.MODE_PRIVATE)
    private val key = "playlists_v1"

    fun load(): List<LanuPlaylist> = runCatching {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val name = o.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                val ids = buildList {
                    val tracks = o.optJSONArray("trackIds") ?: JSONArray()
                    for (j in 0 until tracks.length()) {
                        tracks.optLong(j, -1L).takeIf { it > 0 }?.let(::add)
                    }
                }.distinct()
                add(LanuPlaylist(id, name, ids, o.optLong("createdAt"), o.optLong("updatedAt")))
            }
        }
    }.getOrElse { emptyList() }

    fun create(name: String): LanuPlaylist? {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(60)
        if (clean.isEmpty()) return null
        val now = System.currentTimeMillis()
        val playlist = LanuPlaylist(UUID.randomUUID().toString(), clean, emptyList(), now, now)
        save(load() + playlist)
        return playlist
    }

    fun rename(id: String, name: String): Boolean {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(60)
        if (clean.isEmpty()) return false
        val changed = load().map { if (it.id == id) it.copy(name = clean, updatedAt = System.currentTimeMillis()) else it }
        if (changed.none { it.id == id }) return false
        save(changed)
        return true
    }

    fun delete(id: String): Boolean {
        val current = load()
        val changed = current.filterNot { it.id == id }
        if (changed.size == current.size) return false
        save(changed)
        return true
    }

    fun addTrack(playlistId: String, trackId: Long): Boolean = updateTracks(playlistId) { ids ->
        if (ids.contains(trackId)) ids else ids + trackId
    }

    fun removeTrack(playlistId: String, trackId: Long): Boolean = updateTracks(playlistId) { ids -> ids.filterNot { it == trackId } }

    private fun updateTracks(id: String, transform: (List<Long>) -> List<Long>): Boolean {
        val current = load()
        var found = false
        val changed = current.map {
            if (it.id != id) it else {
                found = true
                it.copy(trackIds = transform(it.trackIds).distinct(), updatedAt = System.currentTimeMillis())
            }
        }
        if (!found) return false
        save(changed)
        return true
    }

    private fun save(playlists: List<LanuPlaylist>) {
        val array = JSONArray()
        playlists.forEach { p ->
            val o = JSONObject().put("id", p.id).put("name", p.name).put("createdAt", p.createdAt).put("updatedAt", p.updatedAt)
            o.put("trackIds", JSONArray().apply { p.trackIds.forEach { put(it) } })
            array.put(o)
        }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
