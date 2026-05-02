package com.customgeocache.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "logs",
    indices = [Index("gccode")],
    foreignKeys = [
        ForeignKey(
            entity = CacheEntity::class,
            parentColumns = ["gccode"],
            childColumns = ["gccode"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gccode: String,
    val type: String,           // Found it, DNF, Note, Write note, ...
    val author: String,
    val dateMillis: Long,
    val text: String
)
