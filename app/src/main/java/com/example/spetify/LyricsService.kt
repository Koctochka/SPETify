package com.example.spetify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LyricsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    suspend fun fetchLyricsList(track: AudioTrack): List<LrcLibResponse> = withContext(Dispatchers.IO) {
        var artist = track.artist.takeIf { !it.contains("unknown", ignoreCase = true) } ?: ""
        var title = track.title.substringBefore(" (").substringBefore(" [")
        
        artist = java.text.Normalizer.normalize(artist, java.text.Normalizer.Form.NFC)
        title = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFC)

        val searchUrl = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("track_name", title)
            ?.addQueryParameter("artist_name", artist)
            ?.build()
        
        val results = mutableListOf<LrcLibResponse>()
        
        if (searchUrl != null) {
            val body = performRequestRaw(Request.Builder().url(searchUrl).build())
            if (body != null) {
                try {
                    results.addAll(json.decodeFromString<List<LrcLibResponse>>(body))
                } catch (e: Exception) { /* ignore */ }
            }
        }

        // If very few results, try global search
        if (results.size < 3) {
            val queryUrl = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("q", if (artist.isNotBlank()) "$artist $title" else title)
                ?.build()

            if (queryUrl != null) {
                val body = performRequestRaw(Request.Builder().url(queryUrl).build())
                if (body != null) {
                    try {
                        val globalResults = json.decodeFromString<List<LrcLibResponse>>(body)
                        results.addAll(globalResults.filter { gr -> results.none { it.id == gr.id } })
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        }

        results.filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
    }

    suspend fun searchLyricsManualList(title: String, artist: String): List<LrcLibResponse> = withContext(Dispatchers.IO) {
        val url = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("track_name", title)
            ?.addQueryParameter("artist_name", artist)
            ?.build() ?: return@withContext emptyList()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SPETify/1.1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString<List<LrcLibResponse>>(body)
                    .filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun performRequestRaw(request: Request): String? {
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            android.util.Log.e("LyricsService", "Network request failed: ${e.message}")
            null
        }
    }

    fun parseSyncedLyrics(lrc: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val regex = "\\[(\\d+):(\\d+\\.\\d+)\\](.*)".toRegex()
        
        lrc.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toDouble()
                val content = match.groupValues[3].trim()
                val timestamp = (min * 60 * 1000) + (sec * 1000).toLong()
                lines.add(LyricsLine(timestamp, content))
            }
        }
        return lines
    }
}
