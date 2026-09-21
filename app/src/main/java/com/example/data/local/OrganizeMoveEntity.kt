package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Write-ahead log and manifest entry for file moves made by Downloads Organizer.
 * Every move is persisted to Room BEFORE performing the file operation.
 * Enables batch recovery and full undo operations surviving process death.
 */
@Entity(tableName = "organize_moves")
data class OrganizeMoveEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val planId: String,
    val fromPath: String,
    val toPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "PENDING", "COMPLETED", "FAILED", "UNDONE"
    val errorMessage: String? = null
)
