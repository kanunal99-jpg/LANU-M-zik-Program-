package com.lanu.music

/**
 * Kotlin's asReversed() is available for List, not Set.
 * Keep insertion-order collections usable with the same API without
 * changing the existing playback/history model.
 */
fun <T> Set<T>.asReversed(): List<T> = toList().asReversed()
