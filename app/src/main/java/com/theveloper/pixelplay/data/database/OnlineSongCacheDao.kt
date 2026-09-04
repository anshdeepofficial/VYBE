package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.Flow

/** Metadata for playable online tracks referenced by persisted local playlists. */
@Entity(tableName = "online_song_cache")
data class OnlineSongCacheEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String?,
    val duration: Long,
    val path: String,
    val contentUri: String,
    val mimeType: String?,
)

@Dao
interface OnlineSongCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<OnlineSongCacheEntity>)

    @Query("SELECT * FROM online_song_cache")
    fun observeAll(): Flow<List<OnlineSongCacheEntity>>

    @Query("SELECT COUNT(*) FROM online_song_cache")
    suspend fun getCount(): Int
}

fun OnlineSongCacheEntity.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    artistId = 0L,
    album = album,
    albumId = 0L,
    path = path,
    contentUriString = contentUri,
    albumArtUriString = albumArtUrl,
    duration = duration,
    mimeType = mimeType,
    bitrate = null,
    sampleRate = null,
)

fun Song.toOnlineSongCacheEntity(): OnlineSongCacheEntity = OnlineSongCacheEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtUrl = albumArtUriString,
    duration = duration,
    path = path,
    contentUri = contentUriString,
    mimeType = mimeType,
)
