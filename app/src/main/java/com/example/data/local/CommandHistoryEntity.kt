package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "command_history",
    indices = [Index(value = ["timestamp"])]
)
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val transcript: String,
    val action: String,
    val target: String,
    val datetime: String? = null,
    val source: String,
    val outcome: String, // "SUCCESS", "CONFIRMATION_PENDING", "CANCELLED", "ERROR"
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null,
    val attachmentBadge: String? = null,
    val engineName: String? = null,
    val routeType: String? = null,
    val latencyMs: Long? = null,
    val confidence: Float? = null,
    val rawIntentJson: String? = null,
    val attachmentUri: String? = null
)
