package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloaded_songs")
data class DownloadedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String = "Downloads",
    val albumArtUrl: String? = null,
    val duration: Long = 0L,
    val localFilePath: String,
    val mimeType: String = "audio/mp4",
    val bitrate: Int = 256,
    val downloadedAt: Long = System.currentTimeMillis()
)

fun DownloadedSongEntity.toSong(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        artistId = 0L,
        album = album,
        albumId = 0L,
        path = localFilePath,
        contentUriString = localFilePath,
        albumArtUriString = albumArtUrl,
        duration = duration,
        mimeType = mimeType,
        bitrate = bitrate,
        sampleRate = 44100
    )
}

@Dao
interface DownloadedSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: DownloadedSongEntity)

    @Query("DELETE FROM downloaded_songs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    fun getAllDownloadedSongs(): Flow<List<DownloadedSongEntity>>

    @Query("SELECT * FROM downloaded_songs WHERE id = :id LIMIT 1")
    suspend fun getDownloadedSongById(id: String): DownloadedSongEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE id = :id)")
    fun isSongDownloaded(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE id = :id)")
    suspend fun isSongDownloadedDirect(id: String): Boolean
}
