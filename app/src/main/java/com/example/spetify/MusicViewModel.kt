package com.example.spetify

import android.media.AudioManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(private val context: Context) : ViewModel() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val playlistDao = AppDatabase.getDatabase(context).playlistDao()
    private val repository = MusicRepository(context)
    private val lyricsService = LyricsService()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("spetify_prefs", Context.MODE_PRIVATE)

    val allTrackMetadata = playlistDao.getAllTrackMetadata()
        .map { list -> list.associateBy { it.trackId } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack = combine(_currentTrack, allTrackMetadata) { track, metadataMap ->
        track?.let { t ->
            val customArt = metadataMap[t.id]?.customArtUri
            if (t.customArtUri != customArt) t.copy(customArtUri = customArt) else t
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracks = combine(_tracks, allTrackMetadata) { tracks, metadataMap ->
        tracks.map { track ->
            val meta = metadataMap[track.id]
            if (meta != null) {
                track.copy(
                    title = meta.cachedTitle ?: track.title,
                    artist = meta.cachedArtist ?: track.artist,
                    album = meta.cachedAlbum ?: track.album,
                    duration = meta.cachedDuration ?: track.duration,
                    customArtUri = meta.customArtUri
                )
            } else track
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playlists = playlistDao.getAllPlaylists()
    val savedQueues = playlistDao.getAllSavedQueues()

    enum class SortOrder(val label: String) {
        TITLE("Title (A-Z)"),
        ARTIST("Artist (A-Z)"),
        DURATION("Duration (Longest)"),
        DATE_ADDED("Recently Added"),
        FILENAME("Filename"),
        SHUFFLE("Shuffle All")
    }

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder = _sortOrder.asStateFlow()

    private val _showSortDialog = MutableStateFlow(false)
    val showSortDialog = _showSortDialog.asStateFlow()

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        
        // One-time re-sort of the actual controller queue
        mediaController?.let { controller ->
            val currentId = _currentTrack.value?.id
            val items = mutableListOf<AudioTrack>()
            
            // Get all IDs first to avoid concurrent issues or empty results
            val idsInQueue = mutableListOf<Long>()
            for (i in 0 until controller.mediaItemCount) {
                controller.getMediaItemAt(i).mediaId.toLongOrNull()?.let { idsInQueue.add(it) }
            }

            if (idsInQueue.isEmpty()) return@let

            // Map IDs back to AudioTrack objects using all available sources
            idsInQueue.forEach { id ->
                val track = _tracks.value.find { it.id == id }
                    ?: _currentQueue.value.find { it.id == id }
                    ?: repository.getTrackFromCache(id)
                
                track?.let { items.add(it) }
            }

            if (items.isEmpty()) return@let

            val sorted = when (order) {
                SortOrder.TITLE -> items.sortedBy { it.title.lowercase() }
                SortOrder.ARTIST -> items.sortedBy { it.artist.lowercase() }
                SortOrder.DURATION -> items.sortedByDescending { it.duration }
                SortOrder.DATE_ADDED -> items.sortedByDescending { it.dateAdded }
                SortOrder.FILENAME -> items.sortedBy { it.fileName?.lowercase() ?: it.title.lowercase() }
                SortOrder.SHUFFLE -> items.shuffled()
            }

            // Put current track at top if playing
            val list = if (currentId != null) {
                val reordered = sorted.toMutableList()
                val currentIndex = reordered.indexOfFirst { it.id == currentId }
                if (currentIndex != -1) {
                    val item = reordered.removeAt(currentIndex)
                    reordered.add(0, item)
                }
                reordered
            } else sorted

            // Update controller with the new one-time order
            val mediaItems = list.map {
                MediaItem.Builder()
                    .setMediaId(it.id.toString())
                    .setUri(it.contentUri)
                    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .build())
                    .build()
            }
            
            val currentPosition = controller.currentPosition
            controller.setMediaItems(mediaItems)
            controller.prepare()
            if (currentId != null) {
                controller.seekTo(0, currentPosition)
            }
            controller.play()
        }
    }

    fun toggleSortDialog(show: Boolean) {
        _showSortDialog.value = show
    }

    private val _directoryContent = MutableStateFlow(DirectoryContent(emptyList(), emptyList(), 0, ""))
    val directoryContent = combine(_directoryContent, _sortOrder) { content, order ->
        val sortedTracks = when (order) {
            SortOrder.TITLE -> content.tracks.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> content.tracks.sortedBy { it.artist.lowercase() }
            SortOrder.DURATION -> content.tracks.sortedByDescending { it.duration }
            SortOrder.DATE_ADDED -> content.tracks.sortedByDescending { it.dateAdded }
            SortOrder.FILENAME -> content.tracks.sortedBy { it.fileName?.lowercase() ?: it.title.lowercase() }
            SortOrder.SHUFFLE -> content.tracks.shuffled()
        }
        content.copy(tracks = sortedTracks)
    }.stateIn(viewModelScope, SharingStarted.Lazily, DirectoryContent(emptyList(), emptyList(), 0, ""))

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val searchResults = combine(_searchQuery, tracks, allTrackMetadata) { query, allTracks, metadataMap ->
        if (query.isBlank()) return@combine emptyList<AudioTrack>()
        
        val queryLower = query.lowercase()
        
        val cachedTracks = metadataMap.values.mapNotNull { meta ->
            val uriString = meta.contentUriString ?: return@mapNotNull null
            AudioTrack(
                id = meta.trackId,
                title = meta.cachedTitle ?: "Unknown Track",
                artist = meta.cachedArtist ?: "Unknown Artist",
                album = meta.cachedAlbum ?: "Single",
                duration = meta.cachedDuration ?: 0L,
                contentUri = Uri.parse(uriString),
                customArtUri = meta.customArtUri
            )
        }
        
        (cachedTracks + allTracks)
            .distinctBy { it.id }
            .filter { 
                it.title.lowercase().contains(queryLower) || 
                it.artist.lowercase().contains(queryLower) 
            }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private var enrichmentJob: Job? = null
    private var fullScanJob: Job? = null
    private var lyricsJob: Job? = null

    private val _currentQueue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val currentQueue = combine(_currentQueue, allTrackMetadata) { queue, metadataMap ->
        queue.map { track ->
            val customArt = metadataMap[track.id]?.customArtUri
            if (track.customArtUri != customArt) track.copy(customArtUri = customArt) else track
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val navigationStack = mutableListOf<Uri>()

    companion object {
        private const val KEY_LAST_TRACK_ID = "last_track_id"
        private const val KEY_LAST_POSITION = "last_position"
        private const val KEY_LAST_TITLE = "last_title"
        private const val KEY_LAST_ARTIST = "last_artist"
        private const val KEY_LAST_URI = "last_uri"
        private const val KEY_LAST_ART = "last_art"
    }

    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite = _isFavorite.asStateFlow()

    private val _volume = MutableStateFlow(0f)
    val volume = _volume.asStateFlow()

    private val _showVolumeSlider = MutableStateFlow(false)
    val showVolumeSlider = _showVolumeSlider.asStateFlow()

    private val _syncedLyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    val syncedLyrics = _syncedLyrics.asStateFlow()

    private val _plainLyrics = MutableStateFlow<String?>(null)
    val plainLyrics = _plainLyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading = _isLyricsLoading.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics = _showLyrics.asStateFlow()

    private val _showLyricsEditor = MutableStateFlow(false)
    val showLyricsEditor = _showLyricsEditor.asStateFlow()

    private val _showLyricsFull = MutableStateFlow(false)
    val showLyricsFull = _showLyricsFull.asStateFlow()

    private val _showTagEditor = MutableStateFlow<AudioTrack?>(null)
    val showTagEditor = _showTagEditor.asStateFlow()

    private val _showLyricsSearchDialog = MutableStateFlow(false)
    val showLyricsSearchDialog = _showLyricsSearchDialog.asStateFlow()

    private val _showLyricsSearchMenu = MutableStateFlow<LyricsMenuType?>(null)
    val showLyricsSearchMenu = _showLyricsSearchMenu.asStateFlow()

    private val _showQueueManager = MutableStateFlow(false)
    val showQueueManager = _showQueueManager.asStateFlow()

    private val _showGlobalMoreMenu = MutableStateFlow(false)
    val showGlobalMoreMenu = _showGlobalMoreMenu.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    private val _showHelp = MutableStateFlow(false)
    val showHelp = _showHelp.asStateFlow()

    private val _appLanguage = MutableStateFlow(sharedPrefs.getString("app_language", "en") ?: "en")
    val appLanguage = _appLanguage.asStateFlow()

    private val _appTheme = MutableStateFlow(sharedPrefs.getString("app_theme", "Default") ?: "Default")
    val appTheme = _appTheme.asStateFlow()

    private val _autoScrollQueue = MutableStateFlow(sharedPrefs.getBoolean("auto_scroll_queue", true))
    val autoScrollQueue = _autoScrollQueue.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerSecondsRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerSecondsRemaining = _sleepTimerSecondsRemaining.asStateFlow()

    private val _sleepTimerTotalSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerTotalSeconds = _sleepTimerTotalSeconds.asStateFlow()

    private val _isProcessingVocal = MutableStateFlow(false)
    val isProcessingVocal = _isProcessingVocal.asStateFlow()

    private val _processingStatus = MutableStateFlow("")
    val processingStatus = _processingStatus.asStateFlow()

    private val _instrumentalVolume = MutableStateFlow(1.0f)
    val instrumentalVolume = _instrumentalVolume.asStateFlow()

    private val _vocalsVolume = MutableStateFlow(1.0f)
    val vocalsVolume = _vocalsVolume.asStateFlow()

    private val _showVocalRemoverScreen = MutableStateFlow(false)
    val showVocalRemoverScreen = _showVocalRemoverScreen.asStateFlow()

    private val _isDualPlayback = MutableStateFlow(false)
    val isDualPlayback = _isDualPlayback.asStateFlow()

    private var vocalPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var instrumentalPlayer: androidx.media3.exoplayer.ExoPlayer? = null

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    private val _currentQueueName = MutableStateFlow("Current Queue")
    val currentQueueName = _currentQueueName.asStateFlow()

    fun toggleQueueManager(show: Boolean) {
        _showQueueManager.value = show
    }

    fun toggleGlobalMoreMenu(show: Boolean) {
        _showGlobalMoreMenu.value = show
    }

    fun toggleSettings(show: Boolean) {
        _showSettings.value = show
        if (show) {
            _showGlobalMoreMenu.value = false
            _showHelp.value = false
        }
    }

    fun toggleHelp(show: Boolean) {
        _showHelp.value = show
        if (show) {
            _showGlobalMoreMenu.value = false
            _showSettings.value = false
        }
    }

    fun setLanguage(lang: String) {
        _appLanguage.value = lang
        sharedPrefs.edit().putString("app_language", lang).apply()
    }

    fun setTheme(theme: String) {
        _appTheme.value = theme
        sharedPrefs.edit().putString("app_theme", theme).apply()
    }

    fun setAutoScrollQueue(enabled: Boolean) {
        _autoScrollQueue.value = enabled
        sharedPrefs.edit().putBoolean("auto_scroll_queue", enabled).apply()
    }

    fun removeVocalFromCurrentTrack() {
        val track = currentTrack.value ?: return
        
        // Pause current playback before starting
        mediaController?.pause()
        _isPlaying.value = false
        stopDualPlayback()

        viewModelScope.launch {
            _isProcessingVocal.value = true
            val result = repository.processVocalRemoval(track) { status ->
                _processingStatus.value = status
            }

            if (result != null) {
                android.widget.Toast.makeText(context, "Обработка завершена. Запуск...", android.widget.Toast.LENGTH_SHORT).show()
                setupDualPlayback(track)
            } else {
                android.widget.Toast.makeText(context, "Ошибка при обработке файла", android.widget.Toast.LENGTH_LONG).show()
            }
            _isProcessingVocal.value = false
        }
    }

    fun setInstrumentalVolume(volume: Float) {
        _instrumentalVolume.value = volume
        if (_isDualPlayback.value) {
            instrumentalPlayer?.volume = volume
        } else {
            mediaController?.volume = volume
        }
    }

    fun setVocalsVolume(volume: Float) {
        _vocalsVolume.value = volume
        if (_isDualPlayback.value) {
            vocalPlayer?.volume = volume
        }
    }

    fun toggleVocalRemoverScreen(show: Boolean) {
        _showVocalRemoverScreen.value = show
    }

    fun toggleDualPlayPause() {
        if (!_isDualPlayback.value) {
            togglePlayPause()
            return
        }
        val targetPlaying = !(_isPlaying.value)
        if (targetPlaying) {
            instrumentalPlayer?.play()
            vocalPlayer?.play()
            startProgressPolling()
        } else {
            instrumentalPlayer?.pause()
            vocalPlayer?.pause()
        }
        _isPlaying.value = targetPlaying
    }

    fun seekDualPlayback(position: Long) {
        if (!_isDualPlayback.value) {
            mediaController?.seekTo(position)
            return
        }
        instrumentalPlayer?.seekTo(position)
        vocalPlayer?.seekTo(position)
        _currentPosition.value = position
    }

    private fun setupDualPlayback(track: AudioTrack) {
        stopDualPlayback()
        mediaController?.pause()
        _isDualPlayback.value = true
        _currentTrack.value = track
        
        val instFile = java.io.File(context.cacheDir, "instrumental_${track.id}.mp3")
        val vocFile = java.io.File(context.cacheDir, "vocals_${track.id}.mp3")

        instrumentalPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(instFile)))
            volume = _instrumentalVolume.value
            prepare()
            play()
        }

        vocalPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(vocFile)))
            volume = _vocalsVolume.value
            prepare()
            play()
        }
        
        _isPlaying.value = true
        startProgressPolling()
    }

    private fun stopDualPlayback() {
        if (!_isDualPlayback.value) return
        _isDualPlayback.value = false
        instrumentalPlayer?.release()
        vocalPlayer?.release()
        instrumentalPlayer = null
        vocalPlayer = null
        _isPlaying.value = false
    }

    fun saveProcessedFiles() {
        val track = currentTrack.value ?: return
        val trackId = if (track.title.contains("[AI]")) track.id - 777777 else track.id
        
        val instFile = java.io.File(context.cacheDir, "instrumental_$trackId.mp3")
        val vocFile = java.io.File(context.cacheDir, "vocals_$trackId.mp3")
        
        if (!instFile.exists() || !vocFile.exists()) {
            android.widget.Toast.makeText(context, "Файлы не найдены", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                val spetifyDir = java.io.File(musicDir, "SPETify")
                val instDir = java.io.File(spetifyDir, "instrumentals")
                val vocDir = java.io.File(spetifyDir, "vocals")
                
                instDir.mkdirs()
                vocDir.mkdirs()
                
                val safeTitle = track.title.replace("[AI] ", "").replace(Regex("[\\\\/:*?\"<>|]"), "_")
                
                val targetInst = java.io.File(instDir, "$safeTitle [Instrumental].mp3")
                val targetVoc = java.io.File(vocDir, "$safeTitle [Vocals].mp3")
                
                instFile.copyTo(targetInst, overwrite = true)
                vocFile.copyTo(targetVoc, overwrite = true)
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Сохранено в Music/SPETify", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Ошибка сохранения: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun setSleepTimer(totalSeconds: Long?) {
        sleepTimerJob?.cancel()
        _sleepTimerTotalSeconds.value = totalSeconds
        _sleepTimerSecondsRemaining.value = totalSeconds
        _sleepTimerMinutes.value = totalSeconds?.let { (it / 60).toInt() }

        if (totalSeconds != null && totalSeconds > 0) {
            sleepTimerJob = viewModelScope.launch {
                var remaining = totalSeconds
                while (remaining > 0) {
                    kotlinx.coroutines.delay(1000L) // 1 second
                    remaining--
                    _sleepTimerSecondsRemaining.value = remaining
                    _sleepTimerMinutes.value = (remaining / 60).toInt()
                }
                mediaController?.pause()
                _isPlaying.value = false
                _sleepTimerSecondsRemaining.value = null
                _sleepTimerTotalSeconds.value = null
                _sleepTimerMinutes.value = null
            }
        }
    }


    fun moveSavedQueue(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val queues = savedQueues.first().toMutableList()
            if (fromIndex in queues.indices && toIndex in queues.indices && fromIndex != toIndex) {
                val moved = queues.removeAt(fromIndex)
                queues.add(toIndex, moved)
                playlistDao.reorderSavedQueues(queues.map { it.id })
            }
        }
    }

    fun deleteSavedQueue(queue: SavedQueue) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.deleteSavedQueue(queue)
        }
    }

    fun renameSavedQueue(queue: SavedQueue, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.updateSavedQueue(queue.copy(name = newName))
        }
    }

    fun loadSavedQueue(queue: SavedQueue) {
        _currentQueueName.value = queue.name
        viewModelScope.launch(Dispatchers.IO) {
            val trackIds = playlistDao.getQueueTrackIds(queue.id)
            val restoredTracks = trackIds.mapNotNull { id ->
                _tracks.value.find { it.id == id } ?: repository.getTrackFromCache(id)
            }
            withContext(Dispatchers.Main) {
                if (restoredTracks.isNotEmpty()) {
                    playAll(restoredTracks)
                }
            }
        }
    }

    fun shareQueue(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        
        val uris = ArrayList<Uri>()
        tracks.forEach { uris.add(it.contentUri) }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "Share Queue Songs")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun closeApp() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    fun startVocalRemover() {
        // Placeholder
    }

    fun downloadTrackArt(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = TagWriter(context).extractAndSaveArtwork(track.contentUri, track.title)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(context, "Artwork saved to Pictures/SPETify", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "No embedded artwork found in this file", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshTrackAfterTagUpdate(trackId: Long, audioUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Clear repository cache
            repository.clearCacheForTrack(audioUri.toString())

            // 2. Update version and metadata in Room
            val meta = playlistDao.getTrackMetadataSync(trackId)
            val artVer = System.currentTimeMillis()
            if (meta != null) {
                // Keep all tags, only bump version
                playlistDao.updateTrackMetadata(meta.copy(artVersion = artVer))
            }

            // 3. Notify system to re-scan file
            try {
                android.media.MediaScannerConnection.scanFile(context, arrayOf(audioUri.path), null) { _, _ -> }
                val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                scanIntent.data = audioUri
                context.sendBroadcast(scanIntent)
            } catch (e: Exception) {}

            // 4. Force re-enrichment
            val updated = repository.getTrackFromCache(trackId)
            
            withContext(Dispatchers.Main) {
                if (_currentTrack.value?.id == trackId) {
                    _currentTrack.value = updated ?: _currentTrack.value
                }
                
                // 5. Update MediaController to refresh notification
                mediaController?.let { controller ->
                    for (i in 0 until controller.mediaItemCount) {
                        val item = controller.getMediaItemAt(i)
                        if (item.mediaId == trackId.toString()) {
                            val refreshedArtUri = updated?.customArtUri?.let { Uri.parse(it) } ?: audioUri
                            val bustCacheUri = refreshedArtUri.buildUpon()
                                .appendQueryParameter("art_v", artVer.toString())
                                .build()

                            val newMeta = item.mediaMetadata.buildUpon()
                                .setArtworkUri(bustCacheUri)
                                .build()
                            
                            controller.replaceMediaItem(i, item.buildUpon().setMediaMetadata(newMeta).build())
                            break
                        }
                    }
                }
                Toast.makeText(context, "Artwork updated and saved to file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    enum class LyricsMenuType { KARAOKE, PLAIN }

    private val _showTrackInfo = MutableStateFlow<AudioTrack?>(null)
    val showTrackInfo = _showTrackInfo.asStateFlow()

    init {
        // Initialize volume from system
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        _volume.value = currentVol.toFloat() / maxVol.toFloat()

        // Step 0: Immediate UI restoration from Prefs (Sync)
        restoreSessionMetadata()

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            mediaController = controller
            
            // Sync initial state immediately
            if (controller != null) {
                _isPlaying.value = controller.isPlaying
                _currentPosition.value = controller.currentPosition
                startProgressPolling()
                
                // NEW: Restore last session after controller is ready
                restorePlaybackState(controller)
            }

            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startProgressPolling()
                    } else {
                        // Save position on pause for better accuracy
                        mediaController?.let { 
                            sharedPrefs.edit().putLong(KEY_LAST_POSITION, it.currentPosition).apply()
                        }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Update isPlaying state on any state change to be sure
                    mediaController?.let { 
                        _isPlaying.value = it.isPlaying 
                        if (it.isPlaying) startProgressPolling()
                        
                        // FIX: Remove redundant seek from here. 
                        // Seeking should only happen once during restore or manual user action.
                    }
                    if (playbackState == Player.STATE_READY) {
                        updateDurationIfMissing()
                    }
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleMode.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.let { item ->
                        val trackId = item.mediaId.toLongOrNull() ?: 0L
                        
                        // Save last played track ID
                        sharedPrefs.edit().putLong(KEY_LAST_TRACK_ID, trackId).apply()
                        
                        // Clear current lyrics immediately to avoid showing wrong text
                        _syncedLyrics.value = emptyList()
                        _plainLyrics.value = null
                        
                        viewModelScope.launch(Dispatchers.IO) {
                            val track = _tracks.value.find { it.id == trackId }
                                ?: _currentQueue.value.find { it.id == trackId }
                                ?: repository.getTrackFromCache(trackId)
                            
                            withContext(Dispatchers.Main) {
                                _currentTrack.value = track
                                track?.let { 
                                    updateFavoriteState(it.id)
                                    // RE-ENABLED: Auto fetch lyrics for new track to ensure it's ready for both views
                                    fetchLyrics(it)
                                }
                            }
                        }
                    }
                }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    updateQueueFromController()
                    
                    // Save queue to database for persistence
                    mediaController?.let { controller ->
                        val ids = mutableListOf<Long>()
                        for (i in 0 until controller.mediaItemCount) {
                            controller.getMediaItemAt(i).mediaId.toLongOrNull()?.let { ids.add(it) }
                        }
                        viewModelScope.launch(Dispatchers.IO) {
                            playlistDao.updateActiveQueue(ids)
                        }
                    }
                }
            })
        }, MoreExecutors.directExecutor())
        
        loadTracks()
    }

    private fun restoreSessionMetadata() {
        val lastTrackId = sharedPrefs.getLong(KEY_LAST_TRACK_ID, -1L)
        if (lastTrackId != -1L) {
            val title = sharedPrefs.getString(KEY_LAST_TITLE, "Unknown Track") ?: "Unknown Track"
            val artist = sharedPrefs.getString(KEY_LAST_ARTIST, "Unknown Artist") ?: "Unknown Artist"
            val uriStr = sharedPrefs.getString(KEY_LAST_URI, null)
            val artStr = sharedPrefs.getString(KEY_LAST_ART, null)
            val pos = sharedPrefs.getLong(KEY_LAST_POSITION, 0L)

            if (uriStr != null) {
                _currentTrack.value = AudioTrack(
                    id = lastTrackId,
                    title = title,
                    artist = artist,
                    duration = 0, // Will be updated by controller
                    contentUri = Uri.parse(uriStr),
                    customArtUri = artStr,
                    dateAdded = pos // Using pos as dummy date if needed, or just 0
                )
                _currentPosition.value = pos
            }
        }
    }

    private fun updateDurationIfMissing() {
        val controller = mediaController ?: return
        if (_currentTrack.value?.duration == 0L && controller.duration > 0) {
            _currentTrack.value = _currentTrack.value?.copy(duration = controller.duration)
        }
    }

    fun moveTrackInQueue(fromIndex: Int, toIndex: Int) {
        mediaController?.let { controller ->
            val itemCount = controller.mediaItemCount
            if (fromIndex in 0 until itemCount && toIndex in 0 until itemCount && fromIndex != toIndex) {
                try {
                    controller.moveMediaItem(fromIndex, toIndex)
                } catch (e: Exception) {
                    android.util.Log.e("MusicViewModel", "Failed to move track in queue", e)
                }
            }
        }
    }

    fun moveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            try {
                val playlistWithTracks = playlistDao.getPlaylistWithTracksSorted(playlistId).first()
                val sortedTracks = playlistWithTracks.getSortedTracks()
                val trackIds = sortedTracks.map { it.trackId }.toMutableList()
                
                if (fromIndex in trackIds.indices && toIndex in trackIds.indices && fromIndex != toIndex) {
                    val movedId = trackIds.removeAt(fromIndex)
                    trackIds.add(toIndex, movedId)
                    playlistDao.reorderPlaylist(playlistId, trackIds)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to move track in playlist", e)
            }
        }
    }

    private fun updateQueueFromController() {
        val controller = mediaController ?: return
        
        // 1. Capture ALL necessary info from controller on the MAIN thread
        val mediaIds = mutableListOf<String>()
        val metadataList = mutableListOf<androidx.media3.common.MediaMetadata>()
        val uriList = mutableListOf<Uri?>()
        
        for (i in 0 until controller.mediaItemCount) {
            val item = controller.getMediaItemAt(i)
            mediaIds.add(item.mediaId)
            metadataList.add(item.mediaMetadata)
            uriList.add(item.localConfiguration?.uri)
        }

        // 2. Process database lookups in the BACKGROUND
        viewModelScope.launch(Dispatchers.IO) {
            val queue = mediaIds.mapIndexed { i, mediaId ->
                val trackId = mediaId.toLongOrNull() ?: 0L
                val meta = metadataList[i]
                
                // Try memory, then DB, then fallback
                _tracks.value.find { it.id == trackId }
                    ?: repository.getTrackFromCache(trackId)
                    ?: AudioTrack(
                        trackId,
                        meta.title?.toString()?.takeIf { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) } ?: "Unknown Track",
                        meta.artist?.toString()?.takeIf { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) } ?: "Unknown Artist",
                        meta.albumTitle?.toString()?.takeIf { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) } ?: "Single",
                        0L,
                        uriList[i] ?: Uri.EMPTY,
                        dateAdded = 0L
                    )
            }
            
            // 3. Return to MAIN thread to update the UI state
            withContext(Dispatchers.Main) {
                _currentQueue.value = queue
            }
        }
    }

    private var progressJob: Job? = null

    private fun startProgressPolling() {
        if (progressJob?.isActive == true) return // Don't start another job if one is running

        progressJob = viewModelScope.launch {
            while (isActive) {
                if (_isDualPlayback.value) {
                    instrumentalPlayer?.let {
                        if (it.isPlaying) {
                            _currentPosition.value = it.currentPosition
                        }
                    }
                } else {
                    val controller = mediaController
                    if (controller != null && controller.isPlaying) {
                        _currentPosition.value = controller.currentPosition
                        
                        // Save current position periodically
                        sharedPrefs.edit().putLong(KEY_LAST_POSITION, controller.currentPosition).apply()

                        // Save metadata periodically to ensure "Instant UI" on next boot is accurate
                        _currentTrack.value?.let { track ->
                            sharedPrefs.edit().apply {
                                putString(KEY_LAST_TITLE, track.title)
                                putString(KEY_LAST_ARTIST, track.artist)
                                putString(KEY_LAST_URI, track.contentUri.toString())
                                putString(KEY_LAST_ART, track.customArtUri)
                            }.apply()
                        }

                        // Also update duration if it's currently 0
                        if (_currentTrack.value?.duration == 0L && controller.duration > 0) {
                            _currentTrack.value = _currentTrack.value?.copy(duration = controller.duration)
                        }
                    }
                }
                delay(500) // Poll more frequently for smoother UI (500ms)
            }
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        _currentPosition.value = position
    }

    private fun loadTracks() {
        viewModelScope.launch {
            var savedUriString = sharedPrefs.getString("directory_uri", null)

            // Try to default to Music folder if nothing saved
            if (savedUriString == null) {
                try {
                    val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                    if (musicDir.exists()) {
                        // This is a file path, we need a SAF URI or just use it as a hint.
                        // For now, we'll keep it null and rely on MediaStore unless we can resolve a URI.
                    }
                } catch (e: Exception) {}
            }

            if (savedUriString != null) {
                val uri = Uri.parse(savedUriString)
                
                val hasPermission = context.contentResolver.persistedUriPermissions.any { 
                    it.uri == uri && it.isReadPermission 
                }
                
                if (hasPermission) {
                    navigationStack.clear()
                    navigationStack.add(uri)
                    loadDirectory(uri)
                    startFullScan(uri)
                } else {
                    // Fallback to MediaStore if permission is lost
                    val mediaStoreTracks = withContext(Dispatchers.IO) {
                        repository.fetchAudioFiles(null)
                    }
                    _directoryContent.value = DirectoryContent(emptyList(), mediaStoreTracks, 0, "Root")
                    _tracks.value = mediaStoreTracks
                }
            } else {
                val mediaStoreTracks = withContext(Dispatchers.IO) {
                    repository.fetchAudioFiles(null)
                }
                _directoryContent.value = DirectoryContent(emptyList(), mediaStoreTracks, 0, "Root")
                _tracks.value = mediaStoreTracks
            }
        }
    }

    private fun restorePlaybackState(controller: MediaController) {
        val lastTrackId = sharedPrefs.getLong(KEY_LAST_TRACK_ID, -1L)
        val lastPosition = sharedPrefs.getLong(KEY_LAST_POSITION, 0L)

        viewModelScope.launch {
            // If player already has items (e.g. session was active), don't override
            if (controller.mediaItemCount > 0) return@launch

            val trackIds = withContext(Dispatchers.IO) { playlistDao.getActiveQueueTrackIds() }
            
            if (trackIds.isNotEmpty()) {
                val restoredTracks = withContext(Dispatchers.IO) {
                    trackIds.mapNotNull { id ->
                        _tracks.value.find { it.id == id } ?: repository.getTrackFromCache(id)
                    }
                }

                if (restoredTracks.isNotEmpty()) {
                    val mediaItems = restoredTracks.map { track ->
                        MediaItem.Builder()
                            .setMediaId(track.id.toString())
                            .setUri(track.contentUri)
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .build())
                            .build()
                    }
                    
                    controller.setMediaItems(mediaItems)
                    
                    // Find index of the last track to restore current track and position
                    val index = trackIds.indexOf(lastTrackId).takeIf { it != -1 } ?: 0
                    controller.seekTo(index, lastPosition)
                    controller.prepare()
                    
                    _currentTrack.value = restoredTracks.getOrNull(index)
                    _currentPosition.value = lastPosition
                }
            } else if (lastTrackId != -1L) {
                // Fallback for single track if queue is empty
                val trackToRestore = withContext(Dispatchers.IO) {
                    _tracks.value.find { it.id == lastTrackId }
                        ?: repository.getTrackFromCache(lastTrackId)
                }
                
                if (trackToRestore != null) {
                    _currentTrack.value = trackToRestore
                    _currentPosition.value = lastPosition
                    
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(trackToRestore.id.toString())
                        .setUri(trackToRestore.contentUri)
                        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(trackToRestore.title)
                            .setArtist(trackToRestore.artist)
                            .build())
                        .build()
                    
                    controller.setMediaItem(mediaItem)
                    controller.seekTo(lastPosition)
                    controller.prepare()
                }
            }
        }
    }

    fun onDirectorySelected(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Log or handle error
        }
        sharedPrefs.edit().putString("directory_uri", uri.toString()).apply()
        navigationStack.clear()
        navigationStack.add(uri)
        loadDirectory(uri)
        startFullScan(uri)
    }

    private fun startFullScan(uri: Uri) {
        fullScanJob?.cancel()
        fullScanJob = viewModelScope.launch {
            repository.scanDirectoryRecursive(uri) { /* Progress can be handled here if UI needs it */ }
        }
    }

    fun navigateInto(uri: Uri) {
        if (navigationStack.isEmpty() || navigationStack.last() != uri) {
            navigationStack.add(uri)
            loadDirectory(uri)
        }
    }

    fun navigateBack(): Boolean {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.size - 1)
            val previousUri = navigationStack.last()
            loadDirectory(previousUri)
            return true
        }
        return false
    }

    private fun loadDirectory(uri: Uri) {
        enrichmentJob?.cancel()
        enrichmentJob = viewModelScope.launch {
            try {
                // 1. Immediate fast load
                val fastContent = repository.fetchFromDocumentTreeFast(uri)
                _directoryContent.value = fastContent
                
                // 2. Background enrichment
                val enrichedTracks = coroutineScope {
                    fastContent.tracks.map { track ->
                        async { repository.enrichTrack(track) }
                    }.awaitAll()
                }
                
                val finalContent = fastContent.copy(
                    tracks = enrichedTracks,
                    totalDuration = enrichedTracks.sumOf { it.duration }
                )
                _directoryContent.value = finalContent
                
                // Update main tracks list if this was the root directory
                if (navigationStack.isNotEmpty() && navigationStack.first() == uri) {
                    _tracks.value = enrichedTracks
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playTrackFromQueue(index: Int) {
        mediaController?.let { controller ->
            if (index in 0 until controller.mediaItemCount) {
                controller.seekTo(index, 0)
                controller.play()
            }
        }
    }

    fun playTrack(track: AudioTrack, playlist: List<AudioTrack>? = null) {
        // If it's an AI track, stop dual playback if active
        stopDualPlayback()

        // If clicking the track that is already loaded, just resume it
        if (_currentTrack.value?.id == track.id) {
            mediaController?.let { 
                if (!it.isPlaying) it.play()
                return 
            }
        }

        val playListToUse = playlist ?: listOf(track)
        
        // Ensure the selected track is at the top
        val reorderedList = playListToUse.toMutableList()
        val trackIndex = reorderedList.indexOfFirst { it.id == track.id }
        if (trackIndex != -1) {
            val item = reorderedList.removeAt(trackIndex)
            reorderedList.add(0, item)
        }

        mediaController?.let { controller ->
            try {
                val mediaItems = reorderedList.map {
                    MediaItem.Builder()
                        .setMediaId(it.id.toString())
                        .setUri(it.contentUri)
                        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(it.title)
                            .setArtist(it.artist)
                            .build())
                        .build()
                }
                controller.setMediaItems(mediaItems)
                controller.seekTo(0, 0)
                controller.prepare()
                controller.play()
                _currentTrack.value = track
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to play track", e)
            }
        }
    }

    fun addToQueue(track: AudioTrack) {
        viewModelScope.launch {
            mediaController?.let { controller ->
                try {
                    // Check if track is already in queue
                    var isDuplicate = false
                    for (i in 0 until controller.mediaItemCount) {
                        if (controller.getMediaItemAt(i).mediaId == track.id.toString()) {
                            isDuplicate = true
                            break
                        }
                    }

                    if (isDuplicate) {
                        android.widget.Toast.makeText(context, "'${track.title}' is already in queue", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val mediaItem = MediaItem.Builder()
                        .setMediaId(track.id.toString())
                        .setUri(track.contentUri)
                        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .build())
                        .build()
                    
                    if (controller.mediaItemCount == 0) {
                        controller.setMediaItem(mediaItem)
                    } else {
                        controller.addMediaItem(mediaItem)
                    }
                    
                    if (!controller.playWhenReady) {
                        controller.prepare()
                    }
                    
                    android.widget.Toast.makeText(context, "Added to queue: ${track.title}", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("MusicViewModel", "Failed to add to queue", e)
                }
            }
        }
    }

    fun removeFromQueue(index: Int) {
        mediaController?.removeMediaItem(index)
    }

    fun clearQueue() {
        mediaController?.clearMediaItems()
    }

    fun playAll(tracks: List<AudioTrack>, shuffle: Boolean = false, sourceName: String? = null) {
        if (tracks.isEmpty()) return
        
        val listToPlay = if (shuffle) tracks.shuffled() else tracks
        
        // Auto-save to Saved Queues if a source name is provided
        sourceName?.let { name ->
            _currentQueueName.value = name
            viewModelScope.launch(Dispatchers.IO) {
                val ids = listToPlay.map { it.id }
                playlistDao.saveCurrentQueueAs(name, ids)
            }
        }

        mediaController?.let { controller ->
            try {
                val mediaItems = listToPlay.map {
                    MediaItem.Builder()
                        .setMediaId(it.id.toString())
                        .setUri(it.contentUri)
                        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(it.title)
                            .setArtist(it.artist)
                            .build())
                        .build()
                }
                controller.setMediaItems(mediaItems)
                controller.prepare()
                controller.play()
                _currentTrack.value = listToPlay.first()
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to play all", e)
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            } else {
                it.play()
                _isPlaying.value = true
                startProgressPolling()
            }
        }
    }
    
    fun skipNext() {
        mediaController?.seekToNext()
    }
    
    fun skipPrevious() {
        mediaController?.seekToPrevious()
    }

    fun setVolume(value: Float) {
        _volume.value = value
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVol = (value * maxVol).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
    }

    fun toggleVolumeSlider() {
        _showVolumeSlider.value = !_showVolumeSlider.value
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistDao.insertPlaylist(Playlist(name = name))
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(playlist)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: AudioTrack) {
        viewModelScope.launch {
            repository.enrichTrack(track) 
            playlistDao.addTrackToPlaylistWithPosition(playlistId, track.id)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Added to playlist", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Removed from playlist", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playNext(track: AudioTrack) {
        mediaController?.let { controller ->
            val currentIndex = controller.currentMediaItemIndex
            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.contentUri)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build())
                .build()
            
            if (controller.mediaItemCount == 0) {
                controller.setMediaItem(mediaItem)
            } else {
                controller.addMediaItem(currentIndex + 1, mediaItem)
            }
            android.widget.Toast.makeText(context, "Will play next: ${track.title}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteTrackPermanently(track: AudioTrack) {
        viewModelScope.launch {
            try {
                // 1. Remove from all playlists and metadata
                // Note: We need a transaction or separate calls
                
                // 2. Remove from current player
                mediaController?.let { controller ->
                    for (i in 0 until controller.mediaItemCount) {
                        if (controller.getMediaItemAt(i).mediaId == track.id.toString()) {
                            controller.removeMediaItem(i)
                            break
                        }
                    }
                }

                // 3. Delete physical file
                val deleted = withContext(Dispatchers.IO) {
                    try {
                        androidx.documentfile.provider.DocumentFile.fromSingleUri(context, track.contentUri)?.delete() == true
                    } catch (e: Exception) { false }
                }

                if (deleted) {
                    android.widget.Toast.makeText(context, "Deleted: ${track.title}", android.widget.Toast.LENGTH_SHORT).show()
                    loadTracks() // Refresh lists
                } else {
                    android.widget.Toast.makeText(context, "Failed to delete file", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Delete failed", e)
            }
        }
    }

    fun toggleShuffleMode() {
        mediaController?.let { controller ->
            val queue = _currentQueue.value
            if (queue.size > 1) {
                val currentId = _currentTrack.value?.id
                // Filter out the currently playing track to ensure we pick a new one
                val otherTracks = queue.filter { it.id != currentId }
                
                if (otherTracks.isNotEmpty()) {
                    val trackToPlay = otherTracks.random()
                    playTrack(trackToPlay, queue)
                    android.widget.Toast.makeText(context, "Shuffled to: ${trackToPlay.title}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else if (queue.size == 1) {
                android.widget.Toast.makeText(context, "Only one song in queue", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleRepeatMode() {
        mediaController?.let {
            val newMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = newMode
            _repeatMode.value = newMode
            
            val modeText = when (newMode) {
                Player.REPEAT_MODE_ALL -> "Repeat All"
                Player.REPEAT_MODE_ONE -> "Repeat One"
                else -> "Repeat Off"
            }
            android.widget.Toast.makeText(context, modeText, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFavoriteState(trackId: Long) {
        viewModelScope.launch {
            _isFavorite.value = allTrackMetadata.value[trackId]?.isFavorite ?: false
        }
    }

    fun toggleFavorite(track: AudioTrack) {
        viewModelScope.launch {
            val currentStatus = _isFavorite.value
            val newStatus = !currentStatus
            
            // Ensure metadata exists
            if (allTrackMetadata.value[track.id] == null) {
                repository.enrichTrack(track)
            }
            
            playlistDao.updateFavoriteStatus(track.id, newStatus)
            _isFavorite.value = newStatus
            
            val message = if (newStatus) "Added to Favorites" else "Removed from Favorites"
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun showInfo(track: AudioTrack?) {
        _showTrackInfo.value = track
    }

    fun dismissInfo() {
        _showTrackInfo.value = null
    }

    fun toggleLyrics() {
        if (_syncedLyrics.value.isEmpty() && _plainLyrics.value == null) {
            _showLyricsSearchMenu.value = LyricsMenuType.KARAOKE
        } else {
            _showLyrics.value = !_showLyrics.value
        }
    }

    fun openLyricsEditor() {
        _showLyricsEditor.value = true
    }

    fun closeLyricsEditor() {
        _showLyricsEditor.value = false
    }

    fun openLyricsFull() {
        if (_plainLyrics.value.isNullOrBlank() && _syncedLyrics.value.isEmpty()) {
            _showLyricsSearchMenu.value = LyricsMenuType.PLAIN
        } else {
            _showLyricsFull.value = true
        }
    }

    fun closeLyricsMenu() {
        _showLyricsSearchMenu.value = null
    }

    fun startAutoSearchFromMenu(type: LyricsMenuType) {
        _showLyricsSearchMenu.value = null
        _currentTrack.value?.let { 
            fetchLyrics(it, forceRefresh = true)
            if (type == LyricsMenuType.KARAOKE) _showLyrics.value = true
            else _showLyricsFull.value = true
        }
    }

    fun startManualSearchFromMenu(type: LyricsMenuType) {
        _showLyricsSearchMenu.value = null
        if (type == LyricsMenuType.KARAOKE) _showLyrics.value = true
        else _showLyricsFull.value = true
        openLyricsSearch()
    }

    fun closeLyricsFull() {
        _showLyricsFull.value = false
    }

    fun openTagEditor(track: AudioTrack) {
        _showTagEditor.value = track
    }

    fun closeTagEditor() {
        _showTagEditor.value = null
    }

    fun saveTrackTags(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        composer: String,
        genre: String,
        lyricist: String
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 1. Update Database
                playlistDao.updateFullMetadata(
                    trackId, title, artist, album, albumArtist, composer, genre, lyricist
                )
            }

            // 2. Refresh UI for current track if needed
            if (_currentTrack.value?.id == trackId) {
                val current = _currentTrack.value!!
                _currentTrack.value = current.copy(
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    composer = composer,
                    genre = genre,
                    lyricist = lyricist
                )
            }
            _showTagEditor.value = null
            android.widget.Toast.makeText(context, "Tags saved", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun saveManualLyrics(trackId: Long, text: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val meta = playlistDao.getTrackMetadataSync(trackId)
                if (meta != null) {
                    playlistDao.updateTrackMetadata(meta.copy(cachedLyrics = text))
                }
            }
            // Refresh current state
            if (_currentTrack.value?.id == trackId) {
                _plainLyrics.value = text
            }
            _showLyricsEditor.value = false
        }
    }

    fun openLyricsSearch() {
        _showLyricsSearchDialog.value = true
    }

    fun closeLyricsSearch() {
        _showLyricsSearchDialog.value = false
    }

    fun searchLyricsManual(title: String, artist: String) {
        viewModelScope.launch {
            _isLyricsLoading.value = true
            _showLyricsSearchDialog.value = false
            try {
                val response = lyricsService.searchLyricsManual(title, artist)
                val track = _currentTrack.value
                if (response != null && track != null) {
                    // CRITICAL FIX: Save the manually found lyrics to the database immediately
                    withContext(Dispatchers.IO) {
                        playlistDao.updateCachedLyrics(track.id, response.plainLyrics, response.syncedLyrics)
                    }

                    if (!response.syncedLyrics.isNullOrBlank()) {
                        _syncedLyrics.value = lyricsService.parseSyncedLyrics(response.syncedLyrics)
                        _plainLyrics.value = response.plainLyrics ?: _syncedLyrics.value.joinToString("\n") { it.content }
                    } else {
                        _plainLyrics.value = response.plainLyrics
                        _syncedLyrics.value = emptyList()
                    }
                } else {
                    _plainLyrics.value = "No lyrics found for \"$title\""
                    _syncedLyrics.value = emptyList()
                }
            } catch (e: Exception) {
                _plainLyrics.value = "Error: ${e.message}"
            }
            _isLyricsLoading.value = false
        }
    }

    fun retryFetchLyrics(track: AudioTrack) {
        fetchLyrics(track, forceRefresh = true)
    }

    private fun fetchLyrics(track: AudioTrack, forceRefresh: Boolean = false) {
        lyricsJob?.cancel() // Cancel previous request immediately
        
        lyricsJob = viewModelScope.launch {
            // 1. Check Cache (Fast!) - Skip if we already have lyrics and NOT forcing refresh
            if (!forceRefresh) {
                val cachedMeta = withContext(Dispatchers.IO) { playlistDao.getTrackMetadataSync(track.id) }
                if (cachedMeta != null && (!cachedMeta.cachedLyrics.isNullOrBlank() || !cachedMeta.cachedSyncedLyrics.isNullOrBlank())) {
                    if (!cachedMeta.cachedSyncedLyrics.isNullOrBlank()) {
                        val parsed = lyricsService.parseSyncedLyrics(cachedMeta.cachedSyncedLyrics)
                        _syncedLyrics.value = parsed
                        _plainLyrics.value = cachedMeta.cachedLyrics ?: parsed.joinToString("\n") { it.content }
                    } else {
                        _plainLyrics.value = cachedMeta.cachedLyrics
                        _syncedLyrics.value = emptyList()
                    }
                    _isLyricsLoading.value = false // CRITICAL: Stop loading when cache hit
                    return@launch
                }
            }
            
            // 2. Network Fetch (Only if not found in DB or forceRefresh is true)
            _isLyricsLoading.value = true
            try {
                val response = lyricsService.fetchLyrics(track)
                
                if (_currentTrack.value?.id != track.id) return@launch

                if (response != null) {
                    // Save to Room for future use
                    withContext(Dispatchers.IO) {
                        playlistDao.updateCachedLyrics(track.id, response.plainLyrics, response.syncedLyrics)
                    }
                    
                    if (!response.syncedLyrics.isNullOrBlank()) {
                        val parsed = lyricsService.parseSyncedLyrics(response.syncedLyrics)
                        _syncedLyrics.value = parsed
                        _plainLyrics.value = response.plainLyrics ?: parsed.joinToString("\n") { it.content }
                    } else if (!response.plainLyrics.isNullOrBlank()) {
                        _plainLyrics.value = response.plainLyrics
                        _syncedLyrics.value = emptyList()
                    } else {
                        _plainLyrics.value = "Lyrics not found in database"
                        _syncedLyrics.value = emptyList()
                    }
                } else {
                    _plainLyrics.value = "No lyrics found for this track"
                    _syncedLyrics.value = emptyList()
                }
            } catch (e: Exception) {
                if (_currentTrack.value?.id == track.id) {
                    _plainLyrics.value = "Error loading lyrics: ${e.message}"
                }
            } finally {
                if (_currentTrack.value?.id == track.id) {
                    _isLyricsLoading.value = false
                }
            }
        }
    }

    fun getPlaylistTracks(playlistId: Long): Flow<List<AudioTrack>> {
        return playlistDao.getPlaylistWithTracksSorted(playlistId).map { playlistWithTracks ->
            android.util.Log.d("MusicViewModel", "Playlist ${playlistId} has ${playlistWithTracks.trackRefs.size} track refs")
            
            // Perform track lookups on IO dispatcher to avoid main thread database access
            withContext(Dispatchers.IO) {
                playlistWithTracks.getSortedTracks().mapNotNull { ref ->
                    // 1. Try to find in currently loaded tracks
                    val found = _tracks.value.find { it.id == ref.trackId }
                    // 2. Try to find in the queue
                        ?: _currentQueue.value.find { it.id == ref.trackId }
                    // 3. Fallback to repository cache (database access happens here)
                        ?: repository.getTrackFromCache(ref.trackId)
                    
                    if (found == null) {
                        android.util.Log.w("MusicViewModel", "Track ${ref.trackId} not found in any source")
                    }
                    found
                }
            }
        }.distinctUntilChanged()
    }

    fun getPlaylistsContainingTrack(trackId: Long): Flow<List<Long>> {
        return playlistDao.getPlaylistsContainingTrack(trackId).map { list ->
            list.map { it.playlistId }
        }
    }

    fun setCustomArt(trackId: Long, uri: Uri) {
        viewModelScope.launch {
            playlistDao.updateTrackMetadata(TrackMetadata(trackId, uri.toString()))
            
            // Sync current track UI
            if (_currentTrack.value?.id == trackId) {
                _currentTrack.value = _currentTrack.value?.copy(customArtUri = uri.toString())
            }

            // Write to physical file
            withContext(Dispatchers.IO) {
                val track = _tracks.value.find { it.id == trackId }
                    ?: _currentQueue.value.find { it.id == trackId }
                    ?: repository.getTrackFromCache(trackId)
                
                val success = track?.let { TagWriter(context).writeAlbumArtToFile(it.contentUri, uri.toString()) } ?: false
                if (success && track != null) {
                    refreshTrackAfterTagUpdate(trackId, track.contentUri)
                }
            }
        }
    }

    fun autoSearchArt(track: AudioTrack) {
        viewModelScope.launch {
            val client = okhttp3.OkHttpClient()
            val searchTerm = if (track.artist == "<unknown>") track.title else "${track.artist} ${track.title}"
            val encodedTerm = withContext(Dispatchers.IO) { java.net.URLEncoder.encode(searchTerm, "UTF-8") }
            val url = "https://itunes.apple.com/search?term=$encodedTerm&entity=song&limit=1"

            val request = okhttp3.Request.Builder().url(url).build()

            val resultUrl = withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null) {
                                val json = org.json.JSONObject(body)
                                val results = json.getJSONArray("results")
                                if (results.length() > 0) {
                                    val artworkUrl = results.getJSONObject(0).getString("artworkUrl100")
                                    artworkUrl.replace("100x100bb.jpg", "600x600bb.jpg")
                                } else null
                            } else null
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (resultUrl != null) {
                withContext(Dispatchers.IO) {
                    val meta = playlistDao.getTrackMetadataSync(track.id) ?: TrackMetadata(
                        trackId = track.id,
                        cachedTitle = track.title,
                        cachedArtist = track.artist,
                        cachedAlbum = track.album,
                        cachedDuration = track.duration,
                        contentUriString = track.contentUri.toString()
                    )
                    playlistDao.updateTrackMetadata(meta.copy(customArtUri = resultUrl))
                    
                    // Write to physical file
                    val success = TagWriter(context).writeAlbumArtToFile(track.contentUri, resultUrl)
                    if (success) {
                        refreshTrackAfterTagUpdate(track.id, track.contentUri)
                    }
                }
                
                if (_currentTrack.value?.id == track.id) {
                    _currentTrack.value = _currentTrack.value?.copy(customArtUri = resultUrl)
                }
            }
        }
    }

    fun searchYouTubeArt(track: AudioTrack) {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("MusicViewModel", "YouTube art search failed", throwable)
        }) {
            withContext(Dispatchers.IO) {
                try {
                    val cleanTitle = track.title.substringBefore(" (").substringBefore(" [")
                    val query = "${track.artist} $cleanTitle".replace(" ", "+")
                    val searchUrl = "https://www.youtube.com/results?search_query=$query"
                    
                    val doc = org.jsoup.Jsoup.connect(searchUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .timeout(10000)
                        .get()
                    
                    val html = doc.html()
                    val regex = "\"videoId\":\"([\\w-]{11})\"".toRegex()
                    val videoId = regex.find(html)?.groupValues?.get(1)
                    
                    if (videoId != null) {
                        val artUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
                        
                        // 1. Update Database Metadata
                        val meta = playlistDao.getTrackMetadataSync(track.id) ?: TrackMetadata(
                            trackId = track.id,
                            contentUriString = track.contentUri.toString(),
                            cachedTitle = track.title,
                            cachedArtist = track.artist,
                            cachedAlbum = track.album,
                            cachedDuration = track.duration
                        )
                        playlistDao.updateTrackMetadata(meta.copy(customArtUri = artUrl))
                        
                        // 2. Physical Embedding into the file
                        val success = TagWriter(context).writeAlbumArtToFile(track.contentUri, artUrl)
                        
                        // 3. Refresh UI and Notifications
                        if (success) {
                            refreshTrackAfterTagUpdate(track.id, track.contentUri)
                        } else {
                            withContext(Dispatchers.Main) {
                                _currentTrack.value = _currentTrack.value?.copy(customArtUri = artUrl)
                                Toast.makeText(context, "Art updated in app, but failed to embed in file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun removeCustomArt(trackId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val meta = playlistDao.getTrackMetadataSync(trackId) ?: TrackMetadata(trackId)
                playlistDao.updateTrackMetadata(meta.copy(customArtUri = null))
            }
            
            if (_currentTrack.value?.id == trackId) {
                _currentTrack.value = _currentTrack.value?.copy(customArtUri = null)
            }
        }
    }

    fun getCustomArt(trackId: Long): String? {
        return allTrackMetadata.value[trackId]?.customArtUri
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
