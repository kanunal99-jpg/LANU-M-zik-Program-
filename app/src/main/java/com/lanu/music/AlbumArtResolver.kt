package com.lanu.music

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Resolves embedded album artwork from a local music URI.
 * Kept independent from the UI so artwork loading can later be moved off the
 * main thread and cached without changing playback behavior.
 */
object AlbumArtResolver {
    fun load(context: Context, uri: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.embeddedPicture?.let { bytes ->
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
