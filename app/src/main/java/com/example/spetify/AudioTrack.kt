package com.example.spetify

import android.net.Uri

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String = "Single",
    val duration: Long,
    val contentUri: Uri,
    val customArtUri: String? = null,
    val fileName: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val genre: String? = null,
    val lyricist: String? = null,
    val dateAdded: Long = 0,
    val artVersion: Long = 0
)
