package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["uriString"], unique = true)]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val formatLabel: String,
    val coverPath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long? = null,
)
