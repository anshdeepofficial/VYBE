package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.Flow
import com.theveloper.pixelplay.data.model.Song

@Entity(tableName = "audius_favorites")
data class AudiusFavoriteEntity(
    @PrimaryKey val id: String, // e.g. audius_1234
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val duration: Long,
    val streamUrl: String
)

@Dao
interface AudiusFavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: AudiusFavoriteEntity)

    @Query("DELETE FROM audius_favorites WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM audius_favorites")
    fun getAllFavorites(): Flow<List<AudiusFavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM audius_favorites WHERE id = :id)")
    fun isFavorite(id: String): Flow<Boolean>
}

fun AudiusFavoriteEntity.toSong(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        artistId = 0L,
        album = "Audius",
        albumId = 0L,
        path = streamUrl,
        contentUriString = streamUrl,
        albumArtUriString = albumArtUrl,
        duration = duration,
        mimeType = "audio/mpeg",
        bitrate = 320,
        sampleRate = 44100
    )
}

fun Song.toAudiusFavoriteEntity(): AudiusFavoriteEntity {
    return AudiusFavoriteEntity(
        id = id,
        title = title,
        artist = artist,
        albumArtUrl = albumArtUriString,
        duration = duration,
        streamUrl = path
    )
}
