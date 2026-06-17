package com.example.spetify

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(playlistTrack: PlaylistTrack)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCountInPlaylist(playlistId: Long): Int

    @Transaction
    suspend fun addTrackToPlaylistWithPosition(playlistId: Long, trackId: Long) {
        val count = getTrackCountInPlaylist(playlistId)
        addTrackToPlaylist(PlaylistTrack(playlistId, trackId, count))
    }

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks>

    data class PlaylistWithTracksSorted(
        @Embedded val playlist: Playlist,
        @Relation(
            parentColumn = "id",
            entityColumn = "playlistId"
        )
        val trackRefs: List<PlaylistTrack>
    ) {
        fun getSortedTracks() = trackRefs.sortedBy { it.position }
    }

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithTracksSorted(playlistId: Long): Flow<PlaylistWithTracksSorted>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(tracks: List<PlaylistTrack>)

    @Transaction
    suspend fun reorderPlaylist(playlistId: Long, tracks: List<Long>) {
        clearPlaylistTracks(playlistId)
        val newTracks = tracks.mapIndexed { index, trackId ->
            PlaylistTrack(playlistId, trackId, index)
        }
        insertPlaylistTracks(newTracks)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateTrackMetadata(metadata: TrackMetadata)

    @Query("SELECT * FROM track_metadata WHERE trackId = :trackId")
    fun getTrackMetadata(trackId: Long): Flow<TrackMetadata?>

    @Query("SELECT * FROM track_metadata WHERE trackId = :trackId")
    fun getTrackMetadataSync(trackId: Long): TrackMetadata?

    @Query("SELECT * FROM track_metadata")
    fun getAllTrackMetadata(): Flow<List<TrackMetadata>>

    @Query("SELECT * FROM track_metadata")
    suspend fun getAllTrackMetadataSync(): List<TrackMetadata>

    @Query("UPDATE track_metadata SET cachedLyrics = :lyrics, cachedSyncedLyrics = :syncedLyrics WHERE trackId = :trackId")
    suspend fun updateCachedLyrics(trackId: Long, lyrics: String?, syncedLyrics: String?)

    @Query("UPDATE track_metadata SET isFavorite = :isFavorite WHERE trackId = :trackId")
    suspend fun updateFavoriteStatus(trackId: Long, isFavorite: Boolean)

    @Query("SELECT * FROM saved_queues WHERE id != 0 ORDER BY position ASC")
    fun getAllSavedQueues(): Flow<List<SavedQueue>>

    @Query("SELECT * FROM saved_queues WHERE id != 0 ORDER BY position ASC")
    suspend fun getAllSavedQueuesSync(): List<SavedQueue>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedQueue(queue: SavedQueue): Long

    @Update
    suspend fun updateSavedQueue(queue: SavedQueue)

    @Update
    suspend fun updateSavedQueues(queues: List<SavedQueue>)

    @Transaction
    suspend fun reorderSavedQueues(queueIds: List<Long>) {
        val updatedQueues = queueIds.mapIndexed { index, id ->
            val queue = getSavedQueueById(id)
            queue?.copy(position = index)
        }.filterNotNull()
        updateSavedQueues(updatedQueues)
    }

    @Query("SELECT * FROM saved_queues WHERE id = :id")
    suspend fun getSavedQueueById(id: Long): SavedQueue?

    @Query("SELECT * FROM saved_queues WHERE name = :name LIMIT 1")
    suspend fun getSavedQueueByName(name: String): SavedQueue?

    @Delete
    suspend fun deleteSavedQueue(queue: SavedQueue)

    @Query("DELETE FROM saved_queue_tracks WHERE queueId = :queueId")
    suspend fun clearQueueTracks(queueId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueTracks(tracks: List<SavedQueueTrack>)

    @Transaction
    suspend fun saveCurrentQueueAs(name: String, trackIds: List<Long>): Long {
        val existing = getSavedQueueByName(name)
        val queueId = if (existing != null) {
            clearQueueTracks(existing.id)
            existing.id
        } else {
            insertSavedQueue(SavedQueue(name = name))
        }

        val tracks = trackIds.mapIndexed { index, trackId ->
            SavedQueueTrack(queueId, trackId, index)
        }
        insertQueueTracks(tracks)
        return queueId
    }

    @Query("UPDATE saved_queues SET lastTrackId = :trackId, lastPosition = :position WHERE id = :queueId")
    suspend fun updateQueueLastState(queueId: Long, trackId: Long?, position: Long)

    @Query("SELECT trackId FROM saved_queue_tracks WHERE queueId = :queueId ORDER BY position ASC")
    suspend fun getQueueTrackIds(queueId: Long): List<Long>

    @Query("DELETE FROM saved_queue_tracks WHERE queueId = 0")
    suspend fun clearActiveQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActiveQueue(tracks: List<SavedQueueTrack>)

    @Transaction
    suspend fun updateActiveQueue(trackIds: List<Long>) {
        clearActiveQueue()
        val tracks = trackIds.mapIndexed { index, trackId ->
            SavedQueueTrack(0, trackId, index) // queueId 0 for active session
        }
        insertActiveQueue(tracks)
    }

    @Query("SELECT trackId FROM saved_queue_tracks WHERE queueId = 0 ORDER BY position ASC")
    suspend fun getActiveQueueTrackIds(): List<Long>

    @Query("SELECT * FROM playlist_tracks WHERE trackId = :trackId")
    fun getPlaylistsContainingTrack(trackId: Long): Flow<List<PlaylistTrack>>

    @Query("UPDATE track_metadata SET cachedTitle = :title, cachedArtist = :artist, cachedAlbum = :album, albumArtist = :albumArtist, composer = :composer, genre = :genre, lyricist = :lyricist WHERE trackId = :trackId")
    suspend fun updateFullMetadata(trackId: Long, title: String?, artist: String?, album: String?, albumArtist: String?, composer: String?, genre: String?, lyricist: String?)
}
