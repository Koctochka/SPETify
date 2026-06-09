package com.example.spetify

import androidx.room.*

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId", "position"]
)
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val position: Int = 0
)

data class PlaylistWithTracks(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val trackRefs: List<PlaylistTrack>
)

@Entity(tableName = "saved_queues")
data class SavedQueue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0
)

@Entity(
    tableName = "saved_queue_tracks",
    primaryKeys = ["queueId", "trackId", "position"]
)
data class SavedQueueTrack(
    val queueId: Long,
    val trackId: Long,
    val position: Int
)

data class QueueWithTracks(
    @Embedded val queue: SavedQueue,
    @Relation(
        parentColumn = "id",
        entityColumn = "queueId"
    )
    val trackRefs: List<SavedQueueTrack>
)

@Entity(tableName = "track_metadata")
data class TrackMetadata(
    @PrimaryKey val trackId: Long,
    val customArtUri: String? = null,
    val cachedTitle: String? = null,
    val cachedArtist: String? = null,
    val cachedAlbum: String? = null,
    val cachedDuration: Long? = null,
    val contentUriString: String? = null,
    val isFavorite: Boolean = false,
    val cachedLyrics: String? = null,
    val cachedSyncedLyrics: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val genre: String? = null,
    val lyricist: String? = null,
    val dateAdded: Long? = null,
    val artVersion: Long = 0
)
