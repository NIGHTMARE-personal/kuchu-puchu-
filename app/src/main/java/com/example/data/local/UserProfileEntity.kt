package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String = "user_default",
    val displayName: String = "Alex Rivera",
    val avatarUrl: String? = null,
    val email: String = "alex.rivera@example.com",
    val bio: String = "AI Assistant Power User",
    val localCachePath: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
