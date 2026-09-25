package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_streams")
data class StreamEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val subtitle: String = "Direct Stream",
    val isFavorite: Boolean = false,
    val lastPlayedTimestamp: Long = System.currentTimeMillis()
)
