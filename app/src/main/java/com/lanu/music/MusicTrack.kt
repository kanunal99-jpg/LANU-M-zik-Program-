package com.lanu.music

data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: String,
    val durationMs: Long
)
