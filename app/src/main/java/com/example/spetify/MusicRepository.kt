package com.example.spetify

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DirectoryContent(
    val folders: List<Folder>,
    val tracks: List<AudioTrack>,
    val totalDuration: Long,
    val path: String,
    val totalSubfoldersRecursive: Int = 0,
    val totalTracksRecursive: Int = 0,
    val allTracksRecursive: List<AudioTrack> = emptyList()
)

private data class CachedMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val albumArtist: String? = null,
    val composer: String? = null,
    val genre: String? = null,
    val lyricist: String? = null
)

class MusicRepository(private val context: Context) {
    private val playlistDao = AppDatabase.getDatabase(context).playlistDao()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.MINUTES)
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(15, TimeUnit.MINUTES)
        .build()

    private val vocalApi = Retrofit.Builder()
        .baseUrl("http://157.22.252.140:8000") // 192.168.1.15
        .client(okHttpClient)
        .build()
        .create(VocalRemoverApi::class.java)

    companion object {
        private val metadataCache = ConcurrentHashMap<String, CachedMetadata>()
    }

    fun fetchAudioFiles(directoryUri: Uri? = null): List<AudioTrack> {
        return if (directoryUri == null) {
            fetchFromMediaStore()
        } else {
            // This is still blocking for Now Playing list, but we'll optimize it later if needed
            runBlocking { fetchFromDocumentTreeFast(directoryUri).tracks }
        }
    }

    private fun generateStableId(uri: Uri, path: String?, name: String?): Long {
        // Prefer real file path for ID generation to unify MediaStore and SAF tracks
        return (path ?: uri.toString()).hashCode().toLong()
    }

    /**
     * Extremely fast directory scan that only returns names and basic info.
     * Metadata extraction should be handled separately or lazily.
     */
    suspend fun fetchFromDocumentTreeFast(directoryUri: Uri): DirectoryContent = withContext(Dispatchers.IO) {
        val folders = mutableListOf<Folder>()
        val tracks = mutableListOf<AudioTrack>()
        
        // Use DocumentFile to handle URI complexity, but we'll still query manually for speed if possible
        val root = DocumentFile.fromTreeUri(context, directoryUri)
        val childrenUri = if (root != null && root.isDirectory) {
            DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, DocumentsContract.getDocumentId(root.uri))
        } else {
            val docId = if (DocumentsContract.isDocumentUri(context, directoryUri)) {
                DocumentsContract.getDocumentId(directoryUri)
            } else {
                DocumentsContract.getTreeDocumentId(directoryUri)
            }
            DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, docId)
        }
        
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx)
                    val mime = cursor.getString(mimeIdx)
                    
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, childDocId)
                    val dateAdded = if (modIdx != -1) cursor.getLong(modIdx) else 0L

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        folders.add(Folder(name ?: "Unknown Folder", childUri))
                    } else if (isAudioMime(mime) || isAudioExtension(name)) {
                        // Crucial: try to get a path-based ID to match MediaStore
                        val realPath = getRealPathFromSAF(childUri)
                        val id = generateStableId(childUri, realPath, name)

                        tracks.add(AudioTrack(
                            id = id,
                            title = name?.substringBeforeLast('.') ?: "Unknown",
                            artist = "Unknown Artist",
                            duration = 0,
                            contentUri = childUri,
                            fileName = name,
                            dateAdded = dateAdded
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "Fast scan failed, trying DocumentFile fallback: ${e.message}")
        }

        // Fallback if no tracks found (sometimes fast query fails on specific subfolders)
        if (tracks.isEmpty() && folders.isEmpty()) {
            val docFile = if (DocumentsContract.isDocumentUri(context, directoryUri)) {
                DocumentFile.fromSingleUri(context, directoryUri)
            } else {
                DocumentFile.fromTreeUri(context, directoryUri)
            }
            
            docFile?.listFiles()?.forEach { file ->
                val name = file.name
                val mime = file.type
                val uri = file.uri
                val dateAdded = file.lastModified()

                if (file.isDirectory) {
                    folders.add(Folder(name ?: "Unknown Folder", uri))
                } else if (isAudioMime(mime) || isAudioExtension(name)) {
                    val id = uri.toString().hashCode().toLong()
                    tracks.add(AudioTrack(
                        id = id,
                        title = name?.substringBeforeLast('.') ?: "Unknown",
                        artist = "Unknown Artist",
                        duration = 0,
                        contentUri = uri,
                        fileName = name,
                        dateAdded = dateAdded
                    ))
                }
            }
        }
        
        var path = directoryUri.path?.substringAfterLast(':')?.replace("/", " > ") ?: ""
        if (path.isEmpty()) path = "Root"
        
        DirectoryContent(
            folders.sortedBy { it.name.lowercase() },
            tracks.sortedBy { it.title.lowercase() }, 
            0, 
            path,
            totalSubfoldersRecursive = folders.size, // Default to immediate for now, will be enriched
            totalTracksRecursive = tracks.size,
            allTracksRecursive = tracks
        )
    }

    suspend fun fetchAllTracksRecursive(directoryUri: Uri): Pair<Int, List<AudioTrack>> = withContext(Dispatchers.IO) {
        val allTracks = mutableListOf<AudioTrack>()
        val stack = mutableListOf(directoryUri)
        var totalFolders = 0
        
        while (stack.isNotEmpty()) {
            val currentUri = stack.removeAt(stack.size - 1)
            val content = fetchFromDocumentTreeFast(currentUri)
            allTracks.addAll(content.tracks)
            totalFolders += content.folders.size
            stack.addAll(content.folders.map { it.uri })
        }
        Pair(totalFolders, allTracks)
    }

    suspend fun scanDirectoryRecursive(directoryUri: Uri, onProgress: (List<AudioTrack>) -> Unit) = withContext(Dispatchers.IO) {
        val stack = mutableListOf(directoryUri)
        
        while (stack.isNotEmpty()) {
            ensureActive() // Check for cancellation
            val currentUri = stack.removeAt(stack.size - 1)
            val content = fetchFromDocumentTreeFast(currentUri)
            
            if (content.tracks.isNotEmpty()) {
                // Batch save basic info to DB to reduce transaction overhead
                content.tracks.forEach { track ->
                    if (playlistDao.getTrackMetadataSync(track.id) == null) {
                        playlistDao.updateTrackMetadata(TrackMetadata(
                            trackId = track.id,
                            cachedTitle = track.title,
                            cachedArtist = "Loading...",
                            contentUriString = track.contentUri.toString(),
                            fileName = track.fileName,
                            dateAdded = track.dateAdded
                        ))
                    }
                }
                onProgress(content.tracks)
                
                // Crucial: Give up CPU time to prevent system from killing the app
                delay(100) 
            }
            
            stack.addAll(content.folders.map { it.uri })
            yield()
        }
    }

    /**
     * Enriches a single track with metadata (Room or File Scan).
     */
    suspend fun enrichTrack(track: AudioTrack): AudioTrack = withContext(Dispatchers.IO) {
        val uriString = track.contentUri.toString()
        
        // 1. Check Room
        val roomMetadata = playlistDao.getTrackMetadataSync(track.id)
        if (roomMetadata?.cachedTitle != null && roomMetadata.cachedArtist != "Loading...") {
            return@withContext track.copy(
                title = roomMetadata.cachedTitle,
                artist = roomMetadata.cachedArtist ?: "Unknown Artist",
                album = roomMetadata.cachedAlbum ?: "Single",
                duration = roomMetadata.cachedDuration ?: 0L,
                customArtUri = roomMetadata.customArtUri,
                dateAdded = roomMetadata.dateAdded ?: track.dateAdded,
                artVersion = roomMetadata.artVersion
            )
        }

        // 2. Check Memory Cache
        val cached = metadataCache[uriString]
        if (cached != null) {
            return@withContext track.copy(
                title = cached.title,
                artist = cached.artist,
                album = cached.album,
                duration = cached.duration,
                customArtUri = roomMetadata?.customArtUri
            )
        }

        // 3. Physical Scan - Pass the real filename for MediaStore matching
        val extracted = extractMetadata(track.contentUri, track.fileName ?: track.title)
        metadataCache[uriString] = extracted
        
        // 4. Update Room for next time
        playlistDao.updateTrackMetadata(TrackMetadata(
            trackId = track.id,
            customArtUri = roomMetadata?.customArtUri,
            cachedTitle = extracted.title,
            cachedArtist = extracted.artist,
            cachedAlbum = extracted.album,
            cachedDuration = extracted.duration,
            contentUriString = uriString,
            fileName = track.fileName,
            dateAdded = roomMetadata?.dateAdded ?: track.dateAdded
        ))

        track.copy(
            title = extracted.title,
            artist = extracted.artist,
            album = extracted.album,
            duration = extracted.duration,
            customArtUri = roomMetadata?.customArtUri,
            albumArtist = extracted.albumArtist,
            composer = extracted.composer,
            genre = extracted.genre,
            lyricist = extracted.lyricist,
            dateAdded = roomMetadata?.dateAdded ?: track.dateAdded
        )
    }

    /**
     * Reconstructs a track from its ID using cached metadata.
     */
    fun getTrackFromCache(trackId: Long): AudioTrack? {
        val metadata = playlistDao.getTrackMetadataSync(trackId) ?: return null
        val uriString = metadata.contentUriString ?: return null
        return AudioTrack(
            id = trackId,
            title = metadata.cachedTitle ?: "Unknown Track",
            artist = metadata.cachedArtist ?: "Unknown Artist",
            album = metadata.cachedAlbum ?: "Single",
            duration = metadata.cachedDuration ?: 0L,
            contentUri = Uri.parse(uriString),
            customArtUri = metadata.customArtUri,
            albumArtist = metadata.albumArtist,
            composer = metadata.composer,
            genre = metadata.genre,
            lyricist = metadata.lyricist,
            dateAdded = metadata.dateAdded ?: 0L,
            artVersion = metadata.artVersion
        )
    }

    fun clearCacheForTrack(uriString: String) {
        metadataCache.remove(uriString)
    }

    private fun isAudioMime(mime: String?): Boolean {
        if (mime == null) return false
        return mime.startsWith("audio/") || 
               mime == "application/ogg" || 
               mime == "application/x-flac" ||
               mime == "application/octet-stream" // Some files might be generic
    }

    private fun isAudioExtension(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav") || 
               lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".aac") ||
               lower.endsWith(".opus") || lower.endsWith(".m4b") || lower.endsWith(".wma")
    }

    private fun extractMetadata(uri: Uri, fileName: String?): CachedMetadata {
        // 1. Try to find in MediaStore first (it's safe and already sanitized by the system)
        val nameWithoutExt = fileName?.substringBeforeLast('.') ?: "Unknown"
        findInMediaStore(fileName, uri)?.let { return it }

        val retriever = android.media.MediaMetadataRetriever()
        var duration = 0L
        var artist = "Unknown Artist"
        var album = "Single"
        var title = nameWithoutExt
        var albumArtist: String? = null
        var composer: String? = null
        var genre: String? = null
        var lyricist: String? = null

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                retriever.setDataSource(fd.fileDescriptor)
                
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = durationStr?.toLongOrNull() ?: 0L

                // Attempt to get other info, but handle each separately for safety
                title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
                artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: artist
                album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: album
                albumArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                composer = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                genre = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)
                // Lyricist is usually not in standard retriever, but we keep the field
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "Safe extraction failed for $uri", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) { /* ignore */ }
        }
        
        return CachedMetadata(title, artist, album, duration, albumArtist, composer, genre, lyricist)
    }

    private fun findInMediaStore(fileName: String?, uri: Uri): CachedMetadata? {
        if (fileName == null) return null
        
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        
        // Strategy 1: Match by FileName (very fast)
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return parseMetadata(cursor, fileName)
                }
            }
        } catch (e: Exception) { /* ignore and try next */ }

        // Strategy 2: Match by File Path (more robust)
        try {
            val realPath = getRealPathFromSAF(uri)
            if (realPath != null) {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Audio.Media.DATA} = ?",
                    arrayOf(realPath),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return parseMetadata(cursor, fileName)
                    }
                }
            }
        } catch (e: Exception) { /* ignore */ }

        return null
    }

    private fun parseMetadata(cursor: android.database.Cursor, fileName: String): CachedMetadata {
        val title = cursor.getString(0)?.takeIf { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) } 
            ?: fileName.substringBeforeLast('.')
        val artist = cursor.getString(1)?.takeIf { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) } 
            ?: "Unknown Artist"
        val album = cursor.getString(2)?.takeIf { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) } 
            ?: "Single"
        val duration = cursor.getLong(3)
        // MediaStore might not have these directly in the same projection, but we'll try to find more
        return CachedMetadata(title, artist, album, duration)
    }

    fun getRealPathFromSAF(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            if (split.size > 1 && "primary".equals(split[0], ignoreCase = true)) {
                "/storage/emulated/0/" + split[1]
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun fetchFromMediaStore(): List<AudioTrack> {
        val audioList = mutableListOf<AudioTrack>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA
        )
        
        val query = context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )
        
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val mediaStoreId = cursor.getLong(idColumn)
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val id = generateStableId(Uri.EMPTY, path, null)
                
                // Use our stable ID as the key for metadata
                val metadata = playlistDao.getTrackMetadataSync(id)
                
                val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) } 
                    ?: "Unknown Track"
                    
                val artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) } 
                    ?: "Unknown Artist"
                    
                val album = cursor.getString(albumColumn)?.takeIf { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) } 
                    ?: "Single"

                val duration = cursor.getLong(durationColumn)
                val dateAdded = cursor.getLong(dateColumn) * 1000 // Convert to ms
                val displayName = cursor.getString(displayNameColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                
                audioList.add(AudioTrack(
                    id = id, 
                    title = title, 
                    artist = artist, 
                    album = album, 
                    duration = duration, 
                    contentUri = contentUri, 
                    customArtUri = metadata?.customArtUri, 
                    fileName = displayName,
                    dateAdded = dateAdded
                ))
            }
        }
        return audioList
    }

    suspend fun processVocalRemoval(track: AudioTrack, onProgress: (String) -> Unit): Pair<File, File>? = withContext(Dispatchers.IO) {
        return@withContext try {
            onProgress("Подготовка файла...")
            val tempFile = copyUriToTempFile(track.contentUri) ?: return@withContext null

            onProgress("Отправка на сервер (Demucs)...")
            val requestFile = tempFile.asRequestBody("audio/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)

            val response = vocalApi.separateVocals(body)

            if (response.isSuccessful) {
                onProgress("Распаковка результата...")
                val resultFileInstrumental = File(context.cacheDir, "instrumental_${track.id}.mp3")
                val resultFileVocals = File(context.cacheDir, "vocals_${track.id}.mp3")
                
                android.util.Log.d("VocalRemover", "Saving to cache for ID ${track.id}: ${resultFileInstrumental.absolutePath}")

                response.body()?.byteStream()?.use { input ->
                    val zipInputStream = java.util.zip.ZipInputStream(input)
                    var entry = zipInputStream.getNextEntry()
                    while (entry != null) {
                        val outputFile = when {
                            entry.name.contains("no_vocals") -> resultFileInstrumental
                            entry.name.contains("vocals") -> resultFileVocals
                            else -> null
                        }

                        if (outputFile != null) {
                            FileOutputStream(outputFile).use { output ->
                                zipInputStream.copyTo(output)
                            }
                        }
                        zipInputStream.closeEntry()
                        entry = zipInputStream.getNextEntry()
                    }
                }
                
                if (resultFileInstrumental.exists() && resultFileVocals.exists()) {
                    onProgress("Готово!")
                    Pair(resultFileInstrumental, resultFileVocals)
                } else {
                    Log.e("MusicRepository", "Failed to extract both stems from ZIP")
                    null
                }
            } else {
                Log.e("MusicRepository", "Vocal removal failed: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error during vocal removal", e)
            null
        }
    }

    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val tempFile = File(context.cacheDir, "temp_process.mp3")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error copying URI to temp file: ${e.message}")
            null
        }
    }
}
