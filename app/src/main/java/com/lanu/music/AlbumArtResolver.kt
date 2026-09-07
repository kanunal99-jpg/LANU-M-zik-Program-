package com.lanu.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore

/**
 * Resolves local album artwork without network access.
 * Priority: embedded artwork in the audio file, then Android's MediaStore
 * album-art provider. All failures safely return null.
 */
object AlbumArtResolver {
    fun load(context: Context, uri: String): Bitmap? {
        val source = Uri.parse(uri)
        val embedded = loadEmbedded(context, source)
        if (embedded != null) return embedded
        return loadMediaStoreAlbumArt(context, source)
    }

    private fun loadEmbedded(context: Context, source: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, source)
            retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun loadMediaStoreAlbumArt(context: Context, source: Uri): Bitmap? {
        if (source.scheme != "content") return null
        return runCatching {
            context.contentResolver.query(
                source,
                arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val albumId = cursor.getLong(0)
                if (albumId <= 0L) return@use null
                val artUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                context.contentResolver.openInputStream(artUri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        }.getOrNull()
    }
}
