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
    private val backupKey = "playlists_v1_backup"
    private val maxPlaylists = 100
    private val maxNameLength = 60

    fun load(): List<LanuPlaylist> = parse(prefs.getString(key, null))
        ?: parse(prefs.getString(backupKey, null))
        ?: emptyList()

    fun create(name: String): LanuPlaylist? {
        val clean = normalizeName(name)
        if (clean.isEmpty()) return null
        val current = load()
        if (current.size >= maxPlaylists || current.any { it.name.equals(clean, ignoreCase = true) }) return null
        val now = System.currentTimeMillis()
        val playlist = LanuPlaylist(UUID.randomUUID().toString(), clean, emptyList(), now, now)
        save(current + playlist)
        return playlist
    }

    fun rename(id: String, name: String): Boolean {
        val clean = normalizeName(name)
        if (clean.isEmpty()) return false
        val current = load()
        if (current.any { it.id != id && it.name.equals(clean, ignoreCase = true) }) return false
        val changed = current.map { if (it.id == id) it.copy(name = clean, updatedAt = System.currentTimeMillis()) else it }
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

    fun addTrack(playlistId: String, trackId: Long): Boolean {
        if (trackId <= 0) return false
        return updateTracks(playlistId) { ids -> if (ids.contains(trackId)) ids else ids + trackId }
    }

    fun removeTrack(playlistId: String, trackId: Long): Boolean =
        updateTracks(playlistId) { ids -> ids.filterNot { it == trackId } }

    fun moveTrack(playlistId: String, trackId: Long, targetIndex: Int): Boolean = updateTracks(playlistId) { ids ->
        val sourceIndex = ids.indexOf(trackId)
        if (sourceIndex < 0) return@updateTracks ids
        val moved = ids.toMutableList().apply {
            removeAt(sourceIndex)
            add(targetIndex.coerceIn(0, size), trackId)
        }
        moved
    }

    private fun updateTracks(id: String, transform: (List<Long>) -> List<Long>): Boolean {
        val current = load()
        var found = false
        var changedValue = false
        val changed = current.map {
            if (it.id != id) it else {
                found = true
                val nextIds = transform(it.trackIds).distinct()
                changedValue = nextIds != it.trackIds
                if (changedValue) it.copy(trackIds = nextIds, updatedAt = System.currentTimeMillis()) else it
            }
        }
        if (!found || !changedValue) return false
        save(changed)
        return true
    }

    private fun normalizeName(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").take(maxNameLength)

    private fun parse(raw: String?): List<LanuPlaylist>? = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            val seenIds = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val name = normalizeName(o.optString("name"))
                if (id.isEmpty() || name.isEmpty() || !seenIds.add(id)) continue
                val ids = buildList {
                    val tracks = o.optJSONArray("trackIds") ?: JSONArray()
                    for (j in 0 until tracks.length()) {
                        tracks.optLong(j, -1L).takeIf { it > 0 }?.let(::add)
                    }
                }.distinct()
                add(LanuPlaylist(id, name, ids, o.optLong("createdAt"), o.optLong("updatedAt")))
                if (size >= maxPlaylists) break
            }
        }
    }.getOrNull()

    private fun save(playlists: List<LanuPlaylist>) {
        val array = JSONArray()
        playlists.take(maxPlaylists).forEach { p ->
            val o = JSONObject()
                .put("id", p.id)
                .put("name", normalizeName(p.name))
                .put("createdAt", p.createdAt)
                .put("updatedAt", p.updatedAt)
            o.put("trackIds", JSONArray().apply { p.trackIds.filter { it > 0 }.distinct().forEach { put(it) } })
            array.put(o)
        }
        val previous = prefs.getString(key, null)
        prefs.edit()
            .apply {
                if (!previous.isNullOrBlank()) putString(backupKey, previous)
                putString(key, array.toString())
            }
            .apply()
    }
}
