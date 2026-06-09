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

    suspend fun fetchLyrics(track: AudioTrack): LrcLibResponse? = withContext(Dispatchers.IO) {
        // Sanitize search terms
        var artist = track.artist.takeIf { !it.contains("unknown", ignoreCase = true) } ?: ""
        var title = track.title.substringBefore(" (").substringBefore(" [") // Remove extra tags
        val album = track.album.takeIf { !it.contains("unknown", ignoreCase = true) && it != "Single" } ?: ""

        artist = java.text.Normalizer.normalize(artist, java.text.Normalizer.Form.NFC)
        title = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFC)

        if ((artist.isBlank() || artist == "Unknown Artist") && title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            title = parts[0].trim()
            artist = parts[1].trim()
        }
        
        // Strategy: Fast Exact -> Lenient Search -> Aggressive Search
        
        // 1. Exact "get" (Try with short timeout first)
        val getUrl = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("track_name", title)
            ?.addQueryParameter("artist_name", artist)
            ?.addQueryParameter("duration", (track.duration / 1000).toString())
            ?.build()

        if (getUrl != null) {
            val response = performRequest(Request.Builder()
                .url(getUrl)
                .header("User-Agent", "SPETify/1.1.0")
                .build())
            if (response != null) return@withContext response
        }

        // 2. Lenient Search (Title + Artist)
        val searchUrl = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("track_name", title)
            ?.addQueryParameter("artist_name", artist)
            ?.build()
        
        if (searchUrl != null) {
            val body = performRequestRaw(Request.Builder().url(searchUrl).build())
            if (body != null) {
                try {
                    val results = json.decodeFromString<List<LrcLibResponse>>(body)
                    val match = results.firstOrNull { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
                    if (match != null) return@withContext match
                } catch (e: Exception) { /* ignore */ }
            }
        }

        // 3. Last Resort: Global Search Query
        val queryUrl = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", if (artist.isNotBlank()) "$artist $title" else title)
            ?.build()

        if (queryUrl != null) {
            val body = performRequestRaw(Request.Builder().url(queryUrl).build())
            if (body != null) {
                try {
                    val results = json.decodeFromString<List<LrcLibResponse>>(body)
                    return@withContext results.firstOrNull { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
                } catch (e: Exception) { /* ignore */ }
            }
        }

        null
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

    private fun performRequest(request: Request): LrcLibResponse? {
        val body = performRequestRaw(request) ?: return null
        return try {
            json.decodeFromString<LrcLibResponse>(body)
        } catch (e: Exception) {
            android.util.Log.e("LyricsService", "JSON parse error: ${e.message}")
            null
        }
    }

    suspend fun searchLyricsManual(title: String, artist: String): LrcLibResponse? = withContext(Dispatchers.IO) {
        val url = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("track_name", title)
            ?.addQueryParameter("artist_name", artist)
            ?.build() ?: return@withContext null

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SPETify/1.0.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val results = json.decodeFromString<List<LrcLibResponse>>(body)
                return@withContext results.firstOrNull { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
            }
        } catch (e: Exception) {
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
