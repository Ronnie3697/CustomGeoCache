package com.customgeocache.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "caches")
data class CacheEntity(
    @PrimaryKey val gccode: String,
    val name: String,
    val type: String,           // Traditional, Mystery, Multi, Letterbox, Earth, Wherigo, ...
    val lat: Double,
    val lon: Double,
    val difficulty: Float,      // 1.0 .. 5.0
    val terrain: Float,         // 1.0 .. 5.0
    val size: String,           // Micro, Small, Regular, Large, Other, Virtual
    val owner: String?,
    val isFound: Boolean = false,
    val isFavorite: Boolean = false,
    val description: String? = null,
    val hint: String? = null,
    val attributes: String? = null,  // CSV názvů attributů
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)
