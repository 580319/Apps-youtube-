package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamDao {
    @Query("SELECT * FROM saved_streams ORDER BY lastPlayedTimestamp DESC")
    fun getAllStreams(): Flow<List<StreamEntity>>

    @Query("SELECT * FROM saved_streams WHERE isFavorite = 1 ORDER BY lastPlayedTimestamp DESC")
    fun getFavoriteStreams(): Flow<List<StreamEntity>>

    @Query("SELECT * FROM saved_streams WHERE url = :url LIMIT 1")
    suspend fun getStreamByUrl(url: String): StreamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStream(stream: StreamEntity): Long

    @Update
    suspend fun updateStream(stream: StreamEntity)

    @Delete
    suspend fun deleteStream(stream: StreamEntity)

    @Query("DELETE FROM saved_streams WHERE id = :id")
    suspend fun deleteStreamById(id: Long)

    @Query("UPDATE saved_streams SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)

    @Query("UPDATE saved_streams SET lastPlayedTimestamp = :timestamp WHERE url = :url")
    suspend fun updateLastPlayed(url: String, timestamp: Long)
}
