package com.example.spetify

import kotlinx.serialization.Serializable

@Serializable
data class LrcLibResponse(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

data class LyricsLine(
    val timestamp: Long, // milliseconds
    val content: String
)
